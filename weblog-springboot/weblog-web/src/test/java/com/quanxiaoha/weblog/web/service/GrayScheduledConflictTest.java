package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.admin.dao.AdminGrayRuleDao;
import com.quanxiaoha.weblog.admin.dao.AdminPreviewTokenDao;
import com.quanxiaoha.weblog.admin.dao.AdminPublishBatchDao;
import com.quanxiaoha.weblog.admin.dao.AdminVersionExposureLogDao;
import com.quanxiaoha.weblog.admin.dao.AdminRollbackAuditDao;
import com.quanxiaoha.weblog.admin.model.vo.article.GrayPublishReqVO;
import com.quanxiaoha.weblog.admin.model.vo.article.GrayRuleItemVO;
import com.quanxiaoha.weblog.admin.schedule.ArticlePublishScheduler;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.admin.service.impl.AdminGrayPublishServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 灰度发布与定时发布冲突测试
 * 验证: 调度器遇到灰度活跃时延后，灰度发布遇到待定时发布时拒绝
 */
@ExtendWith(MockitoExtension.class)
class GrayScheduledConflictTest {

    // ==================== 调度器场景 ====================

    private ArticlePublishScheduler scheduler;
    @Mock private AdminArticleVersionDao articleVersionDao;
    @Mock private AdminArticleServiceImpl articleService;

    // ==================== 灰度发布场景 ====================

    private AdminGrayPublishServiceImpl grayPublishService;
    @Mock private AdminGrayRuleDao grayRuleDao;
    @Mock private AdminPreviewTokenDao previewTokenDao;
    @Mock private AdminPublishBatchDao publishBatchDao;
    @Mock private AdminVersionExposureLogDao exposureLogDao;
    @Mock private AdminRollbackAuditDao rollbackAuditDao;
    @Mock private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        // 调度器
        scheduler = new ArticlePublishScheduler();
        ReflectionTestUtils.setField(scheduler, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(scheduler, "articleService", articleService);

        // 灰度服务
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        grayPublishService = new AdminGrayPublishServiceImpl(transactionManager);
        ReflectionTestUtils.setField(grayPublishService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(grayPublishService, "grayRuleDao", grayRuleDao);
        ReflectionTestUtils.setField(grayPublishService, "previewTokenDao", previewTokenDao);
        ReflectionTestUtils.setField(grayPublishService, "publishBatchDao", publishBatchDao);
        ReflectionTestUtils.setField(grayPublishService, "exposureLogDao", exposureLogDao);
        ReflectionTestUtils.setField(grayPublishService, "rollbackAuditDao", rollbackAuditDao);
        ReflectionTestUtils.setField(grayPublishService, "articleService", articleService);
    }

    @Test
    @DisplayName("调度器 - 文章有活跃灰度版本时延后定时发布")
    void scheduler_DefersWhenGrayActive() {
        ArticleVersionDO pendingVersion = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(3)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .scheduledAt(new Date(System.currentTimeMillis() - 60_000))
                .build();

        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Arrays.asList(pendingVersion));
        // CAS 成功
        when(articleVersionDao.updateStatusWithCas(eq(1L),
                eq(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode()),
                eq(ArticleVersionStatusEnum.PUBLISHED.getCode())))
                .thenReturn(1);
        when(articleService.isVersionStale(any())).thenReturn(false);

        // 有活跃灰度版本
        ArticleVersionDO activeGray = ArticleVersionDO.builder()
                .id(50L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(activeGray);

        scheduler.processPendingPublish();

        // 验证: 定时版本被恢复为 PENDING_PUBLISH，不物化
        verify(articleVersionDao).updateStatus(1L, ArticleVersionStatusEnum.PENDING_PUBLISH.getCode());
        verify(articleService, never()).publishScheduledVersion(any());
    }

    @Test
    @DisplayName("调度器 - 无灰度冲突正常发布")
    void scheduler_PublishesWhenNoGrayConflict() {
        ArticleVersionDO pendingVersion = ArticleVersionDO.builder()
                .id(1L).articleId(10L).versionNum(3)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .scheduledAt(new Date(System.currentTimeMillis() - 60_000))
                .build();

        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Arrays.asList(pendingVersion));
        when(articleVersionDao.updateStatusWithCas(eq(1L),
                eq(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode()),
                eq(ArticleVersionStatusEnum.PUBLISHED.getCode())))
                .thenReturn(1);
        when(articleService.isVersionStale(any())).thenReturn(false);
        // 无活跃灰度
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(null);
        when(articleService.publishScheduledVersion(any())).thenReturn(Response.success());

        scheduler.processPendingPublish();

        verify(articleService).publishScheduledVersion(pendingVersion);
    }

    @Test
    @DisplayName("灰度发布 - 存在待定时发布版本时拒绝")
    void grayPublish_RejectedWhenPendingPublishExists() {
        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(2L).articleId(10L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(2L)).thenReturn(draftVersion);
        when(articleVersionDao.hasPendingPublishForArticle(10L)).thenReturn(true);

        GrayPublishReqVO req = GrayPublishReqVO.builder()
                .versionId(2L)
                .rules(Arrays.asList(GrayRuleItemVO.builder()
                        .ruleType("TAG").ruleValue("beta").build()))
                .build();

        Response response = grayPublishService.grayPublish(req);

        assertFalse(response.isSuccess());
        verify(grayRuleDao, never()).insert(any());
        verify(publishBatchDao, never()).insert(any());
    }

    @Test
    @DisplayName("灰度发布 - 同一文章已有灰度时拒绝")
    void grayPublish_RejectedWhenGrayActiveForSameArticle() {
        ArticleVersionDO draftVersion = ArticleVersionDO.builder()
                .id(3L).articleId(10L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(3L)).thenReturn(draftVersion);
        when(articleVersionDao.hasPendingPublishForArticle(10L)).thenReturn(false);
        when(articleVersionDao.hasActiveGrayForArticle(10L)).thenReturn(true);

        GrayPublishReqVO req = GrayPublishReqVO.builder().versionId(3L).build();
        Response response = grayPublishService.grayPublish(req);

        assertFalse(response.isSuccess());
    }
}
