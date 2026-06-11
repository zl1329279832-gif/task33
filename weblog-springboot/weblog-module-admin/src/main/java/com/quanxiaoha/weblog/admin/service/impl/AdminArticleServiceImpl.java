package com.quanxiaoha.weblog.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.ResponseCodeEnum;
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
 * @description: TODO
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

    // 手动事务
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AdminArticleServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response publishArticle(PublishArticleReqVO publishArticleReqVO) {
        boolean isExecuteSuccess = transactionTemplate.execute(status -> {
            ArticleDO articleDO = ArticleDO.builder()
                    .title(publishArticleReqVO.getTitle())
                    .titleImage(publishArticleReqVO.getTitleImage())
                    .description(publishArticleReqVO.getDescription())
                    .build();
            articleDao.insertArticle(articleDO);

            Long articleId = articleDO.getId();

            ArticleContentDO articleContentDO = ArticleContentDO.builder()
                    .articleId(articleId)
                    .content(publishArticleReqVO.getContent())
                    .build();
            articleContentDao.insertArticleContent(articleContentDO);

            // 所属分类
            ArticleCategoryRelDO articleCategoryRelDO = ArticleCategoryRelDO.builder()
                    .articleId(articleId)
                    .categoryId(publishArticleReqVO.getCategoryId())
                    .build();
            articleCategoryRelDao.insert(articleCategoryRelDO);

            // 标签
            // 提交的标签
            List<String> publishTags = publishArticleReqVO.getTags();
            handleTagBiz(articleId, publishTags);
            return true;
        });

        return isExecuteSuccess ? Response.success() : Response.fail();
    }

    @Override
    public Response queryArticleDetail(QueryArticleDetailReqVO queryArticleDetailReqVO) {
        Long articleId = queryArticleDetailReqVO.getArticleId();
        ArticleDO articleDO = articleDao.queryByArticleId(articleId);
        ArticleContentDO articleContentDO = articleContentDao.queryByArticleId(articleId);

        // 所属分类
        ArticleCategoryRelDO articleCategoryRelDO = articleCategoryRelDao.selectByArticleId(articleId);

        // 对应标签
        List<ArticleTagRelDO> articleTagRelDOS = articleTagRelDao.selectByArticleId(articleId);
        List<Long> tagIds = articleTagRelDOS.stream().map(p -> p.getTagId()).collect(Collectors.toList());

        // 检查是否存在草稿版本，如果有则优先返回草稿数据
        ArticleVersionDO draftVersion = articleVersionDao.selectLatestDraftByArticleId(articleId);
        if (draftVersion != null) {
            List<Long> draftTagIds = new ArrayList<>();
            if (draftVersion.getTagIds() != null && !draftVersion.getTagIds().isEmpty()) {
                for (String id : draftVersion.getTagIds().split(",")) {
                    if (!id.trim().isEmpty()) {
                        draftTagIds.add(Long.valueOf(id.trim()));
                    }
                }
            }
            QueryArticleDetailRspVO rspVO = QueryArticleDetailRspVO.builder()
                    .id(articleDO.getId())
                    .title(draftVersion.getTitle())
                    .titleImage(draftVersion.getTitleImage())
                    .content(draftVersion.getContent())
                    .categoryId(draftVersion.getCategoryId())
                    .tagIds(draftTagIds)
                    .description(draftVersion.getDescription())
                    .build();
            return Response.success(rspVO);
        }

        QueryArticleDetailRspVO queryArticleDetailRspVO = QueryArticleDetailRspVO.builder()
                .id(articleDO.getId())
                .title(articleDO.getTitle())
                .titleImage(articleDO.getTitleImage())
                .content(articleContentDO.getContent())
                .categoryId(articleCategoryRelDO.getCategoryId())
                .tagIds(tagIds)
                .description(articleDO.getDescription())
                .build();

        return Response.success(queryArticleDetailRspVO);
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
        // 软删除该文章的所有版本
        articleVersionDao.softDeleteByArticleId(articleId);
        return Response.success();
    }

    @Override
    // @Transactional(rollbackFor = Exception.class)
    public Response updateArticle(UpdateArticleReqVO updateArticleReqVO) {
        boolean isExecuteSuccess = transactionTemplate.execute(status -> {
            Long articleId = updateArticleReqVO.getId();

            ArticleDO articleDO = ArticleDO.builder()
                    .id(articleId)
                    .title(updateArticleReqVO.getTitle())
                    .titleImage(updateArticleReqVO.getTitleImage())
                    .description(updateArticleReqVO.getDescription())
                    .updateTime(new Date())
                    .build();
            articleDao.updateById(articleDO);

            ArticleContentDO articleContentDO = ArticleContentDO.builder()
                    .articleId(articleId)
                    .content(updateArticleReqVO.getContent())
                    .build();
            articleContentDao.updateByArticleId(articleContentDO);

            // 更新文章分类
            articleCategoryRelDao.deleteByArticleId(articleId);
            ArticleCategoryRelDO articleCategoryRelDO = ArticleCategoryRelDO.builder()
                    .articleId(articleId)
                    .categoryId(updateArticleReqVO.getCategoryId())
                    .build();
            articleCategoryRelDao.insert(articleCategoryRelDO);

            // 更新文章标签
            articleTagRelDao.deleteByArticleId(articleId);
            // 提交的标签
            List<String> publishTags = updateArticleReqVO.getTags();
            handleTagBiz(articleId, publishTags);
            return true;
        });

        return isExecuteSuccess ? Response.success() : Response.fail();
    }

    @Override
    public Response saveDraft(SaveDraftReqVO saveDraftReqVO) {
        Long articleId = saveDraftReqVO.getArticleId();

        // 解析标签为ID列表
        String tagIdsStr = resolveTagIds(saveDraftReqVO.getTags());

        if (articleId == null || articleId == 0) {
            // 新文章草稿，不写入线上表
            ArticleVersionDO versionDO = ArticleVersionDO.builder()
                    .articleId(0L)
                    .versionNum(1)
                    .status(ArticleVersionStatusEnum.DRAFT.getCode())
                    .title(saveDraftReqVO.getTitle())
                    .titleImage(saveDraftReqVO.getTitleImage())
                    .description(saveDraftReqVO.getDescription())
                    .content(saveDraftReqVO.getContent())
                    .categoryId(saveDraftReqVO.getCategoryId())
                    .tagIds(tagIdsStr)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .isDeleted(false)
                    .build();
            articleVersionDao.insert(versionDO);
            return Response.success(versionDO);
        }

        // 已有文章，查找已有草稿
        ArticleVersionDO existingDraft = articleVersionDao.selectLatestDraftByArticleId(articleId);
        if (existingDraft != null) {
            // 更新现有草稿
            existingDraft.setTitle(saveDraftReqVO.getTitle());
            existingDraft.setTitleImage(saveDraftReqVO.getTitleImage());
            existingDraft.setDescription(saveDraftReqVO.getDescription());
            existingDraft.setContent(saveDraftReqVO.getContent());
            existingDraft.setCategoryId(saveDraftReqVO.getCategoryId());
            existingDraft.setTagIds(tagIdsStr);
            existingDraft.setUpdateTime(new Date());
            articleVersionDao.updateById(existingDraft);
            return Response.success(existingDraft);
        }

        // 创建新版本
        Integer maxVersionNum = articleVersionDao.selectMaxVersionNum(articleId);
        ArticleVersionDO versionDO = ArticleVersionDO.builder()
                .articleId(articleId)
                .versionNum(maxVersionNum + 1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title(saveDraftReqVO.getTitle())
                .titleImage(saveDraftReqVO.getTitleImage())
                .description(saveDraftReqVO.getDescription())
                .content(saveDraftReqVO.getContent())
                .categoryId(saveDraftReqVO.getCategoryId())
                .tagIds(tagIdsStr)
                .createTime(new Date())
                .updateTime(new Date())
                .isDeleted(false)
                .build();
        articleVersionDao.insert(versionDO);
        return Response.success(versionDO);
    }

    @Override
    public Response publishVersion(PublishVersionReqVO publishVersionReqVO) {
        Long versionId = publishVersionReqVO.getVersionId();
        ArticleVersionDO version = articleVersionDao.selectById(versionId);

        if (version == null) {
            return Response.fail(ResponseCodeEnum.VERSION_NOT_FOUND);
        }

        if (version.getStatus().equals(ArticleVersionStatusEnum.PUBLISHED.getCode())) {
            return Response.fail(ResponseCodeEnum.VERSION_ALREADY_PUBLISHED);
        }

        if (!version.getStatus().equals(ArticleVersionStatusEnum.DRAFT.getCode())) {
            return Response.fail(ResponseCodeEnum.VERSION_NOT_DRAFT);
        }

        // 定时发布
        Date scheduledAt = publishVersionReqVO.getScheduledAt();
        if (scheduledAt != null && scheduledAt.after(new Date())) {
            version.setStatus(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode());
            version.setScheduledAt(scheduledAt);
            version.setUpdateTime(new Date());
            articleVersionDao.updateById(version);
            return Response.success();
        }

        // 立即发布
        publishVersionToLive(version);
        return Response.success();
    }

    @Override
    public void publishVersionToLive(ArticleVersionDO version) {
        transactionTemplate.execute(status -> {
            Long articleId = version.getArticleId();

            if (articleId == null || articleId == 0) {
                // 新文章首次发布，创建文章记录
                ArticleDO articleDO = ArticleDO.builder()
                        .title(version.getTitle())
                        .titleImage(version.getTitleImage())
                        .description(version.getDescription())
                        .build();
                articleDao.insertArticle(articleDO);
                articleId = articleDO.getId();
                version.setArticleId(articleId);
            } else {
                // 更新已有文章（不更新 readNum）
                ArticleDO articleDO = ArticleDO.builder()
                        .id(articleId)
                        .title(version.getTitle())
                        .titleImage(version.getTitleImage())
                        .description(version.getDescription())
                        .updateTime(new Date())
                        .build();
                articleDao.updateById(articleDO);
            }

            // 更新文章内容
            articleContentDao.deleteByArticleId(articleId);
            ArticleContentDO articleContentDO = ArticleContentDO.builder()
                    .articleId(articleId)
                    .content(version.getContent())
                    .build();
            articleContentDao.insertArticleContent(articleContentDO);

            // 更新文章分类
            articleCategoryRelDao.deleteByArticleId(articleId);
            ArticleCategoryRelDO articleCategoryRelDO = ArticleCategoryRelDO.builder()
                    .articleId(articleId)
                    .categoryId(version.getCategoryId())
                    .build();
            articleCategoryRelDao.insert(articleCategoryRelDO);

            // 更新文章标签
            articleTagRelDao.deleteByArticleId(articleId);
            if (version.getTagIds() != null && !version.getTagIds().isEmpty()) {
                List<ArticleTagRelDO> tagRelDOS = Lists.newArrayList();
                for (String tagIdStr : version.getTagIds().split(",")) {
                    if (!tagIdStr.trim().isEmpty()) {
                        ArticleTagRelDO tagRelDO = ArticleTagRelDO.builder()
                                .articleId(version.getArticleId())
                                .tagId(Long.valueOf(tagIdStr.trim()))
                                .build();
                        tagRelDOS.add(tagRelDO);
                    }
                }
                if (!tagRelDOS.isEmpty()) {
                    articleTagRelDao.insertBatch(tagRelDOS);
                }
            }

            // 将该文章之前的已发布版本标记为已回滚
            List<ArticleVersionDO> publishedVersions = articleVersionDao.selectPublishedVersionsByArticleId(version.getArticleId());
            for (ArticleVersionDO pv : publishedVersions) {
                pv.setStatus(ArticleVersionStatusEnum.ROLLBACK.getCode());
                pv.setUpdateTime(new Date());
                articleVersionDao.updateById(pv);
            }

            // 设置当前版本为已发布
            version.setStatus(ArticleVersionStatusEnum.PUBLISHED.getCode());
            version.setPublishedAt(new Date());
            version.setUpdateTime(new Date());
            articleVersionDao.updateById(version);

            return true;
        });
    }

    @Override
    public Response rollbackVersion(RollbackVersionReqVO rollbackVersionReqVO) {
        Long targetVersionId = rollbackVersionReqVO.getTargetVersionId();
        ArticleVersionDO targetVersion = articleVersionDao.selectById(targetVersionId);

        if (targetVersion == null) {
            return Response.fail(ResponseCodeEnum.VERSION_NOT_FOUND);
        }

        if (!targetVersion.getStatus().equals(ArticleVersionStatusEnum.PUBLISHED.getCode())
                && !targetVersion.getStatus().equals(ArticleVersionStatusEnum.ROLLBACK.getCode())) {
            return Response.fail(ResponseCodeEnum.ROLLBACK_TARGET_NOT_PUBLISHED);
        }

        // 创建新版本（基于目标版本的快照）
        Integer maxVersionNum = articleVersionDao.selectMaxVersionNum(targetVersion.getArticleId());
        ArticleVersionDO newVersion = ArticleVersionDO.builder()
                .articleId(targetVersion.getArticleId())
                .versionNum(maxVersionNum + 1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title(targetVersion.getTitle())
                .titleImage(targetVersion.getTitleImage())
                .description(targetVersion.getDescription())
                .content(targetVersion.getContent())
                .categoryId(targetVersion.getCategoryId())
                .tagIds(targetVersion.getTagIds())
                .createTime(new Date())
                .updateTime(new Date())
                .isDeleted(false)
                .build();
        articleVersionDao.insert(newVersion);

        // 立即发布该新版本
        publishVersionToLive(newVersion);
        return Response.success();
    }

    @Override
    public Response queryArticleVersionList(QueryVersionListReqVO queryVersionListReqVO) {
        Long articleId = queryVersionListReqVO.getArticleId();
        List<ArticleVersionDO> versions = articleVersionDao.selectByArticleId(articleId);

        List<QueryVersionListRspVO> rspList = versions.stream().map(v ->
                QueryVersionListRspVO.builder()
                        .id(v.getId())
                        .articleId(v.getArticleId())
                        .versionNum(v.getVersionNum())
                        .status(v.getStatus())
                        .title(v.getTitle())
                        .description(v.getDescription())
                        .categoryId(v.getCategoryId())
                        .tagIds(v.getTagIds())
                        .scheduledAt(v.getScheduledAt())
                        .publishedAt(v.getPublishedAt())
                        .createTime(v.getCreateTime())
                        .build()
        ).collect(Collectors.toList());

        return Response.success(rspList);
    }

    @Override
    public Response queryVersionDiff(Long versionId1, Long versionId2) {
        ArticleVersionDO v1 = articleVersionDao.selectById(versionId1);
        ArticleVersionDO v2 = articleVersionDao.selectById(versionId2);

        if (v1 == null || v2 == null) {
            return Response.fail(ResponseCodeEnum.VERSION_NOT_FOUND);
        }

        VersionDiffRspVO diffRspVO = VersionDiffRspVO.builder()
                .oldTitle(v1.getTitle())
                .newTitle(v2.getTitle())
                .oldContent(v1.getContent())
                .newContent(v2.getContent())
                .oldDescription(v1.getDescription())
                .newDescription(v2.getDescription())
                .oldCategoryId(v1.getCategoryId())
                .newCategoryId(v2.getCategoryId())
                .oldTagIds(v1.getTagIds())
                .newTagIds(v2.getTagIds())
                .titleChanged(!Objects.equals(v1.getTitle(), v2.getTitle()))
                .contentChanged(!Objects.equals(v1.getContent(), v2.getContent()))
                .descriptionChanged(!Objects.equals(v1.getDescription(), v2.getDescription()))
                .categoryChanged(!Objects.equals(v1.getCategoryId(), v2.getCategoryId()))
                .tagsChanged(!Objects.equals(v1.getTagIds(), v2.getTagIds()))
                .build();

        return Response.success(diffRspVO);
    }

    /**
     * 处理标签相关业务
     * @param articleId
     * @param publishTags
     */
    public void handleTagBiz(Long articleId, List<String> publishTags) {
        List<TagDO> tagDOS = tagDao.selectAll();

        // 筛选出库中不存在的标签
        List<String> noExistTags = null;
        // 库中已存在的标签
        List<String> existTags = null;
        if (!CollectionUtils.isEmpty(tagDOS)) {
            List<String> tagIds = tagDOS.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.toList());
            noExistTags = publishTags.stream().filter(p -> !tagIds.contains(p)).collect(Collectors.toList());
            existTags = publishTags.stream().filter(p -> tagIds.contains(p)).collect(Collectors.toList());
        }

        // 不存在的标签先入库
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

    /**
     * 将标签列表（可能是ID或名称）解析为逗号分隔的ID字符串
     * 不写入 t_article_tag_rel，仅用于版本快照
     */
    public String resolveTagIds(List<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return "";
        }

        List<TagDO> allTags = tagDao.selectAll();
        List<String> existingTagIds = Collections.emptyList();
        if (!CollectionUtils.isEmpty(allTags)) {
            existingTagIds = allTags.stream().map(t -> String.valueOf(t.getId())).collect(Collectors.toList());
        }

        List<Long> resolvedIds = new ArrayList<>();
        for (String tag : tags) {
            if (existingTagIds.contains(tag)) {
                resolvedIds.add(Long.valueOf(tag));
            } else {
                // 新标签名称，先创建
                TagDO newTag = TagDO.builder()
                        .name(tag)
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build();
                tagDao.insert(newTag);
                resolvedIds.add(newTag.getId());
            }
        }

        return resolvedIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

}
