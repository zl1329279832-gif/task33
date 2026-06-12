package com.quanxiaoha.weblog.web.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.eventbus.EventBus;
import com.quanxiaoha.weblog.common.PageResponse;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.web.convert.ArticleConvert;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.model.vo.article.*;
import com.quanxiaoha.weblog.web.service.impl.ArticleServiceImpl;
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
 * 灰度多入口一致性测试
 * 验证: 文章详情/首页列表/分类列表/标签列表/归档 全部遵循灰度解析
 */
@ExtendWith(MockitoExtension.class)
class GrayMultiEntryConsistencyTest {

    private ArticleServiceImpl articleService;

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao articleContentDao;
    @Mock private CategoryDao categoryDao;
    @Mock private ArticleCategoryRelDao articleCategoryRelDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagRelDao articleTagRelDao;
    @Mock private EventBus eventBus;
    @Mock private ArticleConvert articleConvert;
    @Mock private GrayResolutionService grayResolutionService;
    @Mock private ArticleVersionDao articleVersionDao;
    @Mock private UserDao userDao;

    private ArticleVersionDO grayVersion;

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
        ReflectionTestUtils.setField(articleService, "grayResolutionService", grayResolutionService);
        ReflectionTestUtils.setField(articleService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(articleService, "userDao", userDao);

        grayVersion = ArticleVersionDO.builder()
                .id(100L).articleId(1L).versionNum(2)
                .title("灰度新标题").content("# 灰度内容")
                .titleImage("gray_cover.jpg")
                .categoryId(1L)
                .tagIds("1,2")
                .createTime(new Date())
                .build();
    }

    // ==================== 文章详情 ====================

    @Test
    @DisplayName("文章详情 - 灰度命中时返回灰度版本内容")
    void queryArticleDetail_GrayHit_ReturnsGrayContent() {
        GrayResolutionResult grayResult = GrayResolutionResult.builder()
                .grayHit(true).grayVersion(grayVersion).matchedRule("TAG:vip").build();
        when(grayResolutionService.resolve(eq(1L), any(GrayResolutionContext.class)))
                .thenReturn(grayResult);

        // 灰度需要查的数据
        CategoryDO cat = CategoryDO.builder().id(1L).name("Java").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(cat);
        TagDO tag1 = TagDO.builder().id(1L).name("Spring").build();
        TagDO tag2 = TagDO.builder().id(2L).name("Boot").build();
        when(tagDao.selectByTagIds(any())).thenReturn(Arrays.asList(tag1, tag2));
        when(articleDao.selectPreArticle(1L)).thenReturn(null);
        when(articleDao.selectNextArticle(1L)).thenReturn(null);
        ArticleDO articleDO = ArticleDO.builder().id(1L).readNum(50L).build();
        when(articleDao.selectArticleById(1L)).thenReturn(articleDO);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder()
                .articleId(1L).previewToken(null).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        QueryArticleDetailRspVO detail = (QueryArticleDetailRspVO) response.getData();
        assertEquals("灰度新标题", detail.getTitle());

        // PV 事件仍然触发
        verify(eventBus).post(any());
        // 曝光日志记录
        verify(grayResolutionService).logExposure(eq(grayResult), eq(1L), isNull());
    }

    @Test
    @DisplayName("文章详情 - 灰度未命中时返回线上内容")
    void queryArticleDetail_GrayMiss_ReturnsLiveContent() {
        when(grayResolutionService.resolve(eq(1L), any(GrayResolutionContext.class)))
                .thenReturn(GrayResolutionResult.miss());

        ArticleDO articleDO = ArticleDO.builder()
                .id(1L).title("线上标题").readNum(100L).updateTime(new Date()).build();
        when(articleDao.selectArticleById(1L)).thenReturn(articleDO);

        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(1L).content("# 线上内容").build();
        when(articleContentDao.selectArticleContentByArticleId(1L)).thenReturn(contentDO);

        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(1L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByArticleId(1L)).thenReturn(catRel);
        CategoryDO cat = CategoryDO.builder().id(1L).name("Java").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(cat);
        when(articleTagRelDao.selectByArticleId(1L)).thenReturn(Collections.emptyList());
        when(articleDao.selectPreArticle(1L)).thenReturn(null);
        when(articleDao.selectNextArticle(1L)).thenReturn(null);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder()
                .articleId(1L).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        QueryArticleDetailRspVO detail = (QueryArticleDetailRspVO) response.getData();
        assertEquals("线上标题", detail.getTitle());
    }

    // ==================== 首页列表 ====================

    @Test
    @DisplayName("首页列表 - 灰度命中时覆盖列表项")
    void queryIndexArticlePageList_GrayHit_OverlaysItem() {
        ArticleDO article = ArticleDO.builder()
                .id(1L).title("线上标题").createTime(new Date()).build();
        Page<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(article));
        when(articleDao.queryArticlePageList(1L, 10L)).thenReturn(page);

        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(1L).title("线上标题").build();
        when(articleConvert.convert(any(ArticleDO.class))).thenReturn(itemVO);
        when(categoryDao.selectAllCategory()).thenReturn(Collections.emptyList());
        when(articleCategoryRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());
        when(tagDao.selectAllTag()).thenReturn(Collections.emptyList());
        when(articleTagRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());

        // 灰度命中
        GrayResolutionResult grayResult = GrayResolutionResult.builder()
                .grayHit(true).grayVersion(grayVersion).matchedRule("TAG:vip").build();
        when(grayResolutionService.resolve(eq(1L), any(GrayResolutionContext.class)))
                .thenReturn(grayResult);
        CategoryDO cat = CategoryDO.builder().id(1L).name("Java").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(cat);
        TagDO tag1 = TagDO.builder().id(1L).name("Spring").build();
        TagDO tag2 = TagDO.builder().id(2L).name("Boot").build();
        when(tagDao.selectByTagIds(any())).thenReturn(Arrays.asList(tag1, tag2));

        QueryIndexArticlePageListReqVO req = QueryIndexArticlePageListReqVO.builder()
                .current(1L).size(10L).build();
        PageResponse response = articleService.queryIndexArticlePageList(req);

        assertNotNull(response);
        verify(grayResolutionService).resolve(eq(1L), any(GrayResolutionContext.class));
        verify(grayResolutionService).logExposure(eq(grayResult), eq(1L), isNull());
    }

    // ==================== 分类列表 ====================

    @Test
    @DisplayName("分类列表 - 灰度解析被调用")
    void queryCategoryArticlePageList_GrayResolutionInvoked() {
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(1L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByCategoryId(1L)).thenReturn(Arrays.asList(catRel));

        ArticleDO article = ArticleDO.builder().id(1L).title("文章").build();
        Page<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(article));
        when(articleDao.queryArticlePageListByArticleIds(eq(1L), eq(10L), any())).thenReturn(page);

        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(1L).title("文章").build();
        when(articleConvert.convert(any())).thenReturn(itemVO);
        when(categoryDao.selectAllCategory()).thenReturn(Collections.emptyList());
        when(articleCategoryRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());
        when(tagDao.selectAllTag()).thenReturn(Collections.emptyList());
        when(articleTagRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());

        // 灰度未命中
        when(grayResolutionService.resolve(eq(1L), any(GrayResolutionContext.class)))
                .thenReturn(GrayResolutionResult.miss());

        QueryCategoryArticlePageListReqVO req = QueryCategoryArticlePageListReqVO.builder()
                .current(1L).size(10L).categoryId(1L).build();
        PageResponse response = articleService.queryCategoryArticlePageList(req);

        assertNotNull(response);
        // 验证灰度解析被调用
        verify(grayResolutionService).resolve(eq(1L), any(GrayResolutionContext.class));
    }

    // ==================== 标签列表 ====================

    @Test
    @DisplayName("标签列表 - 灰度解析被调用")
    void queryTagArticlePageList_GrayResolutionInvoked() {
        ArticleTagRelDO tagRel = ArticleTagRelDO.builder()
                .articleId(1L).tagId(1L).build();
        when(articleTagRelDao.selectByTagId(1L)).thenReturn(Arrays.asList(tagRel));

        ArticleDO article = ArticleDO.builder().id(1L).title("文章").build();
        Page<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(article));
        when(articleDao.queryArticlePageListByArticleIds(eq(1L), eq(10L), any())).thenReturn(page);

        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(1L).title("文章").build();
        when(articleConvert.convert(any())).thenReturn(itemVO);
        when(categoryDao.selectAllCategory()).thenReturn(Collections.emptyList());
        when(articleCategoryRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());
        when(tagDao.selectAllTag()).thenReturn(Collections.emptyList());
        when(articleTagRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());

        // 灰度未命中
        when(grayResolutionService.resolve(eq(1L), any(GrayResolutionContext.class)))
                .thenReturn(GrayResolutionResult.miss());

        QueryTagArticlePageListReqVO req = QueryTagArticlePageListReqVO.builder()
                .current(1L).size(10L).tagId(1L).build();
        PageResponse response = articleService.queryTagArticlePageList(req);

        assertNotNull(response);
        verify(grayResolutionService).resolve(eq(1L), any(GrayResolutionContext.class));
    }

    // ==================== 空数据场景 ====================

    @Test
    @DisplayName("首页列表 - 空数据不触发灰度解析")
    void queryIndexArticlePageList_EmptyRecords_NoGrayResolution() {
        Page<ArticleDO> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(articleDao.queryArticlePageList(1L, 10L)).thenReturn(emptyPage);

        QueryIndexArticlePageListReqVO req = QueryIndexArticlePageListReqVO.builder()
                .current(1L).size(10L).build();
        PageResponse response = articleService.queryIndexArticlePageList(req);

        assertNotNull(response);
        verify(grayResolutionService, never()).resolve(anyLong(), any());
    }

    @Test
    @DisplayName("分类列表 - 分类下无文章不触发灰度解析")
    void queryCategoryArticlePageList_NoArticles_NoGrayResolution() {
        when(articleCategoryRelDao.selectByCategoryId(999L)).thenReturn(Collections.emptyList());

        QueryCategoryArticlePageListReqVO req = QueryCategoryArticlePageListReqVO.builder()
                .current(1L).size(10L).categoryId(999L).build();
        PageResponse response = articleService.queryCategoryArticlePageList(req);

        assertNotNull(response);
        verify(grayResolutionService, never()).resolve(anyLong(), any());
    }

    // ==================== 一致性验证 ====================

    @Test
    @DisplayName("所有列表入口对同一文章的灰度解析结果一致")
    void allListEndpoints_SameArticle_ConsistentGrayResolution() {
        // 准备公共数据
        ArticleDO article = ArticleDO.builder().id(1L).title("线上标题").createTime(new Date()).build();
        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(1L).title("线上标题").build();

        GrayResolutionResult grayResult = GrayResolutionResult.builder()
                .grayHit(true).grayVersion(grayVersion).matchedRule("PERCENTAGE:50").build();
        when(grayResolutionService.resolve(eq(1L), any(GrayResolutionContext.class)))
                .thenReturn(grayResult);

        CategoryDO cat = CategoryDO.builder().id(1L).name("Java").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(cat);
        TagDO tag1 = TagDO.builder().id(1L).name("Spring").build();
        TagDO tag2 = TagDO.builder().id(2L).name("Boot").build();
        when(tagDao.selectByTagIds(any())).thenReturn(Arrays.asList(tag1, tag2));

        // --- 首页列表 ---
        Page<ArticleDO> indexPage = new Page<>(1, 10);
        indexPage.setRecords(Arrays.asList(article));
        when(articleDao.queryArticlePageList(1L, 10L)).thenReturn(indexPage);
        when(articleConvert.convert(any(ArticleDO.class))).thenReturn(
                QueryIndexArticlePageItemRspVO.builder().id(1L).title("线上标题").build());
        when(categoryDao.selectAllCategory()).thenReturn(Collections.emptyList());
        when(articleCategoryRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());
        when(tagDao.selectAllTag()).thenReturn(Collections.emptyList());
        when(articleTagRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());

        articleService.queryIndexArticlePageList(
                QueryIndexArticlePageListReqVO.builder().current(1L).size(10L).build());

        // --- 分类列表 ---
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(1L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByCategoryId(1L)).thenReturn(Arrays.asList(catRel));
        Page<ArticleDO> catPage = new Page<>(1, 10);
        catPage.setRecords(Arrays.asList(article));
        when(articleDao.queryArticlePageListByArticleIds(eq(1L), eq(10L), any())).thenReturn(catPage);
        when(articleConvert.convert(any(ArticleDO.class))).thenReturn(
                QueryIndexArticlePageItemRspVO.builder().id(1L).title("线上标题").build());

        articleService.queryCategoryArticlePageList(
                QueryCategoryArticlePageListReqVO.builder().current(1L).size(10L).categoryId(1L).build());

        // --- 标签列表 ---
        ArticleTagRelDO tagRel = ArticleTagRelDO.builder().articleId(1L).tagId(1L).build();
        when(articleTagRelDao.selectByTagId(1L)).thenReturn(Arrays.asList(tagRel));
        Page<ArticleDO> tagPage = new Page<>(1, 10);
        tagPage.setRecords(Arrays.asList(article));
        when(articleConvert.convert(any(ArticleDO.class))).thenReturn(
                QueryIndexArticlePageItemRspVO.builder().id(1L).title("线上标题").build());

        articleService.queryTagArticlePageList(
                QueryTagArticlePageListReqVO.builder().current(1L).size(10L).tagId(1L).build());

        // 验证: 灰度解析在 3 个入口各被调用 1 次，共 3 次
        verify(grayResolutionService, times(3)).resolve(eq(1L), any(GrayResolutionContext.class));
    }
}
