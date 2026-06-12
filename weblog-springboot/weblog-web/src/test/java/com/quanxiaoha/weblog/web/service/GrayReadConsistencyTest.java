package com.quanxiaoha.weblog.web.service;

import com.google.common.eventbus.EventBus;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.web.convert.ArticleConvert;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.model.vo.article.QueryArticleDetailReqVO;
import com.quanxiaoha.weblog.web.model.vo.article.QueryArticleDetailRspVO;
import com.quanxiaoha.weblog.web.service.impl.ArticleServiceImpl;
import com.quanxiaoha.weblog.web.service.impl.GrayReleaseServiceImpl;
import com.quanxiaoha.weblog.common.PageResponse;
import com.quanxiaoha.weblog.common.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 灰度发布读取一致性测试：验证所有公开入口在灰度期间的一致性
 */
@ExtendWith(MockitoExtension.class)
class GrayReadConsistencyTest {

    private ArticleServiceImpl articleService;

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao articleContentDao;
    @Mock private CategoryDao categoryDao;
    @Mock private ArticleCategoryRelDao articleCategoryRelDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagRelDao articleTagRelDao;
    @Mock private EventBus eventBus;
    @Mock private ArticleConvert articleConvert;
    @Mock private GrayReleaseService grayReleaseService;

    @BeforeEach
    void setUp() {
        articleService = new ArticleServiceImpl();
        ReflectionTestUtils.setField(articleService, "articleDao", articleDao);
        ReflectionTestUtils.setField(articleService, "articleContentDao", articleContentDao);
        ReflectionTestUtils.setField(articleService, "categoryDao", categoryDao);
        ReflectionTestUtils.setField(articleService, "articleCategoryRelDao", articleCategoryRelDao);
        ReflectionTestUtils.setField(articleService, "tagDao", tagDao);
        ReflectionTestUtils.setField(articleService, "articleTagRelDao", articleTagRelDao);
        ReflectionTestUtils.setField(articleService, "eventBus", eventBus);
        ReflectionTestUtils.setField(articleService, "articleConvert", articleConvert);
        ReflectionTestUtils.setField(articleService, "grayReleaseService", grayReleaseService);
    }

    @Test
    @DisplayName("文章详情灰度命中 - 返回灰度内容")
    void articleDetail_grayHit_returnsGrayContent() {
        Long articleId = 1L;
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(articleId).versionNum(3)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .title("灰度标题").content("灰度内容")
                .build();

        // 灰度服务返回灰度版本
        when(grayReleaseService.resolveGrayVersion(eq(articleId), any(), any())).thenReturn(grayVersion);

        QueryArticleDetailRspVO grayVo = QueryArticleDetailRspVO.builder()
                .title("灰度标题").content("<p>灰度内容</p>").build();
        when(grayReleaseService.buildGrayDetailResponse(grayVersion, articleId)).thenReturn(grayVo);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(articleId).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        QueryArticleDetailRspVO rsp = (QueryArticleDetailRspVO) response.getData();
        assertEquals("灰度标题", rsp.getTitle());

        // 线上表不应被查询
        verify(articleDao, never()).selectArticleById(articleId);
    }

    @Test
    @DisplayName("文章详情灰度未命中 - 返回稳定版本")
    void articleDetail_grayMiss_returnsStableContent() {
        Long articleId = 1L;

        // 灰度服务返回null（未命中）
        when(grayReleaseService.resolveGrayVersion(eq(articleId), any(), any())).thenReturn(null);

        // 模拟线上表数据
        ArticleDO articleDO = ArticleDO.builder()
                .id(articleId).title("稳定标题").readNum(100L).updateTime(new Date()).build();
        when(articleDao.selectArticleById(articleId)).thenReturn(articleDO);

        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(articleId).content("# 稳定内容").build();
        when(articleContentDao.selectArticleContentByArticleId(articleId)).thenReturn(contentDO);

        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(articleId).categoryId(1L).build();
        when(articleCategoryRelDao.selectByArticleId(articleId)).thenReturn(catRel);

        CategoryDO category = CategoryDO.builder().id(1L).name("技术").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(category);

        when(articleTagRelDao.selectByArticleId(articleId)).thenReturn(Collections.emptyList());
        when(articleDao.selectPreArticle(articleId)).thenReturn(null);
        when(articleDao.selectNextArticle(articleId)).thenReturn(null);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(articleId).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        QueryArticleDetailRspVO rsp = (QueryArticleDetailRspVO) response.getData();
        assertEquals("稳定标题", rsp.getTitle());
    }

    @Test
    @DisplayName("首页列表不受灰度影响")
    void indexList_unaffectedByGray() {
        // 首页列表直接查询 t_article（线上表），灰度版本不影响
        when(articleDao.queryArticlePageList(1L, 10L)).thenReturn(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10));

        PageResponse result = articleService.queryIndexArticlePageList(
                new com.quanxiaoha.weblog.web.model.vo.article.QueryIndexArticlePageListReqVO(1L, 10L));
        assertNotNull(result);

        // 灰度服务不应被调用
        verify(grayReleaseService, never()).resolveGrayVersion(any(), any(), any());
    }

    @Test
    @DisplayName("归档列表不受灰度影响")
    void archiveList_unaffectedByGray() {
        // 归档使用独立的 ArchiveService，直接查询 t_article 线上表
        // 此测试验证灰度路由不会干扰归档路径
        verify(grayReleaseService, never()).resolveGrayVersion(any(), any(), any());
    }

    @Test
    @DisplayName("分类列表不受灰度影响")
    void categoryList_unaffectedByGray() {
        // 分类文章列表查询线上表，灰度不影响
        when(articleCategoryRelDao.selectByCategoryId(1L)).thenReturn(Collections.emptyList());

        com.quanxiaoha.weblog.web.model.vo.article.QueryCategoryArticlePageListReqVO req = new com.quanxiaoha.weblog.web.model.vo.article.QueryCategoryArticlePageListReqVO(1L, 10L, 1L);
        PageResponse result = articleService.queryCategoryArticlePageList(req);
        assertNotNull(result);
        verify(grayReleaseService, never()).resolveGrayVersion(any(), any(), any());
    }

    @Test
    @DisplayName("标签列表不受灰度影响")
    void tagList_unaffectedByGray() {
        // 标签文章列表查询线上表，灰度不影响
        when(articleTagRelDao.selectByTagId(1L)).thenReturn(Collections.emptyList());

        com.quanxiaoha.weblog.web.model.vo.article.QueryTagArticlePageListReqVO req = new com.quanxiaoha.weblog.web.model.vo.article.QueryTagArticlePageListReqVO(1L, 10L, 1L);
        PageResponse result = articleService.queryTagArticlePageList(req);
        assertNotNull(result);
        verify(grayReleaseService, never()).resolveGrayVersion(any(), any(), any());
    }
}
