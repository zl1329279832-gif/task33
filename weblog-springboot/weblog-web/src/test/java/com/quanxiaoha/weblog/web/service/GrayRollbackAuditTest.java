package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.GrayPublishReqVO;
import com.quanxiaoha.weblog.admin.model.vo.article.GrayRollbackReqVO;
import com.quanxiaoha.weblog.admin.model.vo.article.GrayRuleItemVO;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.admin.service.impl.AdminGrayPublishServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.PublishBatchStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 灰度回滚审计测试
 * 验证: 审计记录创建、GRAY->DRAFT、令牌撤销、规则删除、批次状态
 */
@ExtendWith(MockitoExtension.class)
class GrayRollbackAuditTest {

    private AdminGrayPublishServiceImpl grayPublishService;

    @Mock private AdminArticleVersionDao articleVersionDao;
    @Mock private AdminGrayRuleDao grayRuleDao;
    @Mock private AdminPreviewTokenDao previewTokenDao;
    @Mock private AdminPublishBatchDao publishBatchDao;
    @Mock private AdminVersionExposureLogDao exposureLogDao;
    @Mock private AdminRollbackAuditDao rollbackAuditDao;
    @Mock private AdminArticleServiceImpl articleService;
    @Mock private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
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
    @DisplayName("回滚 - 版本状态由 GRAY 恢复为 DRAFT")
    void rollback_StatusChangesToDraft() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(null);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("测试不通过").build();

        Response response = grayPublishService.rollbackGray(req);

        assertTrue(response.isSuccess());

        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).updateById(captor.capture());
        assertEquals(ArticleVersionStatusEnum.DRAFT.getCode(), (int) captor.getValue().getStatus());
    }

    @Test
    @DisplayName("回滚 - 灰度规则被删除")
    void rollback_RulesDeleted() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(null);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("测试不通过").build();
        grayPublishService.rollbackGray(req);

        verify(grayRuleDao).deleteByVersionId(1L);
    }

    @Test
    @DisplayName("回滚 - 预览令牌被撤销")
    void rollback_TokensRevoked() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(null);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("测试不通过").build();
        grayPublishService.rollbackGray(req);

        verify(previewTokenDao).revokeByVersionId(1L);
    }

    @Test
    @DisplayName("回滚 - 批次状态更新为 ROLLED_BACK")
    void rollback_BatchStatusUpdated() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        PublishBatchDO activeBatch = PublishBatchDO.builder().id(100L).build();
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(activeBatch);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("数据异常").build();
        grayPublishService.rollbackGray(req);

        verify(publishBatchDao).updateStatusById(eq(100L),
                eq(PublishBatchStatusEnum.ROLLED_BACK.getCode()), any(Date.class));
    }

    @Test
    @DisplayName("回滚 - 审计记录包含正确信息")
    void rollback_AuditTrailCreated() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        PublishBatchDO activeBatch = PublishBatchDO.builder().id(100L).build();
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(activeBatch);

        ArticleVersionDO stableVersion = ArticleVersionDO.builder()
                .id(50L).articleId(10L)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode()).build();
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(stableVersion);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("灰度效果不佳").build();
        grayPublishService.rollbackGray(req);

        ArgumentCaptor<RollbackAuditDO> captor = ArgumentCaptor.forClass(RollbackAuditDO.class);
        verify(rollbackAuditDao).insert(captor.capture());

        RollbackAuditDO audit = captor.getValue();
        assertEquals(10L, audit.getArticleId());
        assertEquals(1L, audit.getFromVersionId());
        assertEquals(50L, audit.getToVersionId());
        assertEquals(100L, audit.getBatchId());
        assertEquals("灰度效果不佳", audit.getReason());
        assertNotNull(audit.getRollbackTime());
    }

    @Test
    @DisplayName("回滚 - 无稳定版本时 toVersionId 为 0")
    void rollback_NoStableVersion_ToVersionIdIsZero() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(null);
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(null);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("取消灰度").build();
        grayPublishService.rollbackGray(req);

        ArgumentCaptor<RollbackAuditDO> captor = ArgumentCaptor.forClass(RollbackAuditDO.class);
        verify(rollbackAuditDao).insert(captor.capture());
        assertEquals(0L, captor.getValue().getToVersionId());
    }

    @Test
    @DisplayName("回滚 - 灰度未物化到线上表，无需恢复线上数据")
    void rollback_NoLiveTableChanges() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(null);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("回滚").build();
        Response response = grayPublishService.rollbackGray(req);

        assertTrue(response.isSuccess());
        // 验证: 回滚只是 GRAY->DRAFT，不触及线上表
        // 没有调用 publishScheduledVersion 等全量发布方法
        verify(articleService, never()).publishScheduledVersion(any());
    }
}
