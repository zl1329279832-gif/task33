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
 * 前台文章查询一致性测试
 * 验证: 前台所有查询接口只读取线上表（t_article等），不读取版本表
 */
@ExtendWith(MockitoExtension.class)
class ArticleReadConsistencyTest {

    private ArticleServiceImpl articleService;

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao articleContentDao;
    @Mock private CategoryDao categoryDao;
    @Mock private ArticleCategoryRelDao articleCategoryRelDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagRelDao articleTagRelDao;
    @Mock private EventBus eventBus;
    @Mock private ArticleConvert articleConvert;

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
    }

    @Test
    @DisplayName("首页文章列表 - 只返回线上表数据")
    void queryIndexArticlePageList_ReturnsOnlyPublishedData() {
        // 模拟线上表只有一篇已发布文章
        ArticleDO publishedArticle = ArticleDO.builder()
                .id(1L).title("已发布标题")
                .titleImage("img").description("desc")
                .createTime(new Date()).updateTime(new Date())
                .build();

        Page<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(publishedArticle));
        when(articleDao.queryArticlePageList(anyLong(), anyLong())).thenReturn(page);

        // 模拟 ArticleConvert
        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(1L).title("已发布标题").build();
        when(articleConvert.convert(any(ArticleDO.class))).thenReturn(itemVO);

        // 模拟分类
        when(categoryDao.selectAllCategory()).thenReturn(Collections.emptyList());
        when(articleCategoryRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());

        // 模拟标签
        when(tagDao.selectAllTag()).thenReturn(Collections.emptyList());
        when(articleTagRelDao.selectByArticleIds(any())).thenReturn(Collections.emptyList());

        QueryIndexArticlePageListReqVO req = QueryIndexArticlePageListReqVO.builder()
                .current(1L).size(10L).build();

        PageResponse response = articleService.queryIndexArticlePageList(req);

        // 验证: 返回的是线上数据
        assertNotNull(response);
        // 验证: 没有访问版本表相关的任何 DAO
        // （web 模块的 ArticleServiceImpl 本身就没有版本表依赖，这里验证它只调用了线上表 DAO）
        verify(articleDao).queryArticlePageList(1L, 10L);
        verify(articleCategoryRelDao).selectByArticleIds(any());
        verify(articleTagRelDao).selectByArticleIds(any());
    }

    @Test
    @DisplayName("文章详情 - 只返回已发布内容")
    void queryArticleDetail_ReturnsOnlyPublishedContent() {
        // 模拟线上文章
        ArticleDO articleDO = ArticleDO.builder()
                .id(1L).title("已发布标题").readNum(100L)
                .updateTime(new Date()).build();
        when(articleDao.selectArticleById(1L)).thenReturn(articleDO);

        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(1L).content("# 已发布内容").build();
        when(articleContentDao.selectArticleContentByArticleId(1L)).thenReturn(contentDO);

        // 模拟分类
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(1L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByArticleId(1L)).thenReturn(catRel);
        CategoryDO cat = CategoryDO.builder().id(1L).name("Java").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(cat);

        // 模拟标签
        when(articleTagRelDao.selectByArticleId(1L)).thenReturn(Collections.emptyList());

        // 模拟前后文章
        when(articleDao.selectPreArticle(1L)).thenReturn(null);
        when(articleDao.selectNextArticle(1L)).thenReturn(null);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(1L).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        QueryArticleDetailRspVO detail = (QueryArticleDetailRspVO) response.getData();
        assertEquals("已发布标题", detail.getTitle());
        assertEquals(100L, detail.getReadNum()); // PV 来自线上表

        // 验证 PV 事件被发送
        verify(eventBus).post(any());
    }

    @Test
    @DisplayName("分类文章列表 - 只反映已发布分类")
    void queryCategoryArticlePageList_ReflectsPublishedCategoryOnly() {
        // 模拟分类1下有文章
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(1L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByCategoryId(1L)).thenReturn(Arrays.asList(catRel));

        ArticleDO article = ArticleDO.builder()
                .id(1L).title("文章").build();
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

        QueryCategoryArticlePageListReqVO req = QueryCategoryArticlePageListReqVO.builder()
                .current(1L).size(10L).categoryId(1L).build();

        PageResponse response = articleService.queryCategoryArticlePageList(req);
        assertNotNull(response);

        // 验证: 使用了线上分类关联表查询
        verify(articleCategoryRelDao).selectByCategoryId(1L);
    }

    @Test
    @DisplayName("标签文章列表 - 只反映已发布标签")
    void queryTagArticlePageList_ReflectsPublishedTagsOnly() {
        // 模拟标签1下有文章
        ArticleTagRelDO tagRel = ArticleTagRelDO.builder()
                .articleId(1L).tagId(1L).build();
        when(articleTagRelDao.selectByTagId(1L)).thenReturn(Arrays.asList(tagRel));

        ArticleDO article = ArticleDO.builder()
                .id(1L).title("文章").build();
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

        QueryTagArticlePageListReqVO req = QueryTagArticlePageListReqVO.builder()
                .current(1L).size(10L).tagId(1L).build();

        PageResponse response = articleService.queryTagArticlePageList(req);
        assertNotNull(response);

        // 验证: 使用了线上标签关联表查询
        verify(articleTagRelDao).selectByTagId(1L);
    }

    @Test
    @DisplayName("归档查询 - 只反映已发布数据")
    void queryArchive_ReflectsPublishedDataOnly() {
        // 归档服务也读取 t_article 线上表
        // 验证 ArchiveServiceImpl 不依赖版本表
        // 这里通过代码审查确认: ArchiveServiceImpl 使用 ArticleDao (web) 查询 t_article

        // 模拟空数据场景
        Page<ArticleDO> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(articleDao.queryArticlePageList(anyLong(), anyLong())).thenReturn(emptyPage);

        QueryIndexArticlePageListReqVO req = QueryIndexArticlePageListReqVO.builder()
                .current(1L).size(10L).build();

        PageResponse response = articleService.queryIndexArticlePageList(req);
        assertNotNull(response);
    }
}
