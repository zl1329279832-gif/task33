package com.quanxiaoha.weblog.web.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.eventbus.EventBus;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.web.convert.ArticleConvert;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.model.vo.archive.QueryArchiveItemRspVO;
import com.quanxiaoha.weblog.web.model.vo.article.*;
import com.quanxiaoha.weblog.web.service.impl.ArchiveServiceImpl;
import com.quanxiaoha.weblog.web.service.impl.ArticleServiceImpl;
import com.quanxiaoha.weblog.web.model.vo.archive.QueryArchivePageListReqVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArticleReadConsistencyTest {

    @Mock
    private ArticleDao articleDao;
    @Mock
    private ArticleContentDao articleContentDao;
    @Mock
    private CategoryDao categoryDao;
    @Mock
    private ArticleCategoryRelDao articleCategoryRelDao;
    @Mock
    private TagDao tagDao;
    @Mock
    private ArticleTagRelDao articleTagRelDao;
    @Mock
    private EventBus eventBus;
    @Mock
    private ArticleConvert articleConvert;

    @InjectMocks
    private ArticleServiceImpl articleService;

    @Test
    void queryArticleDetail_ReturnsOnlyPublishedContent() {
        // The web ArticleServiceImpl reads from live tables only
        ArticleDO articleDO = ArticleDO.builder()
                .id(100L).title("Published Title").titleImage("pub.jpg")
                .description("Published desc").readNum(50L)
                .updateTime(new Date()).build();
        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(100L).content("Published Content").build();
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(100L).categoryId(1L).build();
        CategoryDO categoryDO = CategoryDO.builder().id(1L).name("Java").build();
        List<ArticleTagRelDO> tagRels = Arrays.asList(
                ArticleTagRelDO.builder().articleId(100L).tagId(1L).build()
        );
        List<TagDO> tags = Arrays.asList(TagDO.builder().id(1L).name("Spring").build());

        when(articleDao.selectArticleById(100L)).thenReturn(articleDO);
        when(articleContentDao.selectArticleContentByArticleId(100L)).thenReturn(contentDO);
        when(articleCategoryRelDao.selectByArticleId(100L)).thenReturn(catRel);
        when(categoryDao.selectByCategoryId(1L)).thenReturn(categoryDO);
        when(articleTagRelDao.selectByArticleId(100L)).thenReturn(tagRels);
        when(tagDao.selectByTagIds(anyList())).thenReturn(tags);
        when(articleDao.selectPreArticle(100L)).thenReturn(null);
        when(articleDao.selectNextArticle(100L)).thenReturn(null);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(100L).build();
        com.quanxiaoha.weblog.common.Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        QueryArticleDetailRspVO rsp = (QueryArticleDetailRspVO) response.getData();
        // Data comes from live tables, not version table
        assertEquals("Published Title", rsp.getTitle());
        assertEquals(50L, rsp.getReadNum());
    }

    @Test
    void queryIndexArticlePageList_ReturnsOnlyPublishedData() {
        ArticleDO articleDO = ArticleDO.builder()
                .id(100L).title("Published Title").titleImage("pub.jpg")
                .description("Published desc").createTime(new Date()).build();

        IPage<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(articleDO));
        page.setTotal(1);

        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(100L).title("Published Title").build();

        when(articleDao.queryArticlePageList(1L, 10L)).thenReturn(page);
        when(articleConvert.convert(articleDO)).thenReturn(itemVO);
        when(categoryDao.selectAllCategory()).thenReturn(
                Arrays.asList(CategoryDO.builder().id(1L).name("Java").build()));
        when(articleCategoryRelDao.selectByArticleIds(anyList())).thenReturn(
                Arrays.asList(ArticleCategoryRelDO.builder().articleId(100L).categoryId(1L).build()));
        when(tagDao.selectAllTag()).thenReturn(
                Arrays.asList(TagDO.builder().id(1L).name("Spring").build()));
        when(articleTagRelDao.selectByArticleIds(anyList())).thenReturn(
                Arrays.asList(ArticleTagRelDO.builder().articleId(100L).tagId(1L).build()));

        QueryIndexArticlePageListReqVO req = QueryIndexArticlePageListReqVO.builder()
                .current(1L).size(10L).build();
        com.quanxiaoha.weblog.common.PageResponse response = articleService.queryIndexArticlePageList(req);

        assertTrue(response.isSuccess());
        // All data sourced from live tables -- no version table involved
        List<?> data = (List<?>) response.getData();
        assertEquals(1, data.size());
    }

    @Test
    void queryArchive_ReflectsPublishedDataOnly() {
        // ArchiveServiceImpl uses its own articleDao -- test via reflection
        ArchiveServiceImpl archiveService = new ArchiveServiceImpl();

        ArticleDO articleDO = ArticleDO.builder()
                .id(100L).title("Published Title").createTime(new Date()).build();

        IPage<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(articleDO));
        page.setTotal(1);

        ArticleDao archiveArticleDao = mock(ArticleDao.class);
        ArticleConvert archiveConvert = mock(ArticleConvert.class);

        try {
            java.lang.reflect.Field daoField = ArchiveServiceImpl.class.getDeclaredField("articleDao");
            daoField.setAccessible(true);
            daoField.set(archiveService, archiveArticleDao);

            java.lang.reflect.Field convertField = ArchiveServiceImpl.class.getDeclaredField("articleConvert");
            convertField.setAccessible(true);
            convertField.set(archiveService, archiveConvert);
        } catch (Exception e) {
            fail("Failed to inject mocks: " + e.getMessage());
        }

        when(archiveArticleDao.queryArticlePageList(1L, 10L)).thenReturn(page);
        when(archiveConvert.convert2Archive(articleDO)).thenReturn(
                QueryArchiveItemRspVO.builder().id(100L).title("Published Title").createMonth("2024-01").build());

        QueryArchivePageListReqVO req = QueryArchivePageListReqVO.builder()
                .current(1L).size(10L).build();
        com.quanxiaoha.weblog.common.Response response = archiveService.queryArchive(req);

        assertTrue(response.isSuccess());
        // Archive reads only from t_article (live table)
        verify(archiveArticleDao).queryArticlePageList(1L, 10L);
    }

    @Test
    void queryCategoryArticlePageList_ReflectsPublishedCategoryOnly() {
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(100L).categoryId(1L).build();

        ArticleDO articleDO = ArticleDO.builder()
                .id(100L).title("Published Title").titleImage("pub.jpg")
                .description("desc").createTime(new Date()).build();

        IPage<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(articleDO));
        page.setTotal(1);

        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(100L).title("Published Title").build();

        when(articleCategoryRelDao.selectByCategoryId(1L)).thenReturn(Arrays.asList(catRel));
        when(articleDao.queryArticlePageListByArticleIds(eq(1L), eq(10L), anyList())).thenReturn(page);
        when(articleConvert.convert(articleDO)).thenReturn(itemVO);
        when(categoryDao.selectAllCategory()).thenReturn(
                Arrays.asList(CategoryDO.builder().id(1L).name("Java").build()));
        when(articleCategoryRelDao.selectByArticleIds(anyList())).thenReturn(Arrays.asList(catRel));
        when(tagDao.selectAllTag()).thenReturn(
                Arrays.asList(TagDO.builder().id(1L).name("Spring").build()));
        when(articleTagRelDao.selectByArticleIds(anyList())).thenReturn(
                Arrays.asList(ArticleTagRelDO.builder().articleId(100L).tagId(1L).build()));

        QueryCategoryArticlePageListReqVO req = QueryCategoryArticlePageListReqVO.builder()
                .current(1L).size(10L).categoryId(1L).build();
        com.quanxiaoha.weblog.common.PageResponse response = articleService.queryCategoryArticlePageList(req);

        assertTrue(response.isSuccess());
        // Category filtering uses live t_article_category_rel, not version table
        verify(articleCategoryRelDao).selectByCategoryId(1L);
    }

    @Test
    void queryTagArticlePageList_ReflectsPublishedTagsOnly() {
        ArticleTagRelDO tagRel = ArticleTagRelDO.builder()
                .articleId(100L).tagId(1L).build();

        ArticleDO articleDO = ArticleDO.builder()
                .id(100L).title("Published Title").titleImage("pub.jpg")
                .description("desc").createTime(new Date()).build();

        IPage<ArticleDO> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(articleDO));
        page.setTotal(1);

        QueryIndexArticlePageItemRspVO itemVO = QueryIndexArticlePageItemRspVO.builder()
                .id(100L).title("Published Title").build();

        when(articleTagRelDao.selectByTagId(1L)).thenReturn(Arrays.asList(tagRel));
        when(articleDao.queryArticlePageListByArticleIds(eq(1L), eq(10L), anyList())).thenReturn(page);
        when(articleConvert.convert(articleDO)).thenReturn(itemVO);
        when(categoryDao.selectAllCategory()).thenReturn(
                Arrays.asList(CategoryDO.builder().id(1L).name("Java").build()));
        when(articleCategoryRelDao.selectByArticleIds(anyList())).thenReturn(
                Arrays.asList(ArticleCategoryRelDO.builder().articleId(100L).categoryId(1L).build()));
        when(tagDao.selectAllTag()).thenReturn(
                Arrays.asList(TagDO.builder().id(1L).name("Spring").build()));
        when(articleTagRelDao.selectByArticleIds(anyList())).thenReturn(Arrays.asList(tagRel));

        QueryTagArticlePageListReqVO req = QueryTagArticlePageListReqVO.builder()
                .current(1L).size(10L).tagId(1L).build();
        com.quanxiaoha.weblog.common.PageResponse response = articleService.queryTagArticlePageList(req);

        assertTrue(response.isSuccess());
        // Tag filtering uses live t_article_tag_rel, not version table
        verify(articleTagRelDao).selectByTagId(1L);
    }
}
