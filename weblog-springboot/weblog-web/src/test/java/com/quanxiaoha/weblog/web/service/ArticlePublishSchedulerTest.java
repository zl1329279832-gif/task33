package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.admin.schedule.ArticlePublishScheduler;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArticlePublishSchedulerTest {

    @Mock
    private AdminArticleVersionDao articleVersionDao;
    @Mock
    private AdminArticleService articleService;

    private ArticlePublishScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new ArticlePublishScheduler();
        Field versionDaoField = ArticlePublishScheduler.class.getDeclaredField("articleVersionDao");
        versionDaoField.setAccessible(true);
        versionDaoField.set(scheduler, articleVersionDao);

        Field serviceField = ArticlePublishScheduler.class.getDeclaredField("articleService");
        serviceField.setAccessible(true);
        serviceField.set(scheduler, articleService);
    }

    @Test
    void processPendingPublish_ProcessesDueVersion() {
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

        scheduler.processPendingPublish();

        verify(articleService).publishVersionToLive(dueVersion);
    }

    @Test
    void processPendingPublish_NoDueVersions() {
        when(articleVersionDao.selectPendingPublishDueVersions(any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processPendingPublish();

        verify(articleService, never()).publishVersionToLive(any());
    }

    @Test
    void processPendingPublish_SkipsFutureVersions() {
        // Future versions should not be returned by selectPendingPublishDueVersions
        when(articleVersionDao.selectPendingPublishDueVersions(any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processPendingPublish();

        verify(articleService, never()).publishVersionToLive(any());
        verify(articleVersionDao, never()).updateById(any());
    }

    @Test
    void processPendingPublish_SkipsAlreadyProcessed() {
        // Already published versions should not appear in pending query
        when(articleVersionDao.selectPendingPublishDueVersions(any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processPendingPublish();

        verify(articleService, never()).publishVersionToLive(any());
    }

    @Test
    void processPendingPublish_MarksAsDraftOnFailure() {
        ArticleVersionDO dueVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(100L)
                .versionNum(1)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .title("Fail Title")
                .content("Fail Content")
                .categoryId(1L)
                .tagIds("1")
                .scheduledAt(new Date(System.currentTimeMillis() - 60000))
                .build();

        when(articleVersionDao.selectPendingPublishDueVersions(any(Date.class)))
                .thenReturn(Arrays.asList(dueVersion));
        doThrow(new RuntimeException("publish failed"))
                .when(articleService).publishVersionToLive(dueVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);

        scheduler.processPendingPublish();

        // Verify version was reverted to DRAFT with null scheduledAt
        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).updateById(captor.capture());
        ArticleVersionDO updated = captor.getValue();
        assertEquals(ArticleVersionStatusEnum.DRAFT.getCode(), updated.getStatus());
        assertNull(updated.getScheduledAt());
    }
}
