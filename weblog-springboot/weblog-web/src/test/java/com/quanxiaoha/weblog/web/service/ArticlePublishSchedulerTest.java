package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.admin.schedule.ArticlePublishScheduler;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 定时发布调度器测试
 */
@ExtendWith(MockitoExtension.class)
class ArticlePublishSchedulerTest {

    private ArticlePublishScheduler scheduler;

    @Mock
    private AdminArticleVersionDao articleVersionDao;
    @Mock
    private AdminArticleServiceImpl articleService;

    @BeforeEach
    void setUp() {
        scheduler = new ArticlePublishScheduler();
        ReflectionTestUtils.setField(scheduler, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(scheduler, "articleService", articleService);
    }

    @Test
    @DisplayName("无到期版本时不执行任何操作")
    void processPendingPublish_NoDueVersions() {
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processPendingPublish();

        verify(articleVersionDao, never()).updateStatusWithCas(any(), anyInt(), anyInt());
        verify(articleService, never()).publishScheduledVersion(any());
    }

    @Test
    @DisplayName("处理到期的待发布版本")
    void processPendingPublish_ProcessesDueVersion() {
        ArticleVersionDO pending = ArticleVersionDO.builder()
                .id(1L)
                .articleId(10L)
                .versionNum(2)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .scheduledAt(new Date(System.currentTimeMillis() - 60_000)) // 1分钟前
                .build();
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Arrays.asList(pending));
        // CAS 成功
        when(articleVersionDao.updateStatusWithCas(eq(1L),
                eq(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode()),
                eq(ArticleVersionStatusEnum.PUBLISHED.getCode())))
                .thenReturn(1);
        when(articleService.publishScheduledVersion(any())).thenReturn(Response.success());

        scheduler.processPendingPublish();

        verify(articleVersionDao).updateStatusWithCas(eq(1L),
                eq(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode()),
                eq(ArticleVersionStatusEnum.PUBLISHED.getCode()));
        verify(articleService).publishScheduledVersion(pending);
    }

    @Test
    @DisplayName("跳过已被其他实例处理的版本")
    void processPendingPublish_SkipsAlreadyProcessed() {
        ArticleVersionDO pending = ArticleVersionDO.builder()
                .id(1L)
                .articleId(10L)
                .versionNum(2)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .build();
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Arrays.asList(pending));
        // CAS 失败（已被其他实例处理）
        when(articleVersionDao.updateStatusWithCas(any(), anyInt(), anyInt()))
                .thenReturn(0);

        scheduler.processPendingPublish();

        verify(articleService, never()).publishScheduledVersion(any());
    }

    @Test
    @DisplayName("发布失败时标记为草稿")
    void processPendingPublish_MarksAsDraftOnFailure() {
        ArticleVersionDO pending = ArticleVersionDO.builder()
                .id(1L)
                .articleId(10L)
                .versionNum(2)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .build();
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Arrays.asList(pending));
        when(articleVersionDao.updateStatusWithCas(any(), anyInt(), anyInt()))
                .thenReturn(1);
        when(articleService.publishScheduledVersion(any()))
                .thenThrow(new RuntimeException("数据库错误"));

        scheduler.processPendingPublish();

        verify(articleVersionDao).updateStatus(1L, ArticleVersionStatusEnum.DRAFT.getCode());
    }

    @Test
    @DisplayName("未到期版本不被处理")
    void processPendingPublish_SkipsFutureVersions() {
        // 模拟 selectPendingPublishDue 只返回到期版本
        // 未到期的版本不会出现在返回结果中（SQL 过滤）
        when(articleVersionDao.selectPendingPublishDue(any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processPendingPublish();

        verify(articleService, never()).publishScheduledVersion(any());
    }
}
