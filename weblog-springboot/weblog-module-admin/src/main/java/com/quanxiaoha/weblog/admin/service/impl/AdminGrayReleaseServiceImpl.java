package com.quanxiaoha.weblog.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.admin.dao.AdminGrayReleaseDao;
import com.quanxiaoha.weblog.admin.dao.AdminUserTagDao;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminGrayReleaseService;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.GrayAccessTypeEnum;
import com.quanxiaoha.weblog.common.enums.GrayFeedbackTypeEnum;
import com.quanxiaoha.weblog.common.enums.GraySegmentRuleTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 灰度发布管理服务实现
 */
@Service
@Slf4j
public class AdminGrayReleaseServiceImpl implements AdminGrayReleaseService {

    @Autowired
    private AdminArticleVersionDao articleVersionDao;

    @Autowired
    private AdminGrayReleaseDao adminGrayReleaseDao;

    @Autowired
    private AdminUserTagDao adminUserTagDao;

    @Autowired
    private AdminArticleServiceImpl adminArticleService;

    private final TransactionTemplate transactionTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AdminGrayReleaseServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Response publishGrayVersion(PublishGrayVersionReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (version.getStatus() != ArticleVersionStatusEnum.DRAFT.getCode()) {
            return Response.fail("只能将草稿版本发布为灰度");
        }

        Long articleId = version.getArticleId();

        // 检查是否已有灰度版本
        if (articleId != null && articleId > 0) {
            ArticleVersionDO existingGray = adminGrayReleaseDao.selectActiveGrayByArticleId(articleId);
            if (existingGray != null) {
                return Response.fail("该文章已存在活跃的灰度版本，请先完成全量发布或回滚");
            }
        }

        // 检查是否有待发布的定时版本
        if (articleId != null && articleId > 0) {
            List<ArticleVersionDO> allVersions = articleVersionDao.selectAllByArticleId(articleId);
            boolean hasPending = allVersions.stream()
                    .anyMatch(v -> v.getStatus() == ArticleVersionStatusEnum.PENDING_PUBLISH.getCode());
            if (hasPending) {
                return Response.fail("该文章存在待发布的定时版本，请先取消定时发布后再创建灰度");
            }
        }

        Boolean success = transactionTemplate.execute(status -> {
            // 更新版本状态为灰度
            version.setStatus(ArticleVersionStatusEnum.GRAY.getCode());
            articleVersionDao.updateById(version);

            // 插入分群规则
            List<String> previewTokens = new ArrayList<>();
            for (GrayRuleConfigVO ruleConfig : req.getRules()) {
                String configJson = buildRuleConfigJson(ruleConfig);

                GraySegmentRuleDO rule = GraySegmentRuleDO.builder()
                        .versionId(version.getId())
                        .articleId(articleId)
                        .ruleType(ruleConfig.getRuleType())
                        .ruleConfig(configJson)
                        .isActive(true)
                        .build();
                adminGrayReleaseDao.insertSegmentRule(rule);

                // 预览链接规则：生成令牌
                if (ruleConfig.getRuleType() == GraySegmentRuleTypeEnum.PREVIEW_LINK.getCode()) {
                    int tokenCount = ruleConfig.getPreviewTokenCount() != null ? ruleConfig.getPreviewTokenCount() : 1;
                    Date expireAt = ruleConfig.getPreviewExpireAt();
                    if (expireAt == null) {
                        // 默认7天过期
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_MONTH, 7);
                        expireAt = cal.getTime();
                    }

                    List<GrayPreviewTokenDO> tokens = new ArrayList<>();
                    for (int i = 0; i < tokenCount; i++) {
                        String token = UUID.randomUUID().toString().replace("-", "");
                        previewTokens.add(token);
                        tokens.add(GrayPreviewTokenDO.builder()
                                .versionId(version.getId())
                                .articleId(articleId)
                                .token(token)
                                .expireAt(expireAt)
                                .createdBy("admin")
                                .build());
                    }
                    if (!tokens.isEmpty()) {
                        adminGrayReleaseDao.insertPreviewTokenBatch(tokens);
                    }
                }
            }
            return true;
        });

        if (Boolean.TRUE.equals(success)) {
            Map<String, Object> data = new HashMap<>();
            data.put("versionId", version.getId());
            return Response.success(data);
        }
        return Response.fail("灰度发布失败");
    }

    @Override
    public Response fullPublishGray(FullPublishGrayReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getGrayVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }

        // 幂等检查
        if (version.getStatus() == ArticleVersionStatusEnum.PUBLISHED.getCode()) {
            return Response.success();
        }

        if (version.getStatus() != ArticleVersionStatusEnum.GRAY.getCode()) {
            return Response.fail("只能全量发布灰度状态的版本");
        }

        try {
            Boolean success = transactionTemplate.execute(status -> {
                Long articleId;
                if (version.getArticleId() == null || version.getArticleId() == 0L) {
                    // 新文章首次物化
                    articleId = adminArticleService.materializeVersionForGray(version);
                    articleVersionDao.updateArticleId(version.getId(), articleId);
                } else {
                    articleId = version.getArticleId();
                    adminArticleService.materializeUpdateForGray(version, articleId);
                }

                // 更新版本状态为已发布
                version.setStatus(ArticleVersionStatusEnum.PUBLISHED.getCode());
                version.setPublishedAt(new Date());
                articleVersionDao.updateById(version);

                // 停用所有分群规则
                adminGrayReleaseDao.deactivateRulesByVersionId(version.getId());

                // 软删除预览令牌
                adminGrayReleaseDao.softDeleteTokensByVersionId(version.getId());

                return true;
            });

            return Boolean.TRUE.equals(success) ? Response.success() : Response.fail("全量发布失败");
        } catch (Exception e) {
            log.error("灰度版本 {} 全量发布失败", version.getId(), e);
            adminArticleService.handlePublishFailure(version);
            return Response.fail("全量发布失败: " + e.getMessage());
        }
    }

    @Override
    public Response rollbackGray(RollbackGrayReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getGrayVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (!Objects.equals(version.getArticleId(), req.getArticleId())) {
            return Response.fail("版本与文章不匹配");
        }
        if (version.getStatus() != ArticleVersionStatusEnum.GRAY.getCode()) {
            return Response.fail("只能回滚灰度状态的版本");
        }

        Boolean success = transactionTemplate.execute(status -> {
            // 灰度版本改为草稿（线上表从未被灰度修改，无需恢复）
            version.setStatus(ArticleVersionStatusEnum.DRAFT.getCode());
            articleVersionDao.updateById(version);

            // 停用所有分群规则
            adminGrayReleaseDao.deactivateRulesByVersionId(version.getId());

            // 软删除预览令牌
            adminGrayReleaseDao.softDeleteTokensByVersionId(version.getId());

            // 查找当前稳定的已发布版本ID用于审计
            Long restoredVersionId = 0L;
            ArticleVersionDO latestPublished = articleVersionDao.selectLatestPublishedByArticleId(req.getArticleId());
            if (latestPublished != null) {
                restoredVersionId = latestPublished.getId();
            }

            // 插入回滚审计记录
            GrayRollbackAuditDO audit = GrayRollbackAuditDO.builder()
                    .articleId(req.getArticleId())
                    .grayVersionId(req.getGrayVersionId())
                    .restoredVersionId(restoredVersionId)
                    .reason(req.getReason() != null ? req.getReason() : "")
                    .operator("admin")
                    .build();
            adminGrayReleaseDao.insertRollbackAudit(audit);

            return true;
        });

        return Boolean.TRUE.equals(success) ? Response.success() : Response.fail("灰度回滚失败");
    }

    @Override
    public Response queryGrayMonitoring(Long grayVersionId) {
        ArticleVersionDO version = articleVersionDao.selectById(grayVersionId);
        if (version == null) {
            return Response.fail("版本不存在");
        }

        // 曝光统计
        long totalExposures = adminGrayReleaseDao.countExposuresByVersionId(grayVersionId);
        long tagHitExposures = adminGrayReleaseDao.countExposuresByVersionIdAndAccessType(
                grayVersionId, GrayAccessTypeEnum.TAG_HIT.getCode());
        long percentageExposures = adminGrayReleaseDao.countExposuresByVersionIdAndAccessType(
                grayVersionId, GrayAccessTypeEnum.PERCENTAGE_HIT.getCode());
        long previewLinkExposures = adminGrayReleaseDao.countExposuresByVersionIdAndAccessType(
                grayVersionId, GrayAccessTypeEnum.PREVIEW_TOKEN.getCode());

        // 反馈统计
        long totalFeedback = adminGrayReleaseDao.countFeedbackByVersionId(grayVersionId);
        long commentCount = adminGrayReleaseDao.countFeedbackByVersionIdAndType(
                grayVersionId, GrayFeedbackTypeEnum.COMMENT.getCode());
        long errorReportCount = adminGrayReleaseDao.countFeedbackByVersionIdAndType(
                grayVersionId, GrayFeedbackTypeEnum.ERROR_REPORT.getCode());

        // 活跃规则
        List<GraySegmentRuleDO> activeRules = adminGrayReleaseDao.selectRulesByVersionId(grayVersionId);
        List<GrayRuleConfigVO> ruleConfigs = activeRules.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .map(r -> parseRuleConfig(r.getRuleType(), r.getRuleConfig()))
                .collect(Collectors.toList());

        // 预览令牌
        List<GrayPreviewTokenDO> tokens = adminGrayReleaseDao.selectTokensByVersionId(grayVersionId);
        Date now = new Date();
        List<PreviewTokenInfoVO> tokenInfos = tokens.stream()
                .map(t -> PreviewTokenInfoVO.builder()
                        .token(t.getToken())
                        .expireAt(t.getExpireAt())
                        .createdBy(t.getCreatedBy())
                        .createTime(t.getCreateTime())
                        .expired(t.getExpireAt() != null && t.getExpireAt().before(now))
                        .build())
                .collect(Collectors.toList());

        QueryGrayMonitoringRspVO rsp = QueryGrayMonitoringRspVO.builder()
                .versionId(version.getId())
                .versionNum(version.getVersionNum())
                .title(version.getTitle())
                .totalExposures(totalExposures)
                .tagHitExposures(tagHitExposures)
                .percentageExposures(percentageExposures)
                .previewLinkExposures(previewLinkExposures)
                .totalFeedback(totalFeedback)
                .commentCount(commentCount)
                .errorReportCount(errorReportCount)
                .activeRules(ruleConfigs)
                .previewTokens(tokenInfos)
                .build();

        return Response.success(rsp);
    }

    @Override
    public Response submitFeedback(SubmitGrayFeedbackReqVO req) {
        // 查找该文章的活跃灰度版本
        ArticleVersionDO grayVersion = adminGrayReleaseDao.selectActiveGrayByArticleId(req.getArticleId());
        if (grayVersion == null) {
            return Response.fail("该文章当前没有活跃的灰度版本");
        }

        GrayFeedbackDO feedback = GrayFeedbackDO.builder()
                .versionId(grayVersion.getId())
                .articleId(req.getArticleId())
                .feedbackType(req.getFeedbackType())
                .content(req.getContent())
                .reporter("user")
                .build();
        adminGrayReleaseDao.insertFeedback(feedback);

        return Response.success();
    }

    @Override
    public Response addUserTags(AddUserTagReqVO req) {
        List<UserTagDO> userTags = req.getTagIds().stream()
                .map(tagId -> UserTagDO.builder()
                        .username(req.getUsername())
                        .tagId(tagId)
                        .build())
                .collect(Collectors.toList());

        adminUserTagDao.insertBatch(userTags);
        return Response.success();
    }

    @Override
    public Response removeUserTag(String username, Long tagId) {
        adminUserTagDao.deleteByUsernameAndTagId(username, tagId);
        return Response.success();
    }

    // ==================== 辅助方法 ====================

    private String buildRuleConfigJson(GrayRuleConfigVO config) {
        try {
            Map<String, Object> jsonMap = new HashMap<>();
            if (config.getRuleType() == GraySegmentRuleTypeEnum.TAG_USERS.getCode() && config.getTagIds() != null) {
                jsonMap.put("tagIds", config.getTagIds());
            } else if (config.getRuleType() == GraySegmentRuleTypeEnum.PERCENTAGE.getCode() && config.getPercentage() != null) {
                jsonMap.put("percentage", config.getPercentage());
            }
            return objectMapper.writeValueAsString(jsonMap);
        } catch (JsonProcessingException e) {
            log.error("序列化规则配置失败", e);
            return "{}";
        }
    }

    private GrayRuleConfigVO parseRuleConfig(Integer ruleType, String ruleConfig) {
        GrayRuleConfigVO config = new GrayRuleConfigVO();
        config.setRuleType(ruleType);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(ruleConfig, Map.class);
            if (ruleType == GraySegmentRuleTypeEnum.TAG_USERS.getCode()) {
                @SuppressWarnings("unchecked")
                List<Number> tagIds = (List<Number>) map.get("tagIds");
                if (tagIds != null) {
                    config.setTagIds(tagIds.stream().map(Number::longValue).collect(Collectors.toList()));
                }
            } else if (ruleType == GraySegmentRuleTypeEnum.PERCENTAGE.getCode()) {
                Number percentage = (Number) map.get("percentage");
                if (percentage != null) {
                    config.setPercentage(percentage.intValue());
                }
            }
        } catch (Exception e) {
            log.warn("解析规则配置失败: {}", ruleConfig, e);
        }
        return config;
    }
}
