package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.exception.StaleVersionException;
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
 * 文章版本化发布并发安全测试
 *
 * 覆盖场景：
 * 1. 草稿保存后定时发布
 * 2. 回滚与定时任务竞争
 * 3. 标签/分类迁移失败回滚
 * 4. 前台多入口读取一致性
 * 5. StaleVersionException（过时版本被拒绝）
 * 6. 发布失败不重置 PV
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishConcurrencyTest {

    private AdminArticleServiceImpl articleService;
    private ArticlePublishScheduler scheduler;

    @Mock private AdminArticleDao articleDao;
    @Mock private AdminArticleContentDao articleContentDao;
    @Mock private AdminArticleCategoryRelDao articleCategoryRelDao;
    @Mock private AdminTagDao tagDao;
    @Mock private AdminArticleTagRelDao articleTagRelDao;
    @Mock private AdminArticleVersionDao articleVersionDao;
    @Mock private AdminCategoryDao categoryDao;

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

        scheduler = new ArticlePublishScheduler();
        ReflectionTestUtils.setField(scheduler, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(scheduler, "articleService", articleService);
    }

    // ==================== 1. 草稿保存后定时发布 ====================

    @Test
    @DisplayName("草稿保存后定时发布 - 设置PENDING_PUBLISH后调度器物化")
    void testDraftSaveThenScheduledPublish() {
        // 1. 保存草稿
        when(tagDao.selectAll()).thenReturn(Collections.emptyList());
        when(tagDao.insert(any(TagDO.class))).thenAnswer(inv -> {
            ((TagDO) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(1);

        SaveArticleDraftReqVO draftReq = SaveArticleDraftReqVO.builder()
                .articleId(10L).title("定时发布标题").content("# 定时发布内容")
                .titleImage("img").description("desc").categoryId(1L)
                .tags(Arrays.asList("Java")).build();
        Response draftResp = articleService.saveDraft(draftReq);
        assertTrue(draftResp.isSuccess());

        // 2. 设置定时发布
        ArticleVersionDO pendingVersion = ArticleVersionDO.builder()
                .id(100L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("定时发布标题").titleImage("img").description("desc")
                .content("# 定时发布内容").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(100L)).thenReturn(pendingVersion);

        Date futureDate = new Date(System.currentTimeMillis() + 3600_000);
        PublishArticleVersionReqVO publishReq = PublishArticleVersionReqVO.builder()
                .versionId(100L).scheduledAt(futureDate).build();
        Response publishResp = articleService.publishVersion(publishReq);
        assertTrue(publishResp.isSuccess());

        // 验证状态已更新为 PENDING_PUBLISH
        assertEquals(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode(), (int) pendingVersion.getStatus());
        assertEquals(futureDate, pendingVersion.getScheduledAt());
        // 验证其他待发布任务被取消
        verify(articleVersionDao).invalidatePendingPublishByArticleId(10L, 100L);

        // 3. 模拟调度器执行：到期后物化
        pendingVersion.setStatus(ArticleVersionStatusEnum.PUBLISHED.getCode());
        when(articleDao.updateByIdWithVersionCas(any(ArticleDO.class), eq(100L))).thenReturn(1);

        Response scheduleResp = articleService.publishScheduledVersion(pendingVersion);
        assertTrue(scheduleResp.isSuccess());

        // 验证物化到了 live 表
        verify(articleDao).updateByIdWithVersionCas(any(ArticleDO.class), eq(100L));
        verify(articleContentDao).updateByArticleId(any());
    }

    // ==================== 2. 回滚与定时任务竞争 ====================

    @Test
    @DisplayName("回滚取消定时发布任务 - 调度器CAS失败跳过")
    void testRollbackInvalidatesPendingPublish() {
        // 设置：文章10有一个 PENDING_PUBLISH 版本(id=200) 和一个历史 PUBLISHED 版本(id=100)
        ArticleVersionDO publishedV1 = ArticleVersionDO.builder()
                .id(100L).articleId(10L).versionNum(1)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V1标题").titleImage("img1").description("desc1")
                .content("# V1内容").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(100L)).thenReturn(publishedV1);
        when(articleVersionDao.selectMaxVersionNum(10L)).thenReturn(3);
        when(articleDao.updateByIdWithVersionCas(any(ArticleDO.class), any())).thenReturn(1);

        // 执行回滚到 V1
        RollbackArticleVersionReqVO rollbackReq = RollbackArticleVersionReqVO.builder()
                .articleId(10L).targetVersionId(100L).build();
        Response rollbackResp = articleService.rollbackToVersion(rollbackReq);
        assertTrue(rollbackResp.isSuccess());

        // 验证：invalidatePendingPublishByArticleId 被调用，取消所有待发布任务
        ArgumentCaptor<Long> articleIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(articleVersionDao).invalidatePendingPublishByArticleId(
                articleIdCaptor.capture(), any());
        assertEquals(10L, articleIdCaptor.getValue());

        // 模拟调度器尝试处理已被取消的版本
        // 由于 invalidate 已将状态改为 DRAFT，CAS 从 PENDING_PUBLISH -> PUBLISHED 应返回 0
        when(articleVersionDao.updateStatusWithCas(
                200L,
                ArticleVersionStatusEnum.PENDING_PUBLISH.getCode(),
                ArticleVersionStatusEnum.PUBLISHED.getCode()
        )).thenReturn(0);

        ArticleVersionDO cancelledPending = ArticleVersionDO.builder()
                .id(200L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .scheduledAt(new Date(System.currentTimeMillis() - 1000)).build();
        when(articleVersionDao.selectPendingPublishDue(any())).thenReturn(Arrays.asList(cancelledPending));

        // 调度器运行
        scheduler.processPendingPublish();

        // 验证：CAS 失败后不会调用 publishScheduledVersion
        // publishScheduledVersion 内部会调用 articleDao.updateByIdWithVersionCas，
        // 但由于 CAS 已返回0，调度器应跳过
        // 在上面的回滚验证中已调用过一次 updateByIdWithVersionCas，
        // 此时不应有额外的 publishScheduledVersion 调用
        verify(articleVersionDao).updateStatusWithCas(200L,
                ArticleVersionStatusEnum.PENDING_PUBLISH.getCode(),
                ArticleVersionStatusEnum.PUBLISHED.getCode());
    }

    // ==================== 3. 标签/分类迁移失败回滚 ====================

    @Test
    @DisplayName("标签迁移失败时恢复上一版本并重置状态为草稿")
    void testTagMigrationFailure_RecoveryInSeparateTransaction() {
        // 设置当前版本和上一个已发布版本
        ArticleVersionDO currentVersion = ArticleVersionDO.builder()
                .id(300L).articleId(10L).versionNum(3)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("V3标题").titleImage("img3").description("desc3")
                .content("# V3内容").categoryId(2L).tagIds("1,2,3").build();
        when(articleVersionDao.selectById(300L)).thenReturn(currentVersion);
        when(articleVersionDao.selectById(300L)).thenReturn(currentVersion);

        ArticleVersionDO prevPublished = ArticleVersionDO.builder()
                .id(200L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("V2标题").titleImage("img2").description("desc2")
                .content("# V2内容").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectPreviousPublishedByArticleId(10L, 300L)).thenReturn(prevPublished);

        // CAS 更新成功（第一次主事务 + 第二次恢复事务）
        when(articleDao.updateByIdWithVersionCas(any(ArticleDO.class), any())).thenReturn(1);

        // 标签批量插入：第一次抛异常（模拟迁移失败），第二次成功（恢复时）
        doThrow(new RuntimeException("标签批量插入失败"))
                .doNothing()
                .when(articleTagRelDao).insertBatch(any());

        // 执行发布（通过 publishVersion 间接调用 executePublish）
        PublishArticleVersionReqVO publishReq = PublishArticleVersionReqVO.builder()
                .versionId(300L).build();
        Response response = articleService.publishVersion(publishReq);

        // 验证发布失败
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("标签批量插入失败"));

        // 验证恢复事务执行了：
        // 1. rollbackToPreviousVersion 尝试恢复 V2 到 live 表
        verify(articleVersionDao).selectPreviousPublishedByArticleId(10L, 300L);
        // 2. 版本状态重置为 DRAFT
        verify(articleVersionDao).updateStatus(300L, ArticleVersionStatusEnum.DRAFT.getCode());

        // 验证 insertBatch 被调用了2次（主事务失败1次 + 恢复事务成功1次）
        verify(articleTagRelDao, times(2)).insertBatch(any());
    }

    // ==================== 4. 前台多入口读取一致性 ====================

    @Test
    @DisplayName("发布后所有live表同步更新 - 前台各入口数据一致")
    void testPublishMaterializesAllLiveTablesAtomically() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(400L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("一致性标题").titleImage("一致性图片").description("一致性描述")
                .content("# 一致性内容").categoryId(5L).tagIds("10,20,30").build();
        when(articleVersionDao.selectById(400L)).thenReturn(version);
        when(articleDao.updateByIdWithVersionCas(any(ArticleDO.class), eq(400L))).thenReturn(1);

        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder().versionId(400L).build();
        Response response = articleService.publishVersion(req);
        assertTrue(response.isSuccess());

        // 验证 t_article 更新（标题、题图、描述、currentVersionId）
        ArgumentCaptor<ArticleDO> articleCaptor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao).updateByIdWithVersionCas(articleCaptor.capture(), eq(400L));
        ArticleDO updatedArticle = articleCaptor.getValue();
        assertEquals("一致性标题", updatedArticle.getTitle());
        assertEquals("一致性图片", updatedArticle.getTitleImage());
        assertEquals("一致性描述", updatedArticle.getDescription());
        assertEquals(400L, updatedArticle.getCurrentVersionId());

        // 验证 t_article_content 更新
        ArgumentCaptor<ArticleContentDO> contentCaptor = ArgumentCaptor.forClass(ArticleContentDO.class);
        verify(articleContentDao).updateByArticleId(contentCaptor.capture());
        assertEquals("# 一致性内容", contentCaptor.getValue().getContent());

        // 验证 t_article_category_rel 原子替换
        verify(articleCategoryRelDao).deleteByArticleId(10L);
        ArgumentCaptor<ArticleCategoryRelDO> catCaptor = ArgumentCaptor.forClass(ArticleCategoryRelDO.class);
        verify(articleCategoryRelDao).insert(catCaptor.capture());
        assertEquals(5L, catCaptor.getValue().getCategoryId());

        // 验证 t_article_tag_rel 原子替换
        verify(articleTagRelDao).deleteByArticleId(10L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArticleTagRelDO>> tagCaptor = ArgumentCaptor.forClass(List.class);
        verify(articleTagRelDao).insertBatch(tagCaptor.capture());
        List<ArticleTagRelDO> tagRels = tagCaptor.getValue();
        assertEquals(3, tagRels.size());
        Set<Long> tagIds = new HashSet<>();
        tagRels.forEach(r -> tagIds.add(r.getTagId()));
        assertTrue(tagIds.contains(10L));
        assertTrue(tagIds.contains(20L));
        assertTrue(tagIds.contains(30L));

        // 验证所有待发布任务被取消
        verify(articleVersionDao).invalidatePendingPublishByArticleId(10L, 400L);
    }

    // ==================== 5. StaleVersionException 场景 ====================

    @Test
    @DisplayName("定时发布遇到更新版本时抛出StaleVersionException并重置为草稿")
    void testScheduledPublish_StaleVersion_HandledGracefully() {
        // 设置版本
        ArticleVersionDO staleVersion = ArticleVersionDO.builder()
                .id(500L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("旧标题").titleImage("img").description("desc")
                .content("# 旧内容").categoryId(1L).tagIds("1").build();

        // CAS 更新返回 0：模拟已有更新版本占据 live 表
        when(articleDao.updateByIdWithVersionCas(any(ArticleDO.class), eq(500L))).thenReturn(0);

        // 查询当前 live 文章，返回更新版本 ID
        ArticleDO currentArticle = ArticleDO.builder()
                .id(10L).currentVersionId(600L).build();
        when(articleDao.queryByArticleId(10L)).thenReturn(currentArticle);

        // 执行定时发布
        Response response = articleService.publishScheduledVersion(staleVersion);

        // 验证：返回失败（被跳过）
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("被跳过"));

        // 验证：版本状态被重置为 DRAFT（因为已过时，无需恢复 live 表）
        verify(articleVersionDao).updateStatus(500L, ArticleVersionStatusEnum.DRAFT.getCode());

        // 验证：不会尝试恢复到上一版本（因为 live 表没被破坏）
        verify(articleVersionDao, never()).selectPreviousPublishedByArticleId(anyLong(), anyLong());
    }

    // ==================== 6. 发布失败不重置 PV ====================

    @Test
    @DisplayName("发布失败恢复不重置PV - readNum保持不变")
    void testPublishFailure_RecoveryPreservesReadNum() {
        // 设置当前版本
        ArticleVersionDO failVersion = ArticleVersionDO.builder()
                .id(600L).articleId(10L).versionNum(3)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("失败标题").titleImage("img").description("desc")
                .content("# 内容").categoryId(1L).tagIds("1").build();
        when(articleVersionDao.selectById(600L)).thenReturn(failVersion);

        // 设置上一个已发布版本
        ArticleVersionDO prevPublished = ArticleVersionDO.builder()
                .id(500L).articleId(10L).versionNum(2)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .title("恢复标题").titleImage("img2").description("desc2")
                .content("# 恢复内容").categoryId(2L).tagIds("2").build();
        when(articleVersionDao.selectPreviousPublishedByArticleId(10L, 600L)).thenReturn(prevPublished);

        // CAS 更新成功
        when(articleDao.updateByIdWithVersionCas(any(ArticleDO.class), any())).thenReturn(1);

        // 内容更新抛异常模拟发布失败
        doThrow(new RuntimeException("内容更新失败"))
                .doReturn(1)
                .when(articleContentDao).updateByArticleId(any());

        // 执行发布（通过 publishVersion 间接调用 executePublish）
        PublishArticleVersionReqVO publishReq = PublishArticleVersionReqVO.builder()
                .versionId(600L).build();
        Response response = articleService.publishVersion(publishReq);
        assertFalse(response.isSuccess());

        // 验证恢复事务中物化了上一版本
        verify(articleVersionDao).selectPreviousPublishedByArticleId(10L, 600L);

        // 验证所有调用 updateByIdWithVersionCas 时，readNum 均为 null（不覆盖 PV）
        ArgumentCaptor<ArticleDO> articleCaptor = ArgumentCaptor.forClass(ArticleDO.class);
        verify(articleDao, atLeastOnce()).updateByIdWithVersionCas(articleCaptor.capture(), any());
        for (ArticleDO captured : articleCaptor.getAllValues()) {
            assertNull(captured.getReadNum(), "readNum 不应被设置，避免重置 PV");
        }

        // 验证版本状态重置为 DRAFT
        verify(articleVersionDao).updateStatus(600L, ArticleVersionStatusEnum.DRAFT.getCode());
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
