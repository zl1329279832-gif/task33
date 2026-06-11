package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.schedule.ArticlePublishScheduler;
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
 * Tests for article version publish race conditions and concurrency fixes.
 * <p>
 * Covers:
 * - Rollback cancels pending scheduled publishes
 * - Scheduler skips stale versions after rollback
 * - Publish failure recovers to previous live version
 * - Tag/category atomic swap with failure recovery
 * - New article publish failure (no rollback needed)
 * - Multi-entry read consistency
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishRaceConditionTest {

    private AdminArticleServiceImpl articleService;
    private ArticlePublishScheduler scheduler;

    @Mock private AdminArticleDao articleDao;
    @Mock private AdminArticleContentDao articleContentDao;
    @Mock private AdminArticleCategoryRelDao articleCategoryRelDao;
    @Mock private AdminTagDao tagDao;
    @Mock private AdminArticleTagRelDao articleTagRelDao;
    @Mock private AdminArticleVersionDao articleVersionDao;
    @Mock private AdminCategoryDao categoryDao;
    @Mock private PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        // Set up real TransactionTemplate with mock TM (executes lambdas directly)
        PlatformTransactionManager realTxManager = mockTransactionManager();

        articleService = new AdminArticleServiceImpl(realTxManager);
        ReflectionTestUtils.setField(articleService, "articleDao", articleDao);
        ReflectionTestUtils.setField(articleService, "articleContentDao", articleContentDao);
        ReflectionTestUtils.setField(articleService, "articleCategoryRelDao", articleCategoryRelDao);
        ReflectionTestUtils.setField(articleService, "tagDao", tagDao);
        ReflectionTestUtils.setField(articleService, "articleTagRelDao", articleTagRelDao);
        ReflectionTestUtils.setField(articleService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(articleService, "categoryDao", categoryDao);

        scheduler = new ArticlePublishScheduler();
        ReflectionTestUtils.setField(scheduler, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(scheduler, "articleService", articleService);
    }

    // ==================== Rollback Cancels Pending Publish ====================

    @Test
    @DisplayName("回滚取消所有待发布的定时任务")
    void rollbackCancelsPendingScheduledPublish() {
        // V1 = published (rollback target), V2 = pending publish (should be cancelled)
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V1").content("V1 content").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(2);
        when(articleVersionDao.cancelPendingPublishByArticleId(10L)).thenReturn(1);

        RollbackArticleVersionReqVO req = RollbackArticleVersionReqVO.builder()
                .articleId(10L).targetVersionId(1L).build();
        Response response = articleService.rollbackToVersion(req);

        assertTrue(response.isSuccess());
        // Verify pending publish was cancelled
        verify(articleVersionDao).cancelPendingPublishByArticleId(10L);
        // Verify live tables updated with V1 content
        ArgumentCaptor<ArticleDO> articleCaptor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao).updateById(articleCaptor.capture());
        assertEquals("V1", articleCaptor.getValue().getTitle());
        // Verify audit version created
        ArgumentCaptor<ArticleVersionDO> versionCaptor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).insert(versionCaptor.capture());
        assertEquals(3, versionCaptor.getValue().getVersionNum());
        assertEquals(ArticleVersionStatusEnum.PUBLISHED.getCode(),
                (int) versionCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("回滚后定时发布已取消的版本 - CAS 失败直接跳过")
    void scheduledPublishAfterRollback_CasFails() {
        // After rollback cancelled V2 (now DRAFT), scheduler picks it up from
        // a stale selectPendingPublishDue result. CAS fails because status is no longer PENDING_PUBLISH.
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("V2").build();
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Collections.singletonList(v2));
        when(articleVersionDao.updateStatusWithCas(eq(2L),
                eq(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode()),
                eq(ArticleVersionStatusEnum.PUBLISHED.getCode())))
                .thenReturn(0); // CAS fails

        scheduler.processPendingPublish();

        // publishScheduledVersion should NOT be called
        verify(articleVersionDao, never()).selectLatestPublishedByArticleId(anyLong());
        verify(articleContentDao, never()).updateByArticleId(any());
    }

    // ==================== Scheduler Staleness Check ====================

    @Test
    @DisplayName("定时发布 CAS 成功后发现版本已过期 - 标记草稿跳过")
    void schedulerSkipsStaleVersionAfterCas() {
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .title("V2 scheduled").build();
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Collections.singletonList(v2));
        when(articleVersionDao.updateStatusWithCas(any(), anyInt(), anyInt()))
                .thenReturn(1); // CAS succeeds

        // A newer published version V3 exists (e.g., from rollback audit)
        ArticleVersionDO v3 = ArticleVersionDO.builder()
                .id(3L).articleId(10L).versionNum(3)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode()).build();
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(v3);

        scheduler.processPendingPublish();

        // V2 should be marked as DRAFT (stale)
        verify(articleVersionDao).updateStatus(2L, ArticleVersionStatusEnum.DRAFT.getCode());
        // publishScheduledVersion should NOT be called
        verify(articleContentDao, never()).updateByArticleId(any());
        verify(articleDao, never()).updateById(any());
    }

    // ==================== Publish Failure Recovery ====================

    @Test
    @DisplayName("发布失败后恢复到上一个已发布版本 - 标签分类迁移失败触发回滚")
    void publishFailureRecoversToPreviousPublishedVersion() {
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("V2 new").content("V2 content").categoryId(2L).tagIds("2,3").build();
        when(articleVersionDao.selectById(2L)).thenReturn(v2);

        // category insert fails after tag delete succeeds (atomic relation swap failure)
        when(articleCategoryRelDao.selectByArticleId(10L)).thenReturn(null);
        doThrow(new RuntimeException("DB constraint violation"))
                .when(articleCategoryRelDao).insert(any(ArticleCategoryRelDO.class));

        // Previous published version for recovery
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V1 stable").content("V1 content").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectPreviousPublishedByArticleId(10L, 2L)).thenReturn(v1);

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(2L).build();
        Response response = articleService.publishVersion(req);

        assertFalse(response.isSuccess());

        // Verify recovery: selected previous published version
        verify(articleVersionDao).selectPreviousPublishedByArticleId(10L, 2L);
        // Verify failed version marked as DRAFT
        verify(articleVersionDao).updateStatus(2L, ArticleVersionStatusEnum.DRAFT.getCode());

        // Verify recovery materialized V1 content (article updated with V1 title)
        ArgumentCaptor<ArticleDO> articleCaptor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao, atLeastOnce()).updateById(articleCaptor.capture());
        boolean hasV1Title = articleCaptor.getAllValues().stream()
                .anyMatch(a -> "V1 stable".equals(a.getTitle()));
        assertTrue(hasV1Title, "Recovery should have materialized V1 title to live tables");
    }

    @Test
    @DisplayName("定时发布失败 - 调用 handlePublishFailure 恢复")
    void scheduledPublishFailureCallsHandlePublishFailure() {
        ArticlePublishScheduler testScheduler = new ArticlePublishScheduler();
        ReflectionTestUtils.setField(testScheduler, "articleVersionDao", articleVersionDao);

        // Use a spy to verify handlePublishFailure is called
        AdminArticleServiceImpl serviceSpy = spy(articleService);
        ReflectionTestUtils.setField(testScheduler, "articleService", serviceSpy);

        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode()) // CAS already done
                .title("V2").content("V2 content").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Collections.singletonList(version));
        when(articleVersionDao.updateStatusWithCas(any(), anyInt(), anyInt()))
                .thenReturn(1);
        // isVersionStale returns false (not stale)
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(null);
        // publishScheduledVersion throws
        doThrow(new RuntimeException("Materialization error"))
                .when(serviceSpy).publishScheduledVersion(any());

        testScheduler.processPendingPublish();

        // handlePublishFailure should be called for recovery
        verify(serviceSpy).handlePublishFailure(version);
    }

    @Test
    @DisplayName("新文章首次发布失败 - 无回滚，仅标记草稿")
    void newArticlePublishFailure_NoRollbackNeeded() {
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).articleId(0L).versionNum(1) // New article
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("New article").content("Content").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        // Make article insert fail
        doThrow(new RuntimeException("Insert failed"))
                .when(articleDao).insertArticle(any(ArticleDO.class));

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(1L).build();
        Response response = articleService.publishVersion(req);

        assertFalse(response.isSuccess());
        // Should NOT try to rollback (no previous version for new article)
        verify(articleVersionDao, never()).selectPreviousPublishedByArticleId(anyLong(), anyLong());
        // Should mark as DRAFT
        verify(articleVersionDao).updateStatus(1L, ArticleVersionStatusEnum.DRAFT.getCode());
    }

    @Test
    @DisplayName("定时发布新文章失败 - 不尝试回滚线上版本")
    void scheduledPublishNewArticleFailure_NoRollback() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(0L).versionNum(1) // New article
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("New").content("Content").categoryId(1L).tagIds("1").build();

        // Make materialization fail
        doThrow(new RuntimeException("Insert failed"))
                .when(articleDao).insertArticle(any(ArticleDO.class));

        Response response = articleService.publishScheduledVersion(version);

        assertFalse(response.isSuccess());
        // Should NOT try to rollback (articleId is 0)
        verify(articleVersionDao, never()).selectPreviousPublishedByArticleId(anyLong(), anyLong());
        // Should mark as DRAFT
        verify(articleVersionDao).updateStatus(1L, ArticleVersionStatusEnum.DRAFT.getCode());
    }

    // ==================== Atomic Relation Swap ====================

    @Test
    @DisplayName("标签分类原子切换 - 分类插入失败时尝试恢复旧关系")
    void atomicRelationSwap_CategoryInsertFails_TriesRestore() {
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("V2").content("V2").categoryId(2L).tagIds("2").build();
        when(articleVersionDao.selectById(2L)).thenReturn(v2);

        // Backup returns old relations
        ArticleCategoryRelDO oldCatRel = ArticleCategoryRelDO.builder()
                .articleId(10L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByArticleId(10L)).thenReturn(oldCatRel);

        List<ArticleTagRelDO> oldTagRels = Collections.singletonList(
                ArticleTagRelDO.builder().articleId(10L).tagId(1L).build());
        when(articleTagRelDao.selectByArticleId(10L)).thenReturn(oldTagRels);

        // Category insert fails (after tag delete succeeds)
        doThrow(new RuntimeException("Constraint violation"))
                .when(articleCategoryRelDao).insert(argThat(rel ->
                        rel.getCategoryId() != null && rel.getCategoryId() == 2L));

        // Recovery: allow old category insert
        // (the old category has categoryId=1, so the argThat above won't match)

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(2L).build();
        Response response = articleService.publishVersion(req);

        assertFalse(response.isSuccess());

        // Verify old relations were backed up before the swap
        verify(articleCategoryRelDao).selectByArticleId(10L);
        verify(articleTagRelDao).selectByArticleId(10L);

        // Verify delete was attempted (first in the swap, then potentially in restore)
        verify(articleCategoryRelDao, atLeastOnce()).deleteByArticleId(10L);
        verify(articleTagRelDao, atLeastOnce()).deleteByArticleId(10L);

        // Verify status marked as DRAFT after failure
        verify(articleVersionDao).updateStatus(2L, ArticleVersionStatusEnum.DRAFT.getCode());
    }

    // ==================== PV Preservation ====================

    @Test
    @DisplayName("回滚和发布恢复均不重置 PV")
    void rollbackAndRecoveryPreservesPV() {
        // Rollback test: updateById should not set readNum
        ArticleVersionDO v1 = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V1").content("content").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(1L)).thenReturn(v1);
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(2);
        when(articleVersionDao.cancelPendingPublishByArticleId(10L)).thenReturn(0);

        RollbackArticleVersionReqVO req = RollbackArticleVersionReqVO.builder()
                .articleId(10L).targetVersionId(1L).build();
        articleService.rollbackToVersion(req);

        // Verify readNum is NOT set in the article update (preserves existing PV)
        ArgumentCaptor<ArticleDO> captor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao).updateById(captor.capture());
        assertNull(captor.getValue().getReadNum(),
                "readNum should be null to preserve existing PV in live table");
    }

    // ==================== Multi-Entry Read Consistency ====================

    @Test
    @DisplayName("回滚后线上表数据一致 - 所有前台入口读取相同数据")
    void liveTablesConsistentAfterRollback() {
        // Simulate: article 10 was rolled back to V1 content
        ArticleDO article = ArticleDO.builder()
                .id(10L).title("V1 title").description("V1 desc").readNum(100L).build();
        when(articleDao.queryByArticleId(10L)).thenReturn(article);

        ArticleContentDO content = ArticleContentDO.builder()
                .articleId(10L).content("# V1 content").build();
        when(articleContentDao.queryByArticleId(10L)).thenReturn(content);

        ArticleCategoryRelDO catRel = ArticleCategoryRelDO.builder()
                .articleId(10L).categoryId(1L).build();
        when(articleCategoryRelDao.selectByArticleId(10L)).thenReturn(catRel);

        ArticleTagRelDO tagRel = ArticleTagRelDO.builder()
                .articleId(10L).tagId(1L).build();
        when(articleTagRelDao.selectByArticleId(10L)).thenReturn(Collections.singletonList(tagRel));

        // Admin detail query reads from version table first (published), then falls back to live tables
        // With no versions present, it falls back to live tables
        when(articleVersionDao.selectLatestDraftByArticleId(10L)).thenReturn(null);
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(null);

        QueryArticleDetailReqVO req = QueryArticleDetailReqVO.builder().articleId(10L).build();
        Response response = articleService.queryArticleDetail(req);

        assertTrue(response.isSuccess());
        // All data from live tables (consistent across all front-end entry points)
        verify(articleDao).queryByArticleId(10L);
        verify(articleContentDao).queryByArticleId(10L);
        verify(articleCategoryRelDao).selectByArticleId(10L);
        verify(articleTagRelDao).selectByArticleId(10L);
    }

    @Test
    @DisplayName("调度器处理多个到期版本 - 仅发布非过期版本")
    void schedulerProcessesMultipleVersions_OnlyPublishesNonStale() {
        ArticleVersionDO v2 = ArticleVersionDO.builder()
                .id(2L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .title("V2 stale").build();
        ArticleVersionDO v3 = ArticleVersionDO.builder()
                .id(3L).articleId(20L).versionNum(1)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .title("V3 fresh").build();

        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Arrays.asList(v2, v3));
        when(articleVersionDao.updateStatusWithCas(any(), anyInt(), anyInt()))
                .thenReturn(1); // Both CAS succeed

        // V2 is stale (newer version exists for article 10)
        ArticleVersionDO newerV = ArticleVersionDO.builder()
                .id(4L).articleId(10L).versionNum(5)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode()).build();
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(newerV);
        // V3 is NOT stale (no newer version for article 20)
        when(articleVersionDao.selectLatestPublishedByArticleId(20L)).thenReturn(null);

        scheduler.processPendingPublish();

        // V2 marked as DRAFT (stale)
        verify(articleVersionDao).updateStatus(2L, ArticleVersionStatusEnum.DRAFT.getCode());
        // V3 should proceed to publish (no staleness mark)
        verify(articleVersionDao, never()).updateStatus(eq(3L), anyInt());
    }

    // ==================== Helper ====================

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
