package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.schedule.ArticlePublishScheduler;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminArticleVersionServiceTest {

    @Mock
    private AdminArticleDao articleDao;
    @Mock
    private AdminArticleContentDao articleContentDao;
    @Mock
    private AdminArticleCategoryRelDao articleCategoryRelDao;
    @Mock
    private AdminTagDao tagDao;
    @Mock
    private AdminArticleTagRelDao articleTagRelDao;
    @Mock
    private AdminArticleVersionDao articleVersionDao;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AdminArticleServiceImpl articleService;

    @BeforeEach
    void setUp() throws Exception {
        articleService = new AdminArticleServiceImpl(transactionManager);
        // Inject all mocks via reflection since constructor injection prevents field injection
        setField(articleService, "articleDao", articleDao);
        setField(articleService, "articleContentDao", articleContentDao);
        setField(articleService, "articleCategoryRelDao", articleCategoryRelDao);
        setField(articleService, "tagDao", tagDao);
        setField(articleService, "articleTagRelDao", articleTagRelDao);
        setField(articleService, "articleVersionDao", articleVersionDao);

        // Replace transactionTemplate with a mock that executes callbacks immediately
        TransactionTemplate tt = mock(TransactionTemplate.class);
        lenient().when(tt.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        setField(articleService, "transactionTemplate", tt);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // Helper to create a SaveDraftReqVO
    private SaveDraftReqVO buildSaveDraftReq(Long articleId, String title, String content, Long categoryId, List<String> tags) {
        return SaveDraftReqVO.builder()
                .articleId(articleId)
                .title(title)
                .content(content)
                .titleImage("img.jpg")
                .description("desc")
                .categoryId(categoryId)
                .tags(tags)
                .build();
    }

    @Test
    void testSaveDraft_NewArticle_DoesNotTouchLiveTables() {
        when(tagDao.selectAll()).thenReturn(Arrays.asList(
                TagDO.builder().id(1L).name("Java").build()
        ));
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenReturn(1);

        SaveDraftReqVO req = buildSaveDraftReq(null, "New Title", "New Content", 1L, Arrays.asList("1"));
        Response response = articleService.saveDraft(req);

        assertTrue(response.isSuccess());
        // Verify live tables were NOT touched
        verify(articleDao, never()).insertArticle(any());
        verify(articleContentDao, never()).insertArticleContent(any());
        verify(articleCategoryRelDao, never()).insert(any());
        verify(articleTagRelDao, never()).insertBatch(anyList());
        // Verify version was inserted
        verify(articleVersionDao).insert(any(ArticleVersionDO.class));
    }

    @Test
    void testSaveDraft_ExistingArticle_LiveTablesUnchanged() {
        when(tagDao.selectAll()).thenReturn(Arrays.asList(
                TagDO.builder().id(1L).name("Java").build()
        ));
        when(articleVersionDao.selectLatestDraftByArticleId(100L)).thenReturn(null);
        when(articleVersionDao.selectMaxVersionNum(100L)).thenReturn(1);
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenReturn(1);

        SaveDraftReqVO req = buildSaveDraftReq(100L, "Updated Title", "Updated Content", 2L, Arrays.asList("1"));
        Response response = articleService.saveDraft(req);

        assertTrue(response.isSuccess());
        verify(articleDao, never()).updateById(any());
        verify(articleContentDao, never()).updateByArticleId(any());
        verify(articleCategoryRelDao, never()).deleteByArticleId(anyLong());
        verify(articleTagRelDao, never()).deleteByArticleId(anyLong());
    }

    @Test
    void testSaveDraft_UpdateExistingDraft() {
        ArticleVersionDO existingDraft = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("Old Draft Title")
                .content("Old Content")
                .categoryId(1L)
                .tagIds("1")
                .build();

        when(tagDao.selectAll()).thenReturn(Arrays.asList(
                TagDO.builder().id(1L).name("Java").build()
        ));
        when(articleVersionDao.selectLatestDraftByArticleId(100L)).thenReturn(existingDraft);
        when(articleVersionDao.updateById(any(ArticleVersionDO.class))).thenReturn(1);

        SaveDraftReqVO req = buildSaveDraftReq(100L, "New Draft Title", "New Content", 2L, Arrays.asList("1"));
        Response response = articleService.saveDraft(req);

        assertTrue(response.isSuccess());
        verify(articleVersionDao).updateById(any(ArticleVersionDO.class));
        verify(articleVersionDao, never()).insert(any(ArticleVersionDO.class));
        assertEquals("New Draft Title", existingDraft.getTitle());
    }

    @Test
    void testSaveDraft_CannotEditPublishedVersion() {
        // This test verifies that saveDraft creates a NEW version for an article
        // whose latest version is published (not draft), rather than editing the published one
        when(tagDao.selectAll()).thenReturn(Arrays.asList(
                TagDO.builder().id(1L).name("Java").build()
        ));
        when(articleVersionDao.selectLatestDraftByArticleId(100L)).thenReturn(null);
        when(articleVersionDao.selectMaxVersionNum(100L)).thenReturn(2);
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenReturn(1);

        SaveDraftReqVO req = buildSaveDraftReq(100L, "Title", "Content", 1L, Arrays.asList("1"));
        Response response = articleService.saveDraft(req);

        assertTrue(response.isSuccess());
        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).insert(captor.capture());
        assertEquals(3, captor.getValue().getVersionNum());
        assertEquals(ArticleVersionStatusEnum.DRAFT.getCode(), captor.getValue().getStatus());
    }

    @Test
    void testPublishVersion_Immediate() {
        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("Title")
                .titleImage("img.jpg")
                .description("desc")
                .content("Content")
                .categoryId(1L)
                .tagIds("1,2")
                .build();

        when(articleVersionDao.selectById(10L)).thenReturn(draftVersion);
        when(articleVersionDao.selectPublishedVersionsByArticleId(100L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(articleDao.updateById(any())).thenReturn(1);
        when(articleContentDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleContentDao.insertArticleContent(any())).thenReturn(1);
        when(articleCategoryRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        when(articleTagRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        doNothing().when(articleTagRelDao).insertBatch(anyList());

        PublishVersionReqVO req = PublishVersionReqVO.builder().versionId(10L).build();
        Response response = articleService.publishVersion(req);

        assertTrue(response.isSuccess());
        assertEquals(ArticleVersionStatusEnum.PUBLISHED.getCode(), draftVersion.getStatus());
        assertNotNull(draftVersion.getPublishedAt());
    }

    @Test
    void testPublishVersion_Scheduled() {
        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("Title")
                .content("Content")
                .categoryId(1L)
                .tagIds("1")
                .build();

        when(articleVersionDao.selectById(10L)).thenReturn(draftVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);

        // Schedule for the future
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 1);
        Date futureDate = cal.getTime();

        PublishVersionReqVO req = PublishVersionReqVO.builder()
                .versionId(10L)
                .scheduledAt(futureDate)
                .build();
        Response response = articleService.publishVersion(req);

        assertTrue(response.isSuccess());
        assertEquals(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode(), draftVersion.getStatus());
        assertEquals(futureDate, draftVersion.getScheduledAt());
        // Should NOT have published to live
        verify(articleDao, never()).updateById(any());
    }

    @Test
    void testPublishVersion_AlreadyPublished() {
        ArticleVersionDO publishedVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .build();

        when(articleVersionDao.selectById(10L)).thenReturn(publishedVersion);

        PublishVersionReqVO req = PublishVersionReqVO.builder().versionId(10L).build();
        Response response = articleService.publishVersion(req);

        assertFalse(response.isSuccess());
        verify(articleDao, never()).updateById(any());
    }

    @Test
    void testRollback_RestoresContentAndCreatesNewVersion() {
        ArticleVersionDO targetVersion = ArticleVersionDO.builder()
                .id(5L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("Old Title")
                .titleImage("old.jpg")
                .description("Old desc")
                .content("Old Content")
                .categoryId(1L)
                .tagIds("1,2")
                .build();

        when(articleVersionDao.selectById(5L)).thenReturn(targetVersion);
        when(articleVersionDao.selectMaxVersionNum(100L)).thenReturn(3);
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenAnswer(invocation -> {
            ArticleVersionDO v = invocation.getArgument(0);
            v.setId(20L);
            return 1;
        });
        when(articleVersionDao.selectPublishedVersionsByArticleId(100L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(articleDao.updateById(any())).thenReturn(1);
        when(articleContentDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleContentDao.insertArticleContent(any())).thenReturn(1);
        when(articleCategoryRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        when(articleTagRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        doNothing().when(articleTagRelDao).insertBatch(anyList());

        RollbackVersionReqVO req = RollbackVersionReqVO.builder()
                .articleId(100L)
                .targetVersionId(5L)
                .build();
        Response response = articleService.rollbackVersion(req);

        assertTrue(response.isSuccess());
        // Verify a new version was created
        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).insert(captor.capture());
        ArticleVersionDO newVersion = captor.getValue();
        assertEquals(4, newVersion.getVersionNum());
        assertEquals("Old Title", newVersion.getTitle());
        assertEquals("Old Content", newVersion.getContent());
    }

    @Test
    void testRollback_PreservesReadNum() {
        ArticleVersionDO targetVersion = ArticleVersionDO.builder()
                .id(5L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("Title")
                .titleImage("img.jpg")
                .description("desc")
                .content("Content")
                .categoryId(1L)
                .tagIds("1")
                .build();

        when(articleVersionDao.selectById(5L)).thenReturn(targetVersion);
        when(articleVersionDao.selectMaxVersionNum(100L)).thenReturn(2);
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenAnswer(invocation -> {
            ArticleVersionDO v = invocation.getArgument(0);
            v.setId(20L);
            return 1;
        });
        when(articleVersionDao.selectPublishedVersionsByArticleId(100L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(articleDao.updateById(any())).thenReturn(1);
        when(articleContentDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleContentDao.insertArticleContent(any())).thenReturn(1);
        when(articleCategoryRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        when(articleTagRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        doNothing().when(articleTagRelDao).insertBatch(anyList());

        RollbackVersionReqVO req = RollbackVersionReqVO.builder()
                .articleId(100L)
                .targetVersionId(5L)
                .build();
        articleService.rollbackVersion(req);

        // Verify that readNum is NOT set when updating the article
        ArgumentCaptor<ArticleDO> articleCaptor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao).updateById(articleCaptor.capture());
        assertNull(articleCaptor.getValue().getReadNum());
    }

    @Test
    void testRollback_OnlyPublishedVersions() {
        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(5L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();

        when(articleVersionDao.selectById(5L)).thenReturn(draftVersion);

        RollbackVersionReqVO req = RollbackVersionReqVO.builder()
                .articleId(100L)
                .targetVersionId(5L)
                .build();
        Response response = articleService.rollbackVersion(req);

        assertFalse(response.isSuccess());
        verify(articleDao, never()).updateById(any());
    }

    @Test
    void testVersionDiff_DetectsAllChanges() {
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).title("Title A").content("Content A").description("Desc A")
                .categoryId(1L).tagIds("1,2").build();
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).title("Title B").content("Content B").description("Desc B")
                .categoryId(2L).tagIds("3,4").build();

        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        when(articleVersionDao.selectById(2L)).thenReturn(v2);

        Response response = articleService.queryVersionDiff(1L, 2L);

        assertTrue(response.isSuccess());
        VersionDiffRspVO diff = (VersionDiffRspVO) response.getData();
        assertTrue(diff.isTitleChanged());
        assertTrue(diff.isContentChanged());
        assertTrue(diff.isDescriptionChanged());
        assertTrue(diff.isCategoryChanged());
        assertTrue(diff.isTagsChanged());
    }

    @Test
    void testVersionDiff_NoChanges() {
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).title("Title").content("Content").description("Desc")
                .categoryId(1L).tagIds("1,2").build();
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).title("Title").content("Content").description("Desc")
                .categoryId(1L).tagIds("1,2").build();

        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        when(articleVersionDao.selectById(2L)).thenReturn(v2);

        Response response = articleService.queryVersionDiff(1L, 2L);

        assertTrue(response.isSuccess());
        VersionDiffRspVO diff = (VersionDiffRspVO) response.getData();
        assertFalse(diff.isTitleChanged());
        assertFalse(diff.isContentChanged());
        assertFalse(diff.isDescriptionChanged());
        assertFalse(diff.isCategoryChanged());
        assertFalse(diff.isTagsChanged());
    }

    @Test
    void testCategoryChange_DraftDoesNotAffectLive() {
        when(tagDao.selectAll()).thenReturn(Arrays.asList(
                TagDO.builder().id(1L).name("Java").build()
        ));
        when(articleVersionDao.selectLatestDraftByArticleId(100L)).thenReturn(null);
        when(articleVersionDao.selectMaxVersionNum(100L)).thenReturn(1);
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenReturn(1);

        // Save draft with new category
        SaveDraftReqVO req = buildSaveDraftReq(100L, "Title", "Content", 99L, Arrays.asList("1"));
        articleService.saveDraft(req);

        // Verify category rel table was NOT touched
        verify(articleCategoryRelDao, never()).deleteByArticleId(anyLong());
        verify(articleCategoryRelDao, never()).insert(any());
    }

    @Test
    void testTagChange_DraftDoesNotAffectLiveTags() {
        when(tagDao.selectAll()).thenReturn(Arrays.asList(
                TagDO.builder().id(1L).name("Java").build(),
                TagDO.builder().id(2L).name("Spring").build()
        ));
        when(articleVersionDao.selectLatestDraftByArticleId(100L)).thenReturn(null);
        when(articleVersionDao.selectMaxVersionNum(100L)).thenReturn(1);
        when(articleVersionDao.insert(any(ArticleVersionDO.class))).thenReturn(1);

        SaveDraftReqVO req = buildSaveDraftReq(100L, "Title", "Content", 1L, Arrays.asList("1", "2"));
        articleService.saveDraft(req);

        // Verify tag rel table was NOT touched
        verify(articleTagRelDao, never()).deleteByArticleId(anyLong());
        verify(articleTagRelDao, never()).insertBatch(anyList());
    }

    @Test
    void testTagChange_PublishUpdatesLiveTags() {
        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("Title")
                .titleImage("img.jpg")
                .description("desc")
                .content("Content")
                .categoryId(1L)
                .tagIds("1,2")
                .build();

        when(articleVersionDao.selectById(10L)).thenReturn(draftVersion);
        when(articleVersionDao.selectPublishedVersionsByArticleId(100L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(articleDao.updateById(any())).thenReturn(1);
        when(articleContentDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleContentDao.insertArticleContent(any())).thenReturn(1);
        when(articleCategoryRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        when(articleTagRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        doNothing().when(articleTagRelDao).insertBatch(anyList());

        PublishVersionReqVO req = PublishVersionReqVO.builder().versionId(10L).build();
        articleService.publishVersion(req);

        // Verify tags WERE updated in the live table
        verify(articleTagRelDao).deleteByArticleId(100L);
        verify(articleTagRelDao).insertBatch(anyList());
    }

    @Test
    void testQueryArticleVersionList() {
        List<ArticleVersionDO> versions = Arrays.asList(
                ArticleVersionDO.builder().id(1L).articleId(100L).versionNum(2).status(0)
                        .title("V2").description("d2").categoryId(1L).tagIds("1")
                        .createTime(new Date()).build(),
                ArticleVersionDO.builder().id(2L).articleId(100L).versionNum(1).status(2)
                        .title("V1").description("d1").categoryId(1L).tagIds("1")
                        .createTime(new Date()).publishedAt(new Date()).build()
        );
        when(articleVersionDao.selectByArticleId(100L)).thenReturn(versions);

        QueryVersionListReqVO req = QueryVersionListReqVO.builder().articleId(100L).build();
        Response response = articleService.queryArticleVersionList(req);

        assertTrue(response.isSuccess());
        List<?> data = (List<?>) response.getData();
        assertEquals(2, data.size());
    }

    @Test
    void testQueryArticleDetail_PrefersDraftOverPublished() {
        ArticleDO articleDO = ArticleDO.builder()
                .id(100L).title("Published Title").titleImage("pub.jpg")
                .description("Published desc").build();
        ArticleContentDO contentDO = ArticleContentDO.builder()
                .articleId(100L).content("Published Content").build();
        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(100L).categoryId(1L).build();
        List<ArticleTagRelDO> tagRels = Arrays.asList(
                ArticleTagRelDO.builder().articleId(100L).tagId(1L).build()
        );

        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(10L).articleId(100L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("Draft Title").titleImage("draft.jpg")
                .description("Draft desc").content("Draft Content")
                .categoryId(2L).tagIds("2,3").build();

        when(articleDao.queryByArticleId(100L)).thenReturn(articleDO);
        when(articleContentDao.queryByArticleId(100L)).thenReturn(contentDO);
        when(articleCategoryRelDao.selectByArticleId(100L)).thenReturn(catRel);
        when(articleTagRelDao.selectByArticleId(100L)).thenReturn(tagRels);
        when(articleVersionDao.selectLatestDraftByArticleId(100L)).thenReturn(draftVersion);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(100L).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        QueryArticleDetailRspVO rsp = (QueryArticleDetailRspVO) response.getData();
        assertEquals("Draft Title", rsp.getTitle());
        assertEquals("Draft Content", rsp.getContent());
        assertEquals(2L, rsp.getCategoryId());
    }

    @Test
    void testDeleteArticle_SoftDeletesVersions() {
        when(articleDao.deleteById(100L)).thenReturn(1);
        when(articleContentDao.deleteByArticleId(100L)).thenReturn(1);
        when(articleVersionDao.softDeleteByArticleId(100L)).thenReturn(2);

        DeleteArticleReqVO req = DeleteArticleReqVO.builder().articleId(100L).build();
        Response response = articleService.deleteArticle(req);

        assertTrue(response.isSuccess());
        verify(articleVersionDao).softDeleteByArticleId(100L);
    }

    @Test
    void testScheduler_PublishesDueVersions() {
        ArticleVersionDO dueVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .title("Scheduled Title")
                .titleImage("img.jpg")
                .description("desc")
                .content("Scheduled Content")
                .categoryId(1L)
                .tagIds("1")
                .scheduledAt(new Date(System.currentTimeMillis() - 60000))
                .build();

        when(articleVersionDao.selectPendingPublishDueVersions(any(Date.class)))
                .thenReturn(Arrays.asList(dueVersion));
        when(articleVersionDao.selectPublishedVersionsByArticleId(100L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(articleDao.updateById(any())).thenReturn(1);
        when(articleContentDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleContentDao.insertArticleContent(any())).thenReturn(1);
        when(articleCategoryRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        when(articleTagRelDao.deleteByArticleId(anyLong())).thenReturn(1);
        doNothing().when(articleTagRelDao).insertBatch(anyList());

        // Use the scheduler directly
        ArticlePublishScheduler scheduler = new ArticlePublishScheduler();
        // Inject mocks manually via reflection
        try {
            java.lang.reflect.Field versionDaoField = ArticlePublishScheduler.class.getDeclaredField("articleVersionDao");
            versionDaoField.setAccessible(true);
            versionDaoField.set(scheduler, articleVersionDao);

            java.lang.reflect.Field serviceField = ArticlePublishScheduler.class.getDeclaredField("articleService");
            serviceField.setAccessible(true);
            serviceField.set(scheduler, articleService);
        } catch (Exception e) {
            fail("Failed to inject mocks into scheduler: " + e.getMessage());
        }

        scheduler.processPendingPublish();

        // Verify publishVersionToLive was called (which updates the article)
        verify(articleDao).updateById(any(ArticleDO.class));
    }
}
