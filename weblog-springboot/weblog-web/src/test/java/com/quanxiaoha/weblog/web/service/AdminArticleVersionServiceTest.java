package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminArticleService 版本化发布功能单元测试
 */
@ExtendWith(MockitoExtension.class)
class AdminArticleVersionServiceTest {

    private AdminArticleServiceImpl articleService;

    @Mock private AdminArticleDao articleDao;
    @Mock private AdminArticleContentDao articleContentDao;
    @Mock private AdminArticleCategoryRelDao articleCategoryRelDao;
    @Mock private AdminTagDao tagDao;
    @Mock private AdminArticleTagRelDao articleTagRelDao;
    @Mock private AdminArticleVersionDao articleVersionDao;
    @Mock private AdminCategoryDao categoryDao;
    @Mock private AdminGrayReleaseDao adminGrayReleaseDao;

    @BeforeEach
    void setUp() {
        articleService = new AdminArticleServiceImpl(mockTransactionManager());
        ReflectionTestUtils.setField(articleService, "articleDao", articleDao);
        ReflectionTestUtils.setField(articleService, "articleContentDao", articleContentDao);
        ReflectionTestUtils.setField(articleService, "articleCategoryRelDao", articleCategoryRelDao);
        ReflectionTestUtils.setField(articleService, "tagDao", tagDao);
        ReflectionTestUtils.setField(articleService, "articleTagRelDao", articleTagRelDao);
        ReflectionTestUtils.setField(articleService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(articleService, "categoryDao", categoryDao);
        ReflectionTestUtils.setField(articleService, "adminGrayReleaseDao", adminGrayReleaseDao);
    }

    // ==================== 草稿保存测试 ====================

    @Test
    @DisplayName("新建草稿 - 不触碰线上表")
    void testSaveDraft_NewArticle_DoesNotTouchLiveTables() {
        when(tagDao.selectAll()).thenReturn(Collections.emptyList());
        when(tagDao.insert(any(TagDO.class))).thenAnswer(inv -> {
            TagDO tag = inv.getArgument(0);
            tag.setId(1L);
            return 1;
        });

        SaveArticleDraftReqVO req = SaveArticleDraftReqVO.builder()
                .title("草稿标题").content("# 草稿内容").titleImage("http://img.test/1.jpg")
                .description("草稿描述").categoryId(1L).tags(Arrays.asList("Java")).build();

        Response response = articleService.saveDraft(req);
        assertTrue(response.isSuccess());

        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).insert(captor.capture());
        ArticleVersionDO saved = captor.getValue();
        assertEquals(ArticleVersionStatusEnum.DRAFT.getCode(), (int) saved.getStatus());
        assertEquals(0L, saved.getArticleId());
        assertEquals("草稿标题", saved.getTitle());
        assertEquals(1, saved.getVersionNum());

        verify(articleDao, never()).insertArticle(any());
        verify(articleContentDao, never()).insertArticleContent(any());
        verify(articleCategoryRelDao, never()).insert(any());
        verify(articleTagRelDao, never()).insertBatch(any());
    }

    @Test
    @DisplayName("已有文章保存草稿 - 线上表不变")
    void testSaveDraft_ExistingArticle_LiveTablesUnchanged() {
        when(tagDao.selectAll()).thenReturn(Collections.emptyList());
        when(articleVersionDao.selectMaxVersionNum(1L)).thenReturn(2);
        when(tagDao.insert(any(TagDO.class))).thenAnswer(inv -> {
            ((TagDO) inv.getArgument(0)).setId(10L);
            return 1;
        });

        SaveArticleDraftReqVO req = SaveArticleDraftReqVO.builder()
                .articleId(1L).title("更新标题").content("# 更新内容")
                .titleImage("http://img.test/2.jpg").description("更新描述")
                .categoryId(2L).tags(Arrays.asList("NewTag")).build();

        Response response = articleService.saveDraft(req);
        assertTrue(response.isSuccess());

        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).insert(captor.capture());
        assertEquals(3, captor.getValue().getVersionNum());
        assertEquals(1L, captor.getValue().getArticleId());

        verify(articleDao, never()).updateById(any());
        verify(articleContentDao, never()).updateByArticleId(any());
    }

    @Test
    @DisplayName("更新已有草稿版本")
    void testSaveDraft_UpdateExistingDraft() {
        ArticleVersionDO existingDraft = ArticleVersionDO.builder()
                .id(100L).articleId(1L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode()).title("旧标题").build();
        when(articleVersionDao.selectById(100L)).thenReturn(existingDraft);
        when(tagDao.selectAll()).thenReturn(Collections.emptyList());
        when(tagDao.insert(any(TagDO.class))).thenAnswer(inv -> {
            ((TagDO) inv.getArgument(0)).setId(5L);
            return 1;
        });

        SaveArticleDraftReqVO req = SaveArticleDraftReqVO.builder()
                .versionId(100L).title("新标题").content("# 新内容")
                .titleImage("img").description("desc").categoryId(1L)
                .tags(Arrays.asList("Tag1")).build();

        Response response = articleService.saveDraft(req);
        assertTrue(response.isSuccess());

        verify(articleVersionDao).updateById(any(ArticleVersionDO.class));
        verify(articleVersionDao, never()).insert(any());
        assertEquals("新标题", existingDraft.getTitle());
    }

    @Test
    @DisplayName("不能编辑非草稿状态的版本")
    void testSaveDraft_CannotEditPublishedVersion() {
        ArticleVersionDO published = ArticleVersionDO.builder()
                .id(100L).status(ArticleVersionStatusEnum.PUBLISHED.getCode()).build();
        when(articleVersionDao.selectById(100L)).thenReturn(published);

        SaveArticleDraftReqVO req = SaveArticleDraftReqVO.builder()
                .versionId(100L).title("标题").content("内容")
                .titleImage("img").description("desc").categoryId(1L)
                .tags(Arrays.asList("Tag")).build();

        Response response = articleService.saveDraft(req);
        assertFalse(response.isSuccess());
    }

    // ==================== 发布测试 ====================

    @Test
    @DisplayName("立即发布 - 物化到线上表")
    void testPublishVersion_Immediate() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(1L).articleId(0L).versionNum(1)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("发布标题").titleImage("img").description("desc")
                .content("# 内容").categoryId(1L).tagIds("1,2").build();
        when(articleVersionDao.selectById(1L)).thenReturn(draft);
        when(articleDao.insertArticle(any(ArticleDO.class))).thenAnswer(inv -> {
            ((ArticleDO) inv.getArgument(0)).setId(10L);
            return 1;
        });

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder().versionId(1L).build();
        Response response = articleService.publishVersion(req);
        assertTrue(response.isSuccess());

        verify(articleDao).insertArticle(any());
        verify(articleContentDao).insertArticleContent(any());
        verify(articleCategoryRelDao).insert(any());
        verify(articleTagRelDao).insertBatch(any());
        verify(articleVersionDao).updateArticleId(eq(1L), eq(10L));
    }

    @Test
    @DisplayName("定时发布 - 设置为PENDING_PUBLISH")
    void testPublishVersion_Scheduled() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(1L).articleId(1L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(draft);

        Date futureDate = new Date(System.currentTimeMillis() + 3600_000);
        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(1L).scheduledAt(futureDate).build();

        Response response = articleService.publishVersion(req);
        assertTrue(response.isSuccess());

        assertEquals(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode(), (int) draft.getStatus());
        assertEquals(futureDate, draft.getScheduledAt());
        verify(articleVersionDao).updateById(draft);
        verify(articleDao, never()).updateById(any());
    }

    @Test
    @DisplayName("已发布版本不能重复发布")
    void testPublishVersion_AlreadyPublished() {
        ArticleVersionDO published = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.PUBLISHED.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(published);

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder().versionId(1L).build();
        Response response = articleService.publishVersion(req);
        assertFalse(response.isSuccess());
    }

    // ==================== 版本回滚测试 ====================

    @Test
    @DisplayName("回滚恢复历史内容并创建新版本")
    void testRollback_RestoresContentAndCreatesNewVersion() {
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V1标题").titleImage("img1").description("desc1")
                .content("# V1内容").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(3);

        RollbackArticleVersionReqVO req = RollbackArticleVersionReqVO.builder()
                .articleId(10L).targetVersionId(1L).build();
        Response response = articleService.rollbackToVersion(req);
        assertTrue(response.isSuccess());

        ArgumentCaptor<ArticleDO> articleCaptor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao).updateById(articleCaptor.capture());
        assertEquals("V1标题", articleCaptor.getValue().getTitle());

        ArgumentCaptor<ArticleVersionDO> versionCaptor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).insert(versionCaptor.capture());
        assertEquals(4, versionCaptor.getValue().getVersionNum());
        assertEquals(ArticleVersionStatusEnum.PUBLISHED.getCode(), (int) versionCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("回滚保留PV")
    void testRollback_PreservesReadNum() {
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V1").content("content").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(2);

        RollbackArticleVersionReqVO req = RollbackArticleVersionReqVO.builder()
                .articleId(10L).targetVersionId(1L).build();
        articleService.rollbackToVersion(req);

        ArgumentCaptor<ArticleDO> captor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao).updateById(captor.capture());
        assertNull(captor.getValue().getReadNum());
    }

    @Test
    @DisplayName("只能回滚到已发布的版本")
    void testRollback_OnlyPublishedVersions() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(2L).articleId(10L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode()).build();
        when(articleVersionDao.selectById(2L)).thenReturn(draft);

        RollbackArticleVersionReqVO req = RollbackArticleVersionReqVO.builder()
                .articleId(10L).targetVersionId(2L).build();
        Response response = articleService.rollbackToVersion(req);
        assertFalse(response.isSuccess());
    }

    // ==================== 标签/分类变更测试 ====================

    @Test
    @DisplayName("草稿标签不影响线上")
    void testTagChange_DraftDoesNotAffectLiveTags() {
        TagDO existingTag = TagDO.builder().id(1L).name("Java").build();
        when(tagDao.selectAll()).thenReturn(Arrays.asList(existingTag));
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(1);

        SaveArticleDraftReqVO req = SaveArticleDraftReqVO.builder()
                .articleId(10L).title("标题").content("内容").titleImage("img")
                .description("desc").categoryId(1L)
                .tags(Arrays.asList("1", "Spring", "MyBatis")).build();

        articleService.saveDraft(req);
        verify(articleTagRelDao, never()).insertBatch(any());
        verify(articleTagRelDao, never()).deleteByArticleId(any());
    }

    @Test
    @DisplayName("发布后线上标签更新")
    void testTagChange_PublishUpdatesLiveTags() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("标题").titleImage("img").description("desc")
                .content("内容").categoryId(1L).tagIds("1,2,3").build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder().versionId(1L).build();
        articleService.publishVersion(req);

        verify(articleTagRelDao).deleteByArticleId(10L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArticleTagRelDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(articleTagRelDao).insertBatch(captor.capture());
        assertEquals(3, captor.getValue().size());
    }

    @Test
    @DisplayName("草稿分类不影响线上")
    void testCategoryChange_DraftDoesNotAffectLive() {
        when(tagDao.selectAll()).thenReturn(Collections.emptyList());
        when(tagDao.insert(any(TagDO.class))).thenAnswer(inv -> {
            ((TagDO) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(1);

        SaveArticleDraftReqVO req = SaveArticleDraftReqVO.builder()
                .articleId(10L).title("标题").content("内容").titleImage("img")
                .description("desc").categoryId(2L).tags(Arrays.asList("Tag")).build();

        articleService.saveDraft(req);
        verify(articleCategoryRelDao, never()).insert(any());
        verify(articleCategoryRelDao, never()).deleteByArticleId(any());
    }

    // ==================== 版本差异对比测试 ====================

    @Test
    @DisplayName("检测所有字段变更")
    void testVersionDiff_DetectsAllChanges() {
        ArticleVersionDO left = ArticleVersionDO.builder()
                .id(1L).versionNum(1).title("标题A").content("内容A")
                .categoryId(1L).tagIds("1").description("描述A").titleImage("imgA").build();
        ArticleVersionDO right = ArticleVersionDO.builder()
                .id(2L).versionNum(2).title("标题B").content("内容B")
                .categoryId(2L).tagIds("1,2").description("描述B").titleImage("imgB").build();
        when(articleVersionDao.selectById(1L)).thenReturn(left);
        when(articleVersionDao.selectById(2L)).thenReturn(right);

        CategoryDO cat1 = CategoryDO.builder().id(1L).name("分类1").build();
        CategoryDO cat2 = CategoryDO.builder().id(2L).name("分类2").build();
        when(categoryDao.selectAllCategory()).thenReturn(Arrays.asList(cat1, cat2));
        TagDO tag1 = TagDO.builder().id(1L).name("Java").build();
        TagDO tag2 = TagDO.builder().id(2L).name("Spring").build();
        when(tagDao.selectAll()).thenReturn(Arrays.asList(tag1, tag2));

        QueryVersionDiffReqVO req = QueryVersionDiffReqVO.builder()
                .leftVersionId(1L).rightVersionId(2L).build();
        Response response = articleService.queryVersionDiff(req);
        assertTrue(response.isSuccess());

        QueryVersionDiffRspVO diff = (QueryVersionDiffRspVO) response.getData();
        assertTrue(diff.isTitleChanged());
        assertTrue(diff.isContentChanged());
        assertTrue(diff.isCategoryChanged());
        assertTrue(diff.isTagsChanged());
        assertTrue(diff.isDescriptionChanged());
        assertTrue(diff.isTitleImageChanged());
        assertEquals("标题A", diff.getLeftTitle());
        assertEquals("标题B", diff.getRightTitle());
        assertEquals("分类1", diff.getLeftCategoryName());
        assertEquals("分类2", diff.getRightCategoryName());
        assertEquals(1, diff.getAddedTagNames().size());
        assertEquals("Spring", diff.getAddedTagNames().get(0));
    }

    @Test
    @DisplayName("无变更时所有标志为false")
    void testVersionDiff_NoChanges() {
        ArticleVersionDO v = ArticleVersionDO.builder()
                .id(1L).versionNum(1).title("相同").content("相同内容")
                .categoryId(1L).tagIds("1").description("相同描述").titleImage("相同图片").build();
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).versionNum(2).title("相同").content("相同内容")
                .categoryId(1L).tagIds("1").description("相同描述").titleImage("相同图片").build();
        when(articleVersionDao.selectById(1L)).thenReturn(v);
        when(articleVersionDao.selectById(2L)).thenReturn(v2);

        QueryVersionDiffReqVO req = QueryVersionDiffReqVO.builder()
                .leftVersionId(1L).rightVersionId(2L).build();
        Response response = articleService.queryVersionDiff(req);
        QueryVersionDiffRspVO diff = (QueryVersionDiffRspVO) response.getData();

        assertFalse(diff.isTitleChanged());
        assertFalse(diff.isContentChanged());
        assertFalse(diff.isCategoryChanged());
        assertFalse(diff.isTagsChanged());
        assertFalse(diff.isDescriptionChanged());
        assertFalse(diff.isTitleImageChanged());
    }

    // ==================== 删除文章测试 ====================

    @Test
    @DisplayName("删除文章级联软删版本")
    void testDeleteArticle_SoftDeletesVersions() {
        DeleteArticleReqVO req = DeleteArticleReqVO.builder().articleId(10L).build();
        Response response = articleService.deleteArticle(req);
        assertTrue(response.isSuccess());

        verify(articleDao).deleteById(10L);
        verify(articleContentDao).deleteByArticleId(10L);
        verify(articleVersionDao).softDeleteByArticleId(10L);
    }

    // ==================== 版本列表与详情测试 ====================

    @Test
    @DisplayName("查询版本列表")
    void testQueryArticleVersionList() {
        List<ArticleVersionDO> versions = Arrays.asList(
                ArticleVersionDO.builder().id(3L).versionNum(3)
                        .status(ArticleVersionStatusEnum.DRAFT.getCode())
                        .title("草稿").createTime(new Date()).build(),
                ArticleVersionDO.builder().id(2L).versionNum(2)
                        .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                        .title("V2").createTime(new Date()).publishedAt(new Date()).build(),
                ArticleVersionDO.builder().id(1L).versionNum(1)
                        .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                        .title("V1").createTime(new Date()).publishedAt(new Date()).build());
        when(articleVersionDao.selectAllByArticleId(10L)).thenReturn(versions);

        Response response = articleService.queryArticleVersionList(10L);
        assertTrue(response.isSuccess());

        @SuppressWarnings("unchecked")
        List<QueryArticleVersionListRspVO> list = (List<QueryArticleVersionListRspVO>) response.getData();
        assertEquals(3, list.size());
        assertEquals("DRAFT", list.get(0).getStatus());
        assertEquals("PUBLISHED", list.get(1).getStatus());
    }

    @Test
    @DisplayName("管理员查看详情 - 优先返回草稿")
    void testQueryArticleDetail_PrefersDraftOverPublished() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("草稿标题").content("草稿内容")
                .categoryId(1L).tagIds("1").description("草稿描述").titleImage("img").build();
        when(articleVersionDao.selectLatestDraftByArticleId(10L)).thenReturn(draft);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(10L).build();
        Response response = articleService.queryArticleDetail(req);
        assertTrue(response.isSuccess());

        QueryArticleDetailRspVO detail = (QueryArticleDetailRspVO) response.getData();
        assertEquals("草稿标题", detail.getTitle());
    }

    // ==================== 定时发布调度测试 ====================

    @Test
    @DisplayName("调度器物化到期版本")
    void testScheduler_PublishesDueVersions() {
        ArticleVersionDO pending = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("定时发布标题").titleImage("img").description("desc")
                .content("# 内容").categoryId(1L).tagIds("1").build();

        Response response = articleService.publishScheduledVersion(pending);
        assertTrue(response.isSuccess());

        verify(articleDao).updateById(any());
        verify(articleContentDao).updateByArticleId(any());
        verify(articleCategoryRelDao).deleteByArticleId(10L);
        verify(articleCategoryRelDao).insert(any());
        verify(articleTagRelDao).deleteByArticleId(10L);
        verify(articleTagRelDao).insertBatch(any());
    }

    // ==================== 辅助方法 ====================

    private PlatformTransactionManager mockTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
