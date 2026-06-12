package com.quanxiaoha.weblog.admin.service.impl;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminGrayPublishService;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.PublishBatchStatusEnum;
import com.quanxiaoha.weblog.common.enums.PublishBatchTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 灰度发布管理服务实现
 */
@Service
@Slf4j
public class AdminGrayPublishServiceImpl implements AdminGrayPublishService {

    @Autowired
    private AdminArticleVersionDao articleVersionDao;
    @Autowired
    private AdminGrayRuleDao grayRuleDao;
    @Autowired
    private AdminPreviewTokenDao previewTokenDao;
    @Autowired
    private AdminPublishBatchDao publishBatchDao;
    @Autowired
    private AdminVersionExposureLogDao exposureLogDao;
    @Autowired
    private AdminRollbackAuditDao rollbackAuditDao;
    @Autowired
    private AdminArticleServiceImpl articleService;

    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AdminGrayPublishServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Response grayPublish(GrayPublishReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (version.getStatus() != ArticleVersionStatusEnum.DRAFT.getCode()) {
            return Response.fail("只能对草稿版本进行灰度发布");
        }

        Long articleId = version.getArticleId();

        // 冲突检查: 存在待发布的定时版本
        if (articleId != null && articleId != 0L && articleVersionDao.hasPendingPublishForArticle(articleId)) {
            return Response.fail("存在待发布的定时版本，请先取消或等待完成");
        }

        // 冲突检查: 已有活跃灰度版本
        if (articleId != null && articleId != 0L && articleVersionDao.hasActiveGrayForArticle(articleId)) {
            return Response.fail("该文章已有活跃的灰度版本");
        }

        return transactionTemplate.execute(status -> {
            // 更新版本状态为 GRAY
            version.setStatus(ArticleVersionStatusEnum.GRAY.getCode());
            articleVersionDao.updateById(version);

            // 插入灰度规则
            if (!CollectionUtils.isEmpty(req.getRules())) {
                for (GrayRuleItemVO ruleItem : req.getRules()) {
                    GrayRuleDO rule = GrayRuleDO.builder()
                            .versionId(version.getId())
                            .ruleType(ruleItem.getRuleType())
                            .ruleValue(ruleItem.getRuleValue())
                            .createTime(new Date())
                            .build();
                    grayRuleDao.insert(rule);
                }
            }

            // 创建灰度批次
            PublishBatchDO batch = PublishBatchDO.builder()
                    .versionId(version.getId())
                    .batchNum(1)
                    .batchType(PublishBatchTypeEnum.GRAY.getCode())
                    .status(PublishBatchStatusEnum.ACTIVE.getCode())
                    .createdBy(getCurrentUsername())
                    .createTime(new Date())
                    .build();
            publishBatchDao.insert(batch);

            log.info("文章版本 {} 灰度发布成功，批次 {}", version.getId(), batch.getId());
            return Response.success();
        });
    }

    @Override
    public Response updateGrayRules(GrayRuleUpdateReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (version.getStatus() != ArticleVersionStatusEnum.GRAY.getCode()) {
            return Response.fail("只能更新灰度状态版本的规则");
        }

        return transactionTemplate.execute(status -> {
            // 删除旧规则
            grayRuleDao.deleteByVersionId(req.getVersionId());

            // 插入新规则
            if (!CollectionUtils.isEmpty(req.getRules())) {
                for (GrayRuleItemVO ruleItem : req.getRules()) {
                    GrayRuleDO rule = GrayRuleDO.builder()
                            .versionId(req.getVersionId())
                            .ruleType(ruleItem.getRuleType())
                            .ruleValue(ruleItem.getRuleValue())
                            .createTime(new Date())
                            .build();
                    grayRuleDao.insert(rule);
                }
            }

            return Response.success();
        });
    }

    @Override
    public Response generatePreviewToken(GeneratePreviewTokenReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (version.getStatus() != ArticleVersionStatusEnum.GRAY.getCode()) {
            return Response.fail("只能为灰度状态的版本生成预览令牌");
        }

        int expireHours = (req.getExpireHours() != null && req.getExpireHours() > 0) ? req.getExpireHours() : 24;
        String token = UUID.randomUUID().toString().replace("-", "");
        Date expireAt = new Date(System.currentTimeMillis() + (long) expireHours * 3600_000);

        PreviewTokenDO tokenDO = PreviewTokenDO.builder()
                .versionId(req.getVersionId())
                .token(token)
                .expireAt(expireAt)
                .createTime(new Date())
                .isUsed(false)
                .build();
        previewTokenDao.insert(tokenDO);

        String previewUrl = "/article/detail?articleId=" + version.getArticleId() + "&previewToken=" + token;

        GeneratePreviewTokenRspVO rsp = GeneratePreviewTokenRspVO.builder()
                .token(token)
                .previewUrl(previewUrl)
                .expireAt(expireAt)
                .build();

        return Response.success(rsp);
    }

    @Override
    public Response promoteGrayToFull(GrayPromoteReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }

        // 幂等: 已发布则直接返回成功
        if (version.getStatus() == ArticleVersionStatusEnum.PUBLISHED.getCode()) {
            return Response.success();
        }

        if (version.getStatus() != ArticleVersionStatusEnum.GRAY.getCode()) {
            return Response.fail("只能将灰度版本全量发布");
        }

        try {
            Boolean success = transactionTemplate.execute(status -> {
                Long articleId;
                if (version.getArticleId() == null || version.getArticleId() == 0L) {
                    // 新文章首次发布
                    articleId = articleService.materializeVersion(version);
                    articleVersionDao.updateArticleId(version.getId(), articleId);
                } else {
                    articleId = version.getArticleId();
                    articleService.materializeUpdateToLiveTables(version, articleId);
                }

                // 更新版本状态
                version.setStatus(ArticleVersionStatusEnum.PUBLISHED.getCode());
                version.setPublishedAt(new Date());
                articleVersionDao.updateById(version);

                // 完成灰度批次
                PublishBatchDO activeBatch = publishBatchDao.selectActiveByVersionId(version.getId());
                if (activeBatch != null) {
                    publishBatchDao.updateStatusById(activeBatch.getId(),
                            PublishBatchStatusEnum.COMPLETED.getCode(), new Date());
                }

                // 创建全量发布批次
                int batchNum = activeBatch != null ? activeBatch.getBatchNum() + 1 : 1;
                PublishBatchDO fullBatch = PublishBatchDO.builder()
                        .versionId(version.getId())
                        .batchNum(batchNum)
                        .batchType(PublishBatchTypeEnum.FULL.getCode())
                        .status(PublishBatchStatusEnum.COMPLETED.getCode())
                        .createdBy(getCurrentUsername())
                        .createTime(new Date())
                        .completedAt(new Date())
                        .build();
                publishBatchDao.insert(fullBatch);

                // 撤销预览令牌
                previewTokenDao.revokeByVersionId(version.getId());

                // 删除灰度规则
                grayRuleDao.deleteByVersionId(version.getId());

                log.info("文章版本 {} 灰度全量发布成功", version.getId());
                return true;
            });
            return Boolean.TRUE.equals(success) ? Response.success() : Response.fail("全量发布失败");
        } catch (Exception e) {
            log.error("灰度全量发布版本 {} 失败", req.getVersionId(), e);
            return Response.fail("全量发布失败: " + e.getMessage());
        }
    }

    @Override
    public Response rollbackGray(GrayRollbackReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (version.getStatus() != ArticleVersionStatusEnum.GRAY.getCode()) {
            return Response.fail("只能回滚灰度状态的版本");
        }

        return transactionTemplate.execute(status -> {
            // GRAY -> DRAFT
            version.setStatus(ArticleVersionStatusEnum.DRAFT.getCode());
            articleVersionDao.updateById(version);

            // 更新批次状态
            PublishBatchDO activeBatch = publishBatchDao.selectActiveByVersionId(version.getId());
            if (activeBatch != null) {
                publishBatchDao.updateStatusById(activeBatch.getId(),
                        PublishBatchStatusEnum.ROLLED_BACK.getCode(), new Date());
            }

            // 删除灰度规则
            grayRuleDao.deleteByVersionId(version.getId());

            // 撤销预览令牌
            previewTokenDao.revokeByVersionId(version.getId());

            // 找到当前线上的稳定版本（最新已发布版本）
            Long articleId = version.getArticleId();
            Long toVersionId = 0L;
            if (articleId != null && articleId != 0L) {
                ArticleVersionDO stableVersion = articleVersionDao.selectLatestPublishedByArticleId(articleId);
                if (stableVersion != null) {
                    toVersionId = stableVersion.getId();
                }
            }

            // 创建回滚审计记录
            RollbackAuditDO audit = RollbackAuditDO.builder()
                    .articleId(articleId != null ? articleId : 0L)
                    .fromVersionId(version.getId())
                    .toVersionId(toVersionId)
                    .batchId(activeBatch != null ? activeBatch.getId() : null)
                    .operator(getCurrentUsername())
                    .reason(req.getReason())
                    .rollbackTime(new Date())
                    .build();
            rollbackAuditDao.insert(audit);

            log.info("文章版本 {} 灰度回滚成功", version.getId());
            return Response.success();
        });
    }

    @Override
    public Response queryGrayStats(Long versionId) {
        List<VersionExposureLogDO> logs = exposureLogDao.selectByVersionId(versionId);

        long totalExposures = logs.size();
        long uniqueUsers = logs.stream()
                .map(VersionExposureLogDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Map<String, Long> ruleBreakdown = logs.stream()
                .filter(l -> l.getMatchedRule() != null)
                .collect(Collectors.groupingBy(VersionExposureLogDO::getMatchedRule, Collectors.counting()));

        PublishBatchDO activeBatch = publishBatchDao.selectActiveByVersionId(versionId);

        GrayStatsRspVO rsp = GrayStatsRspVO.builder()
                .versionId(versionId)
                .totalExposures(totalExposures)
                .uniqueUsers(uniqueUsers)
                .ruleBreakdown(ruleBreakdown)
                .batchId(activeBatch != null ? activeBatch.getId() : null)
                .build();

        return Response.success(rsp);
    }

    @Override
    public Response queryPublishBatchList(QueryPublishBatchListReqVO req) {
        List<PublishBatchDO> batches = publishBatchDao.selectByVersionId(req.getVersionId());

        List<QueryPublishBatchListRspVO> list = batches.stream()
                .map(b -> QueryPublishBatchListRspVO.builder()
                        .batchId(b.getId())
                        .batchNum(b.getBatchNum())
                        .batchType(b.getBatchType())
                        .status(b.getStatus())
                        .createdBy(b.getCreatedBy())
                        .createTime(b.getCreateTime())
                        .completedAt(b.getCompletedAt())
                        .build())
                .collect(Collectors.toList());

        return Response.success(list);
    }

    private String getCurrentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
