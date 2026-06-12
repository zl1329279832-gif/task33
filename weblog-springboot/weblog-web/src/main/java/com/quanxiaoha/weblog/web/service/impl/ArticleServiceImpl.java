package com.quanxiaoha.weblog.web.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.quanxiaoha.weblog.common.PageResponse;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.EventEnum;
import com.quanxiaoha.weblog.common.eventbus.ArticleEvent;
import com.quanxiaoha.weblog.common.exception.ResourceNotFoundException;
import com.quanxiaoha.weblog.web.convert.ArticleConvert;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.model.vo.article.*;
import com.quanxiaoha.weblog.web.model.vo.category.QueryCategoryListItemRspVO;
import com.quanxiaoha.weblog.web.model.vo.tag.QueryTagListItemRspVO;
import com.quanxiaoha.weblog.web.service.ArticleService;
import com.quanxiaoha.weblog.web.service.GrayResolutionContext;
import com.quanxiaoha.weblog.web.service.GrayResolutionResult;
import com.quanxiaoha.weblog.web.service.GrayResolutionService;
import com.quanxiaoha.weblog.web.utils.MarkdownUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-04-17 12:08
 * @description: TODO
 **/
@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private ArticleContentDao articleContentDao;
    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private ArticleCategoryRelDao articleCategoryRelDao;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private ArticleTagRelDao articleTagRelDao;
    @Autowired
    private EventBus eventBus;
    @Autowired
    private ArticleConvert articleConvert;
    @Autowired
    private GrayResolutionService grayResolutionService;
    @Autowired
    private ArticleVersionDao articleVersionDao;
    @Autowired
    private UserDao userDao;

    @Override
    public PageResponse queryIndexArticlePageList(QueryIndexArticlePageListReqVO queryIndexArticlePageListReqVO) {
        Long current = queryIndexArticlePageListReqVO.getCurrent();
        Long size = queryIndexArticlePageListReqVO.getSize();

        IPage<ArticleDO> articleDOIPage = articleDao.queryArticlePageList(current, size);
        List<ArticleDO> records = articleDOIPage.getRecords();

        List<QueryIndexArticlePageItemRspVO> list = null;
        if (!CollectionUtils.isEmpty(records)) {
            list = records.stream()
                    .map(articleDO -> articleConvert.convert(articleDO))
                    .collect(Collectors.toList());

            List<Long> articleIds = list.stream().map(p -> p.getId()).collect(Collectors.toList());

            // 设置分类信息
            List<CategoryDO> categoryDOS = categoryDao.selectAllCategory();
            Map<Long, String> categoryIdNameMap = categoryDOS.stream().collect(Collectors.toMap(CategoryDO::getId, CategoryDO::getName));

            List<ArticleCategoryRelDO> articleCategoryRelDOS = articleCategoryRelDao.selectByArticleIds(articleIds);
            list = list.stream().map(p -> {
                Long articleId = p.getId();
                Optional<ArticleCategoryRelDO> optional = articleCategoryRelDOS.stream().filter(rel -> Objects.equals(rel.getArticleId(), articleId)).findFirst();
                if (optional.isPresent()) {
                    ArticleCategoryRelDO articleCategoryRelDO = optional.get();
                    Long categoryId = articleCategoryRelDO.getCategoryId();
                    String categoryName = categoryIdNameMap.get(categoryId);

                    QueryCategoryListItemRspVO queryCategoryListItemRspVO = QueryCategoryListItemRspVO.builder()
                            .id(categoryId)
                            .name(categoryName)
                            .build();
                    p.setCategory(queryCategoryListItemRspVO);
                }
                return p;
            }).collect(Collectors.toList());

            // 设置标签信息
            List<TagDO> tagDOS = tagDao.selectAllTag();
            Map<Long, String> tagIdNameMap = tagDOS.stream().collect(Collectors.toMap(TagDO::getId, TagDO::getName));

            List<ArticleTagRelDO> articleTagRelDOS = articleTagRelDao.selectByArticleIds(articleIds);
            list = list.stream().map(p -> {
                Long articleId = p.getId();
                List<ArticleTagRelDO> articleTagRelDOList = articleTagRelDOS.stream().filter(rel -> Objects.equals(rel.getArticleId(), articleId)).collect(Collectors.toList());

                List<QueryTagListItemRspVO> queryTagListItemRspVOS = Lists.newArrayList();
                articleTagRelDOList.forEach(rel -> {
                    Long tagId = rel.getTagId();
                    String tagName = tagIdNameMap.get(tagId);

                    QueryTagListItemRspVO queryTagListItemRspVO = QueryTagListItemRspVO.builder()
                            .id(tagId)
                            .name(tagName)
                            .build();
                    queryTagListItemRspVOS.add(queryTagListItemRspVO);
                });

                p.setTags(queryTagListItemRspVOS);
                return p;
            }).collect(Collectors.toList());

            // 灰度覆盖
            applyGrayOverlay(list, buildGrayContext(queryIndexArticlePageListReqVO.getPreviewToken()));
        }
        return PageResponse.success(articleDOIPage, list);
    }

    @Override
    public PageResponse queryCategoryArticlePageList(QueryCategoryArticlePageListReqVO queryCategoryArticlePageListReqVO) {
        Long current = queryCategoryArticlePageListReqVO.getCurrent();
        Long size = queryCategoryArticlePageListReqVO.getSize();
        Long queryCategoryId = queryCategoryArticlePageListReqVO.getCategoryId();

        List<ArticleCategoryRelDO> articleCategoryRelDOList = articleCategoryRelDao.selectByCategoryId(queryCategoryId);

        // 判断该分类下是否存在文章
        if (CollectionUtils.isEmpty(articleCategoryRelDOList)) {
            return PageResponse.success(null, null);
        }

        List<Long> categoryArticleIds = articleCategoryRelDOList.stream().map(p -> p.getArticleId()).collect(Collectors.toList());

        IPage<ArticleDO> articleDOIPage = articleDao.queryArticlePageListByArticleIds(current, size, categoryArticleIds);
        List<ArticleDO> records = articleDOIPage.getRecords();

        List<QueryIndexArticlePageItemRspVO> list = null;
        if (!CollectionUtils.isEmpty(records)) {
            list = records.stream()
                    .map(articleDO -> articleConvert.convert(articleDO))
                    .collect(Collectors.toList());

            List<Long> articleIds = list.stream().map(p -> p.getId()).collect(Collectors.toList());

            // 设置分类信息
            List<CategoryDO> categoryDOS = categoryDao.selectAllCategory();
            Map<Long, String> categoryIdNameMap = categoryDOS.stream().collect(Collectors.toMap(CategoryDO::getId, CategoryDO::getName));

            List<ArticleCategoryRelDO> articleCategoryRelDOS = articleCategoryRelDao.selectByArticleIds(articleIds);
            list = list.stream().map(p -> {
                Long articleId = p.getId();
                Optional<ArticleCategoryRelDO> optional = articleCategoryRelDOS.stream().filter(rel -> Objects.equals(rel.getArticleId(), articleId)).findFirst();
                if (optional.isPresent()) {
                    ArticleCategoryRelDO articleCategoryRelDO = optional.get();
                    Long categoryId = articleCategoryRelDO.getCategoryId();
                    String categoryName = categoryIdNameMap.get(categoryId);

                    QueryCategoryListItemRspVO queryCategoryListItemRspVO = QueryCategoryListItemRspVO.builder()
                            .id(categoryId)
                            .name(categoryName)
                            .build();
                    p.setCategory(queryCategoryListItemRspVO);
                }
                return p;
            }).collect(Collectors.toList());

            // 设置标签信息
            List<TagDO> tagDOS = tagDao.selectAllTag();
            Map<Long, String> tagIdNameMap = tagDOS.stream().collect(Collectors.toMap(TagDO::getId, TagDO::getName));

            List<ArticleTagRelDO> articleTagRelDOS = articleTagRelDao.selectByArticleIds(articleIds);
            list = list.stream().map(p -> {
                Long articleId = p.getId();
                List<ArticleTagRelDO> articleTagRelDOList = articleTagRelDOS.stream().filter(rel -> Objects.equals(rel.getArticleId(), articleId)).collect(Collectors.toList());

                List<QueryTagListItemRspVO> queryTagListItemRspVOS = Lists.newArrayList();
                articleTagRelDOList.forEach(rel -> {
                    Long tagId = rel.getTagId();
                    String tagName = tagIdNameMap.get(tagId);

                    QueryTagListItemRspVO queryTagListItemRspVO = QueryTagListItemRspVO.builder()
                            .id(tagId)
                            .name(tagName)
                            .build();
                    queryTagListItemRspVOS.add(queryTagListItemRspVO);
                });

                p.setTags(queryTagListItemRspVOS);
                return p;
            }).collect(Collectors.toList());

            // 灰度覆盖
            applyGrayOverlay(list, buildGrayContext(queryCategoryArticlePageListReqVO.getPreviewToken()));
        }
        return PageResponse.success(articleDOIPage, list);
    }

    @Override
    public Response queryArticleDetail(QueryArticleDetailReqVO queryArticleDetailReqVO) {
        Long articleId = queryArticleDetailReqVO.getArticleId();

        // 灰度解析：检查是否命中灰度版本
        GrayResolutionContext grayCtx = buildGrayContext(queryArticleDetailReqVO.getPreviewToken());
        GrayResolutionResult grayResult = grayResolutionService.resolve(articleId, grayCtx);
        if (grayResult.isGrayHit()) {
            grayResolutionService.logExposure(grayResult, articleId, grayCtx.getUserId());
            // 仍然触发 PV 事件
            log.info("发送 PV +1 消息事件（灰度）");
            eventBus.post(ArticleEvent.builder().articleId(articleId).message(EventEnum.PV_INCREASE.getMessage()).build());
            return Response.success(buildDetailFromGrayVersion(grayResult.getGrayVersion(), articleId));
        }

        // 判断文章是否存在
        ArticleDO articleDO = articleDao.selectArticleById(articleId);

        if (Objects.isNull(articleDO)) {
            throw new ResourceNotFoundException();
        }

        ArticleContentDO articleContentDO = articleContentDao.selectArticleContentByArticleId(articleId);

        QueryArticleDetailRspVO vo = QueryArticleDetailRspVO.builder()
                .title(articleDO.getTitle())
                .updateTime(articleDO.getUpdateTime())
                .content(MarkdownUtil.parse2Html(articleContentDO.getContent()))
                .readNum(articleDO.getReadNum())
                .build();

        // 查询文章所属分类
        ArticleCategoryRelDO articleCategoryRelDO = articleCategoryRelDao.selectByArticleId(articleId);
        CategoryDO categoryDO = categoryDao.selectByCategoryId(articleCategoryRelDO.getCategoryId());
        vo.setCategoryId(categoryDO.getId());
        vo.setCategoryName(categoryDO.getName());

        // 查询文章标签
        List<ArticleTagRelDO> articleTagRelDOS = articleTagRelDao.selectByArticleId(articleId);
        List<Long> tagIds = articleTagRelDOS.stream().map(p -> p.getTagId()).collect(Collectors.toList());
        List<TagDO> tagDOS = tagDao.selectByTagIds(tagIds);

        List<QueryTagListItemRspVO> queryTagListItemRspVOS = tagDOS.stream()
                .map(p -> QueryTagListItemRspVO.builder().id(p.getId()).name(p.getName()).build()).collect(Collectors.toList());
        vo.setTags(queryTagListItemRspVOS);

        // 上一篇
        ArticleDO preArticle = articleDao.selectPreArticle(articleId);
        if (Objects.nonNull(preArticle)) {
            QueryArticleLinkRspVO prevArticleVO = QueryArticleLinkRspVO.builder()
                    .title(preArticle.getTitle())
                    .id(preArticle.getId())
                    .build();
            vo.setPreArticle(prevArticleVO);
        }

        // 下一篇
        ArticleDO nextArticle = articleDao.selectNextArticle(articleId);
        if (Objects.nonNull(nextArticle)) {
            QueryArticleLinkRspVO nextArticleVO = QueryArticleLinkRspVO.builder()
                    .title(nextArticle.getTitle())
                    .id(nextArticle.getId())
                    .build();
            vo.setNextArticle(nextArticleVO);
        }

        // 发送消息事件
        log.info("发送 PV +1 消息事件");
        eventBus.post(ArticleEvent.builder().articleId(articleId).message(EventEnum.PV_INCREASE.getMessage()).build());

        return Response.success(vo);
    }

    @Override
    public PageResponse queryTagArticlePageList(QueryTagArticlePageListReqVO queryTagArticlePageListReqVO) {
        Long current = queryTagArticlePageListReqVO.getCurrent();
        Long size = queryTagArticlePageListReqVO.getSize();
        Long queryTagId = queryTagArticlePageListReqVO.getTagId();

        List<ArticleTagRelDO> articleTagRelDOS1 = articleTagRelDao.selectByTagId(queryTagId);

        // 判断该分类下是否存在文章
        if (CollectionUtils.isEmpty(articleTagRelDOS1)) {
            return PageResponse.success(null, null);
        }

        List<Long> tagArticleIds = articleTagRelDOS1.stream().map(p -> p.getArticleId()).collect(Collectors.toList());

        IPage<ArticleDO> articleDOIPage = articleDao.queryArticlePageListByArticleIds(current, size, tagArticleIds);
        List<ArticleDO> records = articleDOIPage.getRecords();

        List<QueryIndexArticlePageItemRspVO> list = null;
        if (!CollectionUtils.isEmpty(records)) {
            list = records.stream()
                    .map(articleDO -> articleConvert.convert(articleDO))
                    .collect(Collectors.toList());

            List<Long> articleIds = list.stream().map(p -> p.getId()).collect(Collectors.toList());

            // 设置分类信息
            List<CategoryDO> categoryDOS = categoryDao.selectAllCategory();
            Map<Long, String> categoryIdNameMap = categoryDOS.stream().collect(Collectors.toMap(CategoryDO::getId, CategoryDO::getName));

            List<ArticleCategoryRelDO> articleCategoryRelDOS = articleCategoryRelDao.selectByArticleIds(articleIds);
            list = list.stream().map(p -> {
                Long articleId = p.getId();
                Optional<ArticleCategoryRelDO> optional = articleCategoryRelDOS.stream().filter(rel -> Objects.equals(rel.getArticleId(), articleId)).findFirst();
                if (optional.isPresent()) {
                    ArticleCategoryRelDO articleCategoryRelDO = optional.get();
                    Long categoryId = articleCategoryRelDO.getCategoryId();
                    String categoryName = categoryIdNameMap.get(categoryId);

                    QueryCategoryListItemRspVO queryCategoryListItemRspVO = QueryCategoryListItemRspVO.builder()
                            .id(categoryId)
                            .name(categoryName)
                            .build();
                    p.setCategory(queryCategoryListItemRspVO);
                }
                return p;
            }).collect(Collectors.toList());

            // 设置标签信息
            List<TagDO> tagDOS = tagDao.selectAllTag();
            Map<Long, String> tagIdNameMap = tagDOS.stream().collect(Collectors.toMap(TagDO::getId, TagDO::getName));

            List<ArticleTagRelDO> articleTagRelDOS = articleTagRelDao.selectByArticleIds(articleIds);
            list = list.stream().map(p -> {
                Long articleId = p.getId();
                List<ArticleTagRelDO> articleTagRelDOList = articleTagRelDOS.stream().filter(rel -> Objects.equals(rel.getArticleId(), articleId)).collect(Collectors.toList());

                List<QueryTagListItemRspVO> queryTagListItemRspVOS = Lists.newArrayList();
                articleTagRelDOList.forEach(rel -> {
                    Long tagId = rel.getTagId();
                    String tagName = tagIdNameMap.get(tagId);

                    QueryTagListItemRspVO queryTagListItemRspVO = QueryTagListItemRspVO.builder()
                            .id(tagId)
                            .name(tagName)
                            .build();
                    queryTagListItemRspVOS.add(queryTagListItemRspVO);
                });

                p.setTags(queryTagListItemRspVOS);
                return p;
            }).collect(Collectors.toList());

            // 灰度覆盖
            applyGrayOverlay(list, buildGrayContext(queryTagArticlePageListReqVO.getPreviewToken()));
        }
        return PageResponse.success(articleDOIPage, list);
    }

    // ==================== 灰度辅助方法 ====================

    private GrayResolutionContext buildGrayContext(String previewToken) {
        Long userId = null;
        String username = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            username = ((UserDetails) auth.getPrincipal()).getUsername();
            userId = userDao.selectUserIdByUsername(username);
        }
        return GrayResolutionContext.builder()
                .userId(userId)
                .username(username)
                .previewToken(previewToken)
                .build();
    }

    private QueryArticleDetailRspVO buildDetailFromGrayVersion(ArticleVersionDO version, Long articleId) {
        QueryArticleDetailRspVO vo = QueryArticleDetailRspVO.builder()
                .title(version.getTitle())
                .updateTime(version.getCreateTime())
                .content(MarkdownUtil.parse2Html(version.getContent()))
                .readNum(0L)
                .build();

        // 解析分类快照
        Long grayCategoryId = version.getCategoryId();
        if (grayCategoryId != null && grayCategoryId != 0L) {
            CategoryDO categoryDO = categoryDao.selectByCategoryId(grayCategoryId);
            if (categoryDO != null) {
                vo.setCategoryId(categoryDO.getId());
                vo.setCategoryName(categoryDO.getName());
            }
        }

        // 解析标签快照
        String tagSnapshot = version.getTagIds();
        if (tagSnapshot != null && !tagSnapshot.isEmpty()) {
            List<Long> tagIds = Arrays.stream(tagSnapshot.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            List<TagDO> tagDOS = tagDao.selectByTagIds(tagIds);
            List<QueryTagListItemRspVO> tagVOS = tagDOS.stream()
                    .map(t -> QueryTagListItemRspVO.builder().id(t.getId()).name(t.getName()).build())
                    .collect(Collectors.toList());
            vo.setTags(tagVOS);
        }

        // 上一篇 / 下一篇
        ArticleDO preArticle = articleDao.selectPreArticle(articleId);
        if (Objects.nonNull(preArticle)) {
            vo.setPreArticle(QueryArticleLinkRspVO.builder().title(preArticle.getTitle()).id(preArticle.getId()).build());
        }
        ArticleDO nextArticle = articleDao.selectNextArticle(articleId);
        if (Objects.nonNull(nextArticle)) {
            vo.setNextArticle(QueryArticleLinkRspVO.builder().title(nextArticle.getTitle()).id(nextArticle.getId()).build());
        }

        // 读取当前文章的阅读量
        ArticleDO articleDO = articleDao.selectArticleById(articleId);
        if (articleDO != null) {
            vo.setReadNum(articleDO.getReadNum());
        }

        return vo;
    }

    private void applyGrayOverlay(List<QueryIndexArticlePageItemRspVO> items, GrayResolutionContext ctx) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        for (QueryIndexArticlePageItemRspVO item : items) {
            GrayResolutionResult result = grayResolutionService.resolve(item.getId(), ctx);
            if (result.isGrayHit()) {
                ArticleVersionDO grayVersion = result.getGrayVersion();
                item.setTitle(grayVersion.getTitle());
                item.setTitleImage(grayVersion.getTitleImage());

                // 覆盖分类
                Long grayCategoryId = grayVersion.getCategoryId();
                if (grayCategoryId != null && grayCategoryId != 0L) {
                    CategoryDO categoryDO = categoryDao.selectByCategoryId(grayCategoryId);
                    if (categoryDO != null) {
                        item.setCategory(QueryCategoryListItemRspVO.builder()
                                .id(categoryDO.getId()).name(categoryDO.getName()).build());
                    }
                }

                // 覆盖标签
                String tagSnapshot = grayVersion.getTagIds();
                if (tagSnapshot != null && !tagSnapshot.isEmpty()) {
                    List<Long> tagIds = Arrays.stream(tagSnapshot.split(","))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    List<TagDO> tagDOS = tagDao.selectByTagIds(tagIds);
                    List<QueryTagListItemRspVO> tagVOS = tagDOS.stream()
                            .map(t -> QueryTagListItemRspVO.builder().id(t.getId()).name(t.getName()).build())
                            .collect(Collectors.toList());
                    item.setTags(tagVOS);
                }

                grayResolutionService.logExposure(result, item.getId(), ctx.getUserId());
            }
        }
    }
}
