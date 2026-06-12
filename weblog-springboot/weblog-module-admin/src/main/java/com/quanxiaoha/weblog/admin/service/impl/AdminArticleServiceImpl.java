package com.quanxiaoha.weblog.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-04-17 12:08
 * @description: 文章管理服务（含版本化发布）
 **/
@Service
@Slf4j
public class AdminArticleServiceImpl implements AdminArticleService {

    @Autowired
    private AdminArticleDao articleDao;
    @Autowired
    private AdminArticleContentDao articleContentDao;
    @Autowired
    private AdminArticleCategoryRelDao articleCategoryRelDao;
    @Autowired
    private AdminTagDao tagDao;
    @Autowired
    private AdminArticleTagRelDao articleTagRelDao;
    @Autowired
    private AdminArticleVersionDao articleVersionDao;
    @Autowired
    private AdminCategoryDao categoryDao;
    @Autowired
    private AdminGrayReleaseDao adminGrayReleaseDao;

    // 手动事务
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AdminArticleServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ==================== 原有方法（改写为版本化） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response publishArticle(PublishArticleReqVO publishArticleReqVO) {
        boolean isExecuteSuccess = transactionTemplate.execute(status -> {
            // 解析标签
            String tagIdsStr = resolveTagIds(publishArticleReqVO.getTags());

            // 创建版本记录并直接标记为已发布
            ArticleVersionDO version = ArticleVersionDO.builder()
                    .articleId(0L)
                    .versionNum(1)
                    .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                    .title(publishArticleReqVO.getTitle())
                    .titleImage(publishArticleReqVO.getTitleImage())
                    .description(publishArticleReqVO.getDescription())
                    .content(publishArticleReqVO.getContent())
                    .categoryId(publishArticleReqVO.getCategoryId())
                    .tagIds(tagIdsStr)
                    .publishedAt(new Date())
                    .build();
            articleVersionDao.insert(version);

            // 物化到 live 表
            Long articleId = materializeVersion(version);
            articleVersionDao.updateArticleId(version.getId(), articleId);
            return true;
        });

        return isExecuteSuccess ? Response.success() : Response.fail();
    }

    @Override
    public Response queryArticleDetail(QueryArticleDetailReqVO queryArticleDetailReqVO) {
        Long articleId = queryArticleDetailReqVO.getArticleId();

        // 优先返回最新草稿（管理员看到编辑中的内容）
        ArticleVersionDO latestDraft = articleVersionDao.selectLatestDraftByArticleId(articleId);
        if (latestDraft != null) {
            return Response.success(buildDetailRspFromVersion(latestDraft));
        }

        // 其次返回最新已发布版本
        ArticleVersionDO latestPublished = articleVersionDao.selectLatestPublishedByArticleId(articleId);
        if (latestPublished != null) {
            return Response.success(buildDetailRspFromVersion(latestPublished));
        }

        // 兜底：无版本记录时从 live 表读取（兼容历史数据）
        return queryArticleDetailLegacy(articleId);
    }

    @Override
    public Response queryArticlePageList(QueryArticlePageListReqVO queryArticlePageListReqVO) {
        Long current = queryArticlePageListReqVO.getCurrent();
        Long size = queryArticlePageListReqVO.getSize();
        Date startDate = queryArticlePageListReqVO.getStartDate();
        Date endDate = queryArticlePageListReqVO.getEndDate();
        String searchTitle = queryArticlePageListReqVO.getSearchTitle();

        Page<ArticleDO> articleDOPage = articleDao.queryArticlePageList(current, size, startDate, endDate, searchTitle);

        return Response.success(articleDOPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response deleteArticle(DeleteArticleReqVO deleteArticleReqVO) {
        Long articleId = deleteArticleReqVO.getArticleId();
        articleDao.deleteById(articleId);
        articleContentDao.deleteByArticleId(articleId);
        // 级联软删版本记录
        articleVersionDao.softDeleteByArticleId(articleId);
        return Response.success();
    }

    @Override
    public Response updateArticle(UpdateArticleReqVO updateArticleReqVO) {
        boolean isExecuteSuccess = transactionTemplate.execute(status -> {
            Long articleId = updateArticleReqVO.getId();
            Integer maxVersion = articleVersionDao.selectMaxVersionNum(articleId);

            String tagIdsStr = resolveTagIds(updateArticleReqVO.getTags());

            // 创建新的草稿版本（不触碰 live 表）
            ArticleVersionDO draft = ArticleVersionDO.builder()
                    .articleId(articleId)
                    .versionNum(maxVersion + 1)
                    .status(ArticleVersionStatusEnum.DRAFT.getCode())
                    .title(updateArticleReqVO.getTitle())
                    .titleImage(updateArticleReqVO.getTitleImage())
                    .description(updateArticleReqVO.getDescription())
                    .content(updateArticleReqVO.getContent())
                    .categoryId(updateArticleReqVO.getCategoryId())
                    .tagIds(tagIdsStr)
                    .build();
            articleVersionDao.insert(draft);
            return true;
        });

        return isExecuteSuccess ? Response.success() : Response.fail();
    }

    // ==================== 新增：版本化操作方法 ====================

    @Override
    public Response saveDraft(SaveArticleDraftReqVO req) {
        return transactionTemplate.execute(status -> {
            String tagIdsStr = resolveTagIds(req.getTags());

            if (req.getVersionId() != null) {
                // 更新已有草稿版本
                ArticleVersionDO existing = articleVersionDao.selectById(req.getVersionId());
                if (existing == null || existing.getStatus() != ArticleVersionStatusEnum.DRAFT.getCode()) {
                    return Response.fail("只能编辑草稿状态的版本");
                }
                existing.setTitle(req.getTitle());
                existing.setTitleImage(req.getTitleImage());
                existing.setDescription(req.getDescription());
                existing.setContent(req.getContent());
                existing.setCategoryId(req.getCategoryId());
                existing.setTagIds(tagIdsStr);
                articleVersionDao.updateById(existing);
                return Response.success();
            }

            // 新建草稿版本
            Long articleId = req.getArticleId() != null ? req.getArticleId() : 0L;
            Integer maxVersion = articleId > 0 ? articleVersionDao.selectMaxVersionNum(articleId) : 0;

            ArticleVersionDO draft = ArticleVersionDO.builder()
                    .articleId(articleId)
                    .versionNum(maxVersion + 1)
                    .status(ArticleVersionStatusEnum.DRAFT.getCode())
                    .title(req.getTitle())
                    .titleImage(req.getTitleImage())
                    .description(req.getDescription())
                    .content(req.getContent())
                    .categoryId(req.getCategoryId())
                    .tagIds(tagIdsStr)
                    .build();
            articleVersionDao.insert(draft);

            Map<String, Object> data = new HashMap<>();
            data.put("versionId", draft.getId());
            return Response.success(data);
        });
    }

    @Override
    public Response publishVersion(PublishArticleVersionReqVO req) {
        ArticleVersionDO version = articleVersionDao.selectById(req.getVersionId());
        if (version == null) {
            return Response.fail("版本不存在");
        }
        if (version.getStatus() == ArticleVersionStatusEnum.PUBLISHED.getCode()) {
            return Response.fail("该版本已发布");
        }

        // 定时发布：标记为 PENDING_PUBLISH
        if (req.getScheduledAt() != null && req.getScheduledAt().after(new Date())) {
            // 灰度冲突检查：有灰度版本时不允许定时发布
            if (version.getArticleId() != null && version.getArticleId() > 0) {
                ArticleVersionDO activeGray = adminGrayReleaseDao.selectActiveGrayByArticleId(version.getArticleId());
                if (activeGray != null) {
                    return Response.fail("该文章存在活跃的灰度版本，请先完成全量发布或回滚灰度版本后再进行定时发布");
                }
            }
            version.setStatus(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode());
            version.setScheduledAt(req.getScheduledAt());
            articleVersionDao.updateById(version);
            return Response.success();
        }

        // 立即发布
        return executePublish(version);
    }

    @Override
    public Response queryArticleVersionList(Long articleId) {
        List<ArticleVersionDO> versions = articleVersionDao.selectAllByArticleId(articleId);
        List<QueryArticleVersionListRspVO> list = versions.stream()
                .map(v -> QueryArticleVersionListRspVO.builder()
                        .versionId(v.getId())
                        .versionNum(v.getVersionNum())
                        .status(ArticleVersionStatusEnum.valueOf(v.getStatus()).name())
                        .title(v.getTitle())
                        .createTime(v.getCreateTime())
                        .publishedAt(v.getPublishedAt())
                        .scheduledAt(v.getScheduledAt())
                        .build())
                .collect(Collectors.toList());
        return Response.success(list);
    }

    @Override
    public Response queryVersionDetail(Long versionId) {
        ArticleVersionDO version = articleVersionDao.selectById(versionId);
        if (version == null) {
            return Response.fail("版本不存在");
        }
        return Response.success(buildDetailRspFromVersion(version));
    }

    @Override
    public Response queryVersionDiff(QueryVersionDiffReqVO req) {
        ArticleVersionDO left = articleVersionDao.selectById(req.getLeftVersionId());
        ArticleVersionDO right = articleVersionDao.selectById(req.getRightVersionId());
        if (left == null || right == null) {
            return Response.fail("版本不存在");
        }

        QueryVersionDiffRspVO diff = QueryVersionDiffRspVO.builder()
                .leftVersionId(left.getId())
                .leftVersionNum(left.getVersionNum())
                .rightVersionId(right.getId())
                .rightVersionNum(right.getVersionNum())
                .build();

        // 标题
        diff.setTitleChanged(!Objects.equals(left.getTitle(), right.getTitle()));
        diff.setLeftTitle(left.getTitle());
        diff.setRightTitle(right.getTitle());

        // 内容差异
        diff.setContentChanged(!Objects.equals(left.getContent(), right.getContent()));
        if (diff.isContentChanged()) {
            diff.setContentDiff(computeUnifiedDiff(left.getContent(), right.getContent()));
        }

        // 分类
        diff.setCategoryChanged(!Objects.equals(left.getCategoryId(), right.getCategoryId()));
        diff.setLeftCategoryId(left.getCategoryId());
        diff.setRightCategoryId(right.getCategoryId());
        if (diff.isCategoryChanged()) {
            diff.setLeftCategoryName(resolveCategoryName(left.getCategoryId()));
            diff.setRightCategoryName(resolveCategoryName(right.getCategoryId()));
        }

        // 标签
        List<Long> leftTags = parseTagIds(left.getTagIds());
        List<Long> rightTags = parseTagIds(right.getTagIds());
        Set<Long> leftTagSet = new HashSet<>(leftTags);
        Set<Long> rightTagSet = new HashSet<>(rightTags);
        diff.setTagsChanged(!leftTagSet.equals(rightTagSet));
        diff.setLeftTagIds(leftTags);
        diff.setRightTagIds(rightTags);
        if (diff.isTagsChanged()) {
            Map<Long, String> tagIdNameMap = buildTagIdNameMap();
            List<Long> addedIds = rightTags.stream().filter(id -> !leftTagSet.contains(id)).collect(Collectors.toList());
            List<Long> removedIds = leftTags.stream().filter(id -> !rightTagSet.contains(id)).collect(Collectors.toList());
            diff.setAddedTagNames(addedIds.stream().map(id -> tagIdNameMap.getOrDefault(id, String.valueOf(id))).collect(Collectors.toList()));
            diff.setRemovedTagNames(removedIds.stream().map(id -> tagIdNameMap.getOrDefault(id, String.valueOf(id))).collect(Collectors.toList()));
        }

        // 描述和题图
        diff.setDescriptionChanged(!Objects.equals(left.getDescription(), right.getDescription()));
        diff.setTitleImageChanged(!Objects.equals(left.getTitleImage(), right.getTitleImage()));

        return Response.success(diff);
    }

    @Override
    public Response rollbackToVersion(RollbackArticleVersionReqVO req) {
        ArticleVersionDO target = articleVersionDao.selectById(req.getTargetVersionId());
        if (target == null) {
            return Response.fail("目标版本不存在");
        }
        if (!Objects.equals(target.getArticleId(), req.getArticleId())) {
            return Response.fail("版本与文章不匹配");
        }
        if (target.getStatus() != ArticleVersionStatusEnum.PUBLISHED.getCode()) {
            return Response.fail("只能回滚到已发布的版本");
        }

        return transactionTemplate.execute(status -> {
            // 物化目标版本到 live 表
            materializeUpdateToLiveTables(target, req.getArticleId());

            // 取消该文章所有待发布的定时任务，防止被调度器覆盖回滚结果
            int cancelled = articleVersionDao.cancelPendingPublishByArticleId(req.getArticleId());
            if (cancelled > 0) {
                log.info("文章 {} 回滚时取消了 {} 个待发布版本", req.getArticleId(), cancelled);
            }

            // 创建新版本记录保留审计轨迹
            Integer maxVersion = articleVersionDao.selectMaxVersionNum(req.getArticleId());
            ArticleVersionDO rollbackVersion = ArticleVersionDO.builder()
                    .articleId(req.getArticleId())
                    .versionNum(maxVersion + 1)
                    .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                    .title(target.getTitle())
                    .titleImage(target.getTitleImage())
                    .description(target.getDescription())
                    .content(target.getContent())
                    .categoryId(target.getCategoryId())
                    .tagIds(target.getTagIds())
                    .publishedAt(new Date())
                    .build();
            articleVersionDao.insert(rollbackVersion);
            return Response.success();
        });
    }

    @Override
    public Response recoverVersion(Long versionId) {
        ArticleVersionDO target = articleVersionDao.selectById(versionId);
        if (target == null) {
            return Response.fail("版本不存在");
        }
        if (target.getArticleId() == 0L) {
            return Response.fail("该版本未关联文章");
        }

        RollbackArticleVersionReqVO req = RollbackArticleVersionReqVO.builder()
                .articleId(target.getArticleId())
                .targetVersionId(versionId)
                .build();
        return rollbackToVersion(req);
    }

    // ==================== 灰度发布物化代理方法 ====================

    /**
     * 灰度全量发布时调用：物化新版本到 live 表（新文章首次发布）
     */
    public Long materializeVersionForGray(ArticleVersionDO version) {
        return materializeVersion(version);
    }

    /**
     * 灰度全量发布时调用：物化版本更新到 live 表（已有文章更新）
     */
    public void materializeUpdateForGray(ArticleVersionDO version, Long articleId) {
        materializeUpdateToLiveTables(version, articleId);
    }

    // ==================== 核心私有方法 ====================

    /**
     * 定时发布调度器调用：状态已由 CAS 更新为 PUBLISHED，只需物化到 live 表。
     * 事务失败时回退到独立事务做恢复，避免 setRollbackOnly 导致恢复操作一起回滚。
     */
    public Response publishScheduledVersion(ArticleVersionDO version) {
        try {
            Boolean success = transactionTemplate.execute(status -> {
                Long articleId;
                if (version.getArticleId() == 0L) {
                    articleId = materializeVersion(version);
                    articleVersionDao.updateArticleId(version.getId(), articleId);
                } else {
                    articleId = version.getArticleId();
                    materializeUpdateToLiveTables(version, articleId);
                }

                version.setPublishedAt(new Date());
                articleVersionDao.updateById(version);
                return true;
            });
            return Boolean.TRUE.equals(success) ? Response.success() : Response.fail("定时发布失败");
        } catch (Exception e) {
            log.error("定时发布版本 {} 失败", version.getId(), e);
            // 事务已回滚，在独立操作中执行恢复
            handlePublishFailure(version);
            return Response.fail("定时发布失败: " + e.getMessage());
        }
    }

    /**
     * 执行发布：物化版本到 live 表。
     * 事务失败时回退到独立操作中执行恢复。
     */
    Response executePublish(ArticleVersionDO version) {
        try {
            Boolean success = transactionTemplate.execute(status -> {
                Long articleId;
                if (version.getArticleId() == 0L) {
                    // 首次发布：创建文章
                    articleId = materializeVersion(version);
                    articleVersionDao.updateArticleId(version.getId(), articleId);
                } else {
                    articleId = version.getArticleId();
                    materializeUpdateToLiveTables(version, articleId);
                }

                version.setStatus(ArticleVersionStatusEnum.PUBLISHED.getCode());
                version.setPublishedAt(new Date());
                articleVersionDao.updateById(version);
                return true;
            });
            return Boolean.TRUE.equals(success) ? Response.success() : Response.fail("发布失败");
        } catch (Exception e) {
            log.error("发布版本 {} 失败，执行回滚", version.getId(), e);
            // 事务已回滚，在独立操作中执行恢复
            handlePublishFailure(version);
            return Response.fail("发布失败: " + e.getMessage());
        }
    }

    /**
     * 物化新版本到 live 表（新文章首次发布）
     */
    private Long materializeVersion(ArticleVersionDO version) {
        // 插入 t_article
        ArticleDO articleDO = ArticleDO.builder()
                .title(version.getTitle())
                .titleImage(version.getTitleImage())
                .description(version.getDescription())
                .build();
        articleDao.insertArticle(articleDO);
        Long articleId = articleDO.getId();

        // 插入 t_article_content
        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(articleId)
                .content(version.getContent())
                .build();
        articleContentDao.insertArticleContent(contentDO);

        // 插入 t_article_category_rel
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(articleId)
                .categoryId(version.getCategoryId())
                .build();
        articleCategoryRelDao.insert(catRel);

        // 插入 t_article_tag_rel
        List<Long> tagIds = parseTagIds(version.getTagIds());
        if (!tagIds.isEmpty()) {
            List<ArticleTagRelDO> tagRels = tagIds.stream()
                    .map(tagId -> ArticleTagRelDO.builder().articleId(articleId).tagId(tagId).build())
                    .collect(Collectors.toList());
            articleTagRelDao.insertBatch(tagRels);
        }

        return articleId;
    }

    /**
     * 物化版本更新到 live 表（已有文章更新，保留 read_num）
     */
    private void materializeUpdateToLiveTables(ArticleVersionDO version, Long articleId) {
        // 更新 t_article（不覆盖 read_num）
        ArticleDO articleDO = ArticleDO.builder()
                .id(articleId)
                .title(version.getTitle())
                .titleImage(version.getTitleImage())
                .description(version.getDescription())
                .updateTime(new Date())
                .build();
        articleDao.updateById(articleDO);

        // 更新 t_article_content
        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(articleId)
                .content(version.getContent())
                .build();
        articleContentDao.updateByArticleId(contentDO);

        // 原子切换分类和标签关系
        atomicUpdateRelations(articleId, version.getCategoryId(), version.getTagIds());
    }

    /**
     * 原子切换分类和标签关系：先删后插，失败时尝试恢复旧关系以保证一致性。
     * 调用方须在事务内，事务回滚时旧数据自动恢复；此处的恢复逻辑用于
     * 捕获异常后在独立事务中补救的场景。
     */
    private void atomicUpdateRelations(Long articleId, Long categoryId, String tagIds) {
        // 备份旧关系用于异常恢复
        ArticleCategoryRelDO oldCatRel = articleCategoryRelDao.selectByArticleId(articleId);
        List<ArticleTagRelDO> oldTagRels = articleTagRelDao.selectByArticleId(articleId);

        try {
            // 替换 t_article_category_rel
            articleCategoryRelDao.deleteByArticleId(articleId);
            articleCategoryRelDao.insert(ArticleCategoryRelDO.builder()
                    .articleId(articleId).categoryId(categoryId).build());

            // 替换 t_article_tag_rel
            articleTagRelDao.deleteByArticleId(articleId);
            List<Long> tagIdList = parseTagIds(tagIds);
            if (!tagIdList.isEmpty()) {
                List<ArticleTagRelDO> tagRels = tagIdList.stream()
                        .map(tagId -> ArticleTagRelDO.builder().articleId(articleId).tagId(tagId).build())
                        .collect(Collectors.toList());
                articleTagRelDao.insertBatch(tagRels);
            }
        } catch (Exception e) {
            log.error("文章 {} 标签/分类关系切换失败，尝试恢复旧关系", articleId, e);
            // 在事务回滚之前尝试恢复——如果外层事务最终回滚，这些操作也会一起回滚，
            // 但旧数据本来就在事务开始时的快照中，所以不影响正确性。
            tryRestoreRelations(articleId, oldCatRel, oldTagRels);
            throw e;
        }
    }

    /**
     * 尝试恢复分类和标签关系到备份状态
     */
    private void tryRestoreRelations(Long articleId, ArticleCategoryRelDO oldCatRel, List<ArticleTagRelDO> oldTagRels) {
        try {
            articleCategoryRelDao.deleteByArticleId(articleId);
            if (oldCatRel != null) {
                articleCategoryRelDao.insert(oldCatRel);
            }

            articleTagRelDao.deleteByArticleId(articleId);
            if (oldTagRels != null && !oldTagRels.isEmpty()) {
                articleTagRelDao.insertBatch(oldTagRels);
            }
            log.info("文章 {} 标签/分类关系已恢复", articleId);
        } catch (Exception restoreEx) {
            log.error("文章 {} 关系恢复也失败了: {}", articleId, restoreEx.getMessage(), restoreEx);
        }
    }

    /**
     * 发布失败时回滚到上一个已发布版本
     */
    private void rollbackToPreviousVersion(Long articleId, Long failedVersionId) {
        try {
            ArticleVersionDO prevPublished = articleVersionDao
                    .selectPreviousPublishedByArticleId(articleId, failedVersionId);
            if (prevPublished != null) {
                materializeUpdateToLiveTables(prevPublished, articleId);
                log.info("文章 {} 已回滚到版本 {}", articleId, prevPublished.getVersionNum());
            } else {
                log.warn("文章 {} 无可回滚的历史版本", articleId);
            }
        } catch (Exception rollbackEx) {
            log.error("文章 {} 回滚也失败了: {}", articleId, rollbackEx.getMessage(), rollbackEx);
        }
    }

    /**
     * 检查版本是否已过期（被更新的已发布版本取代）。
     * 调度器在 CAS 之后、物化之前调用，避免把已被回滚覆盖的旧版本重新物化到线上。
     */
    public boolean isVersionStale(ArticleVersionDO version) {
        if (version.getArticleId() == null || version.getArticleId() == 0L) {
            // 新文章首次发布，不存在过期问题
            return false;
        }
        ArticleVersionDO latestPublished = articleVersionDao.selectLatestPublishedByArticleId(version.getArticleId());
        if (latestPublished != null && latestPublished.getVersionNum() > version.getVersionNum()) {
            log.info("版本 {} (v{}) 已过期，最新已发布版本为 v{}",
                    version.getId(), version.getVersionNum(), latestPublished.getVersionNum());
            return true;
        }
        return false;
    }

    /**
     * 发布失败后的恢复处理：尝试回滚到上一个已发布版本，并将失败版本标记为草稿。
     * 此方法在失败事务已回滚之后调用，所有操作在独立事务中执行。
     */
    public void handlePublishFailure(ArticleVersionDO version) {
        try {
            // 尝试回滚到上一个已发布版本（恢复线上内容）
            if (version.getArticleId() != null && version.getArticleId() != 0L) {
                rollbackToPreviousVersion(version.getArticleId(), version.getId());
            }
            // 将失败版本标记为草稿供管理员修改
            articleVersionDao.updateStatus(version.getId(), ArticleVersionStatusEnum.DRAFT.getCode());
            log.info("版本 {} 已标记为草稿", version.getId());
        } catch (Exception recoveryEx) {
            log.error("版本 {} 发布失败后的恢复操作也失败了: {}", version.getId(), recoveryEx.getMessage(), recoveryEx);
            // 最后兜底：无论如何尝试标记为草稿
            try {
                articleVersionDao.updateStatus(version.getId(), ArticleVersionStatusEnum.DRAFT.getCode());
            } catch (Exception finalEx) {
                log.error("版本 {} 最终兜底标记草稿也失败", version.getId(), finalEx);
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析标签名/ID混合列表，返回逗号分隔的标签ID字符串
     * 兼容现有 handleTagBiz 逻辑：数字字符串视为已有标签ID，其他视为标签名称
     */
    private String resolveTagIds(List<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return "";
        }

        List<TagDO> allTags = tagDao.selectAll();
        List<String> tagIdStrs = new ArrayList<>();
        if (!CollectionUtils.isEmpty(allTags)) {
            tagIdStrs = allTags.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.toList());
        }

        List<Long> resolvedIds = new ArrayList<>();
        List<String> existingTagIdStrs = tagIdStrs;

        for (String tag : tags) {
            if (existingTagIdStrs.contains(tag)) {
                // 已有标签，直接用ID
                resolvedIds.add(Long.valueOf(tag));
            } else {
                // 检查是否是纯数字（已有标签ID但以不同格式传入）
                try {
                    Long tagId = Long.valueOf(tag);
                    resolvedIds.add(tagId);
                    continue;
                } catch (NumberFormatException ignored) {
                    // 不是数字，当作标签名称处理
                }

                // 按名称查找已有标签
                Optional<TagDO> existingByName = allTags.stream()
                        .filter(t -> t.getName().equals(tag))
                        .findFirst();
                if (existingByName.isPresent()) {
                    resolvedIds.add(existingByName.get().getId());
                } else {
                    // 创建新标签
                    TagDO newTag = TagDO.builder()
                            .name(tag)
                            .createTime(new Date())
                            .updateTime(new Date())
                            .build();
                    tagDao.insert(newTag);
                    resolvedIds.add(newTag.getId());
                }
            }
        }

        return resolvedIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 解析逗号分隔的标签ID字符串为列表
     */
    private List<Long> parseTagIds(String tagIdsStr) {
        if (tagIdsStr == null || tagIdsStr.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tagIdsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 从版本记录构建文章详情响应
     */
    private QueryArticleDetailRspVO buildDetailRspFromVersion(ArticleVersionDO v) {
        return QueryArticleDetailRspVO.builder()
                .id(v.getArticleId() == 0L ? null : v.getArticleId())
                .title(v.getTitle())
                .titleImage(v.getTitleImage())
                .content(v.getContent())
                .categoryId(v.getCategoryId())
                .tagIds(parseTagIds(v.getTagIds()))
                .description(v.getDescription())
                .build();
    }

    /**
     * 兼容历史数据：从 live 表读取文章详情
     */
    private Response queryArticleDetailLegacy(Long articleId) {
        ArticleDO articleDO = articleDao.queryByArticleId(articleId);
        if (articleDO == null) {
            return Response.fail("文章不存在");
        }
        ArticleContentDO articleContentDO = articleContentDao.queryByArticleId(articleId);

        ArticleCategoryRelDO articleCategoryRelDO = articleCategoryRelDao.selectByArticleId(articleId);

        List<ArticleTagRelDO> articleTagRelDOS = articleTagRelDao.selectByArticleId(articleId);
        List<Long> tagIds = articleTagRelDOS.stream().map(p -> p.getTagId()).collect(Collectors.toList());

        QueryArticleDetailRspVO queryArticleDetailRspVO = QueryArticleDetailRspVO.builder()
                .id(articleDO.getId())
                .title(articleDO.getTitle())
                .titleImage(articleDO.getTitleImage())
                .content(articleContentDO.getContent())
                .categoryId(articleCategoryRelDO != null ? articleCategoryRelDO.getCategoryId() : null)
                .tagIds(tagIds)
                .description(articleDO.getDescription())
                .build();

        return Response.success(queryArticleDetailRspVO);
    }

    /**
     * 解析分类名称
     */
    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null || categoryId == 0L) {
            return "";
        }
        List<CategoryDO> allCategories = categoryDao.selectAllCategory();
        return allCategories.stream()
                .filter(c -> Objects.equals(c.getId(), categoryId))
                .map(CategoryDO::getName)
                .findFirst()
                .orElse(String.valueOf(categoryId));
    }

    /**
     * 构建标签ID→名称映射
     */
    private Map<Long, String> buildTagIdNameMap() {
        List<TagDO> allTags = tagDao.selectAll();
        if (CollectionUtils.isEmpty(allTags)) {
            return Collections.emptyMap();
        }
        return allTags.stream()
                .collect(Collectors.toMap(TagDO::getId, TagDO::getName, (a, b) -> a));
    }

    /**
     * 计算行级 unified diff
     */
    private String computeUnifiedDiff(String left, String right) {
        List<String> leftLines = left == null ? Collections.emptyList()
                : Arrays.asList(left.split("\n", -1));
        List<String> rightLines = right == null ? Collections.emptyList()
                : Arrays.asList(right.split("\n", -1));

        StringBuilder sb = new StringBuilder();
        int maxLen = Math.max(leftLines.size(), rightLines.size());
        for (int i = 0; i < maxLen; i++) {
            String lLine = i < leftLines.size() ? leftLines.get(i) : null;
            String rLine = i < rightLines.size() ? rightLines.get(i) : null;
            if (!Objects.equals(lLine, rLine)) {
                if (lLine != null) {
                    sb.append("- ").append(lLine).append("\n");
                }
                if (rLine != null) {
                    sb.append("+ ").append(rLine).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 处理标签相关业务（保留原有逻辑兼容）
     */
    public void handleTagBiz(Long articleId, List<String> publishTags) {
        List<TagDO> tagDOS = tagDao.selectAll();

        List<String> noExistTags = null;
        List<String> existTags = null;
        if (!CollectionUtils.isEmpty(tagDOS)) {
            List<String> tagIds = tagDOS.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.toList());
            noExistTags = publishTags.stream().filter(p -> !tagIds.contains(p)).collect(Collectors.toList());
            existTags = publishTags.stream().filter(p -> tagIds.contains(p)).collect(Collectors.toList());
        }

        if (!CollectionUtils.isEmpty(noExistTags)) {
            List<ArticleTagRelDO> articleTagRelDOS = Lists.newArrayList();
            noExistTags.forEach(noExistTag -> {
                TagDO tagDO = TagDO.builder()
                        .name(noExistTag)
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build();

                tagDao.insert(tagDO);
                Long tagId = tagDO.getId();

                ArticleTagRelDO articleTagRelDO = ArticleTagRelDO.builder()
                        .articleId(articleId)
                        .tagId(tagId)
                        .build();
                articleTagRelDOS.add(articleTagRelDO);
            });

            articleTagRelDao.insertBatch(articleTagRelDOS);
        }

        if (!CollectionUtils.isEmpty(existTags)) {
            List<ArticleTagRelDO> articleTagRelDOS = Lists.newArrayList();
            existTags.forEach(existTagId -> {
                ArticleTagRelDO articleTagRelDO = ArticleTagRelDO.builder()
                        .articleId(articleId)
                        .tagId(Long.valueOf(existTagId))
                        .build();
                articleTagRelDOS.add(articleTagRelDO);
            });
            articleTagRelDao.insertBatch(articleTagRelDOS);
        }
    }
}
