package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.admin.service.impl.AdminGrayPublishServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.PublishBatchStatusEnum;
import com.quanxiaoha.weblog.common.enums.PublishBatchTypeEnum;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 灰度发布管理服务测试
 */
@ExtendWith(MockitoExtension.class)
class GrayPublishServiceTest {

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

    // ==================== grayPublish ====================

    @Test
    @DisplayName("灰度发布 - 成功")
    void grayPublish_Success() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(articleVersionDao.hasPendingPublishForArticle(10L)).thenReturn(false);
        when(articleVersionDao.hasActiveGrayForArticle(10L)).thenReturn(false);

        GrayRuleItemVO rule = GrayRuleItemVO.builder()
                .ruleType("TAG").ruleValue("vip").build();
        GrayPublishReqVO req = GrayPublishReqVO.builder()
                .versionId(1L).rules(Arrays.asList(rule)).build();

        Response response = grayPublishService.grayPublish(req);

        assertTrue(response.isSuccess());
        verify(articleVersionDao).updateById(argThat(v ->
                ((ArticleVersionDO) v).getStatus() == ArticleVersionStatusEnum.GRAY.getCode()));
        verify(grayRuleDao).insert(any(GrayRuleDO.class));
        verify(publishBatchDao).insert(argThat(b ->
                ((PublishBatchDO) b).getBatchType() == PublishBatchTypeEnum.GRAY.getCode()));
    }

    @Test
    @DisplayName("灰度发布 - 版本不存在")
    void grayPublish_VersionNotFound() {
        when(articleVersionDao.selectById(999L)).thenReturn(null);

        GrayPublishReqVO req = GrayPublishReqVO.builder().versionId(999L).build();
        Response response = grayPublishService.grayPublish(req);

        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("灰度发布 - 非草稿状态拒绝")
    void grayPublish_RejectsNonDraft() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GrayPublishReqVO req = GrayPublishReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.grayPublish(req);

        assertFalse(response.isSuccess());
        verify(grayRuleDao, never()).insert(any());
    }

    @Test
    @DisplayName("灰度发布 - 存在待定时发布版本冲突")
    void grayPublish_ConflictWithPendingPublish() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(articleVersionDao.hasPendingPublishForArticle(10L)).thenReturn(true);

        GrayPublishReqVO req = GrayPublishReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.grayPublish(req);

        assertFalse(response.isSuccess());
        verify(grayRuleDao, never()).insert(any());
    }

    @Test
    @DisplayName("灰度发布 - 已有活跃灰度版本冲突")
    void grayPublish_ConflictWithActiveGray() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);
        when(articleVersionDao.hasPendingPublishForArticle(10L)).thenReturn(false);
        when(articleVersionDao.hasActiveGrayForArticle(10L)).thenReturn(true);

        GrayPublishReqVO req = GrayPublishReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.grayPublish(req);

        assertFalse(response.isSuccess());
    }

    // ==================== updateGrayRules ====================

    @Test
    @DisplayName("更新灰度规则 - 成功")
    void updateGrayRules_Success() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GrayRuleItemVO rule = GrayRuleItemVO.builder()
                .ruleType("PERCENTAGE").ruleValue("30").build();
        GrayRuleUpdateReqVO req = GrayRuleUpdateReqVO.builder()
                .versionId(1L).rules(Arrays.asList(rule)).build();

        Response response = grayPublishService.updateGrayRules(req);

        assertTrue(response.isSuccess());
        verify(grayRuleDao).deleteByVersionId(1L);
        verify(grayRuleDao).insert(any(GrayRuleDO.class));
    }

    @Test
    @DisplayName("更新灰度规则 - 非灰度状态拒绝")
    void updateGrayRules_RejectsNonGray() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.DRAFT.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GrayRuleUpdateReqVO req = GrayRuleUpdateReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.updateGrayRules(req);

        assertFalse(response.isSuccess());
    }

    // ==================== generatePreviewToken ====================

    @Test
    @DisplayName("生成预览令牌 - 成功")
    void generatePreviewToken_Success() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GeneratePreviewTokenReqVO req = GeneratePreviewTokenReqVO.builder()
                .versionId(1L).expireHours(48).build();

        Response response = grayPublishService.generatePreviewToken(req);

        assertTrue(response.isSuccess());
        verify(previewTokenDao).insert(any(PreviewTokenDO.class));
        GeneratePreviewTokenRspVO rsp = (GeneratePreviewTokenRspVO) response.getData();
        assertNotNull(rsp.getToken());
        assertNotNull(rsp.getPreviewUrl());
        assertNotNull(rsp.getExpireAt());
    }

    @Test
    @DisplayName("生成预览令牌 - 非灰度状态拒绝")
    void generatePreviewToken_RejectsNonGray() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.DRAFT.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GeneratePreviewTokenReqVO req = GeneratePreviewTokenReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.generatePreviewToken(req);

        assertFalse(response.isSuccess());
    }

    // ==================== promoteGrayToFull ====================

    @Test
    @DisplayName("灰度全量发布 - 成功")
    void promoteGrayToFull_Success() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        PublishBatchDO activeBatch = PublishBatchDO.builder()
                .id(100L).batchNum(1).build();
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(activeBatch);

        GrayPromoteReqVO req = GrayPromoteReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.promoteGrayToFull(req);

        assertTrue(response.isSuccess());
        verify(previewTokenDao).revokeByVersionId(1L);
        verify(grayRuleDao).deleteByVersionId(1L);
        verify(publishBatchDao).updateStatusById(eq(100L),
                eq(PublishBatchStatusEnum.COMPLETED.getCode()), any(Date.class));
    }

    @Test
    @DisplayName("灰度全量发布 - 幂等（已发布直接成功）")
    void promoteGrayToFull_IdempotentAlreadyPublished() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.PUBLISHED.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GrayPromoteReqVO req = GrayPromoteReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.promoteGrayToFull(req);

        assertTrue(response.isSuccess());
        // 幂等: 已发布不再执行物化
        verify(publishBatchDao, never()).insert(any());
    }

    @Test
    @DisplayName("灰度全量发布 - 非灰度状态拒绝")
    void promoteGrayToFull_RejectsNonGray() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.DRAFT.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GrayPromoteReqVO req = GrayPromoteReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.promoteGrayToFull(req);

        assertFalse(response.isSuccess());
    }

    // ==================== rollbackGray ====================

    @Test
    @DisplayName("灰度回滚 - 成功")
    void rollbackGray_Success() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).articleId(10L)
                .status(ArticleVersionStatusEnum.GRAY.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        PublishBatchDO activeBatch = PublishBatchDO.builder().id(100L).build();
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(activeBatch);
        when(articleVersionDao.selectLatestPublishedByArticleId(10L)).thenReturn(null);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder()
                .versionId(1L).reason("灰度测试不通过").build();
        Response response = grayPublishService.rollbackGray(req);

        assertTrue(response.isSuccess());
        verify(articleVersionDao).updateById(argThat(v ->
                ((ArticleVersionDO) v).getStatus() == ArticleVersionStatusEnum.DRAFT.getCode()));
        verify(grayRuleDao).deleteByVersionId(1L);
        verify(previewTokenDao).revokeByVersionId(1L);
        verify(publishBatchDao).updateStatusById(eq(100L),
                eq(PublishBatchStatusEnum.ROLLED_BACK.getCode()), any(Date.class));
        verify(rollbackAuditDao).insert(any(RollbackAuditDO.class));
    }

    @Test
    @DisplayName("灰度回滚 - 非灰度状态拒绝")
    void rollbackGray_RejectsNonGray() {
        ArticleVersionDO version = ArticleVersionDO.builder()
                .id(1L).status(ArticleVersionStatusEnum.DRAFT.getCode()).build();
        when(articleVersionDao.selectById(1L)).thenReturn(version);

        GrayRollbackReqVO req = GrayRollbackReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.rollbackGray(req);

        assertFalse(response.isSuccess());
        verify(rollbackAuditDao, never()).insert(any());
    }

    // ==================== queryGrayStats ====================

    @Test
    @DisplayName("查询灰度统计")
    void queryGrayStats_ReturnsCorrectStats() {
        VersionExposureLogDO log1 = VersionExposureLogDO.builder()
                .userId(1L).matchedRule("TAG:vip").build();
        VersionExposureLogDO log2 = VersionExposureLogDO.builder()
                .userId(2L).matchedRule("TAG:vip").build();
        VersionExposureLogDO log3 = VersionExposureLogDO.builder()
                .userId(1L).matchedRule("PERCENTAGE:30").build();
        when(exposureLogDao.selectByVersionId(1L)).thenReturn(Arrays.asList(log1, log2, log3));

        PublishBatchDO batch = PublishBatchDO.builder().id(100L).build();
        when(publishBatchDao.selectActiveByVersionId(1L)).thenReturn(batch);

        Response response = grayPublishService.queryGrayStats(1L);

        assertTrue(response.isSuccess());
        GrayStatsRspVO stats = (GrayStatsRspVO) response.getData();
        assertEquals(3L, stats.getTotalExposures());
        assertEquals(2L, stats.getUniqueUsers());
        assertEquals(100L, stats.getBatchId());
    }

    // ==================== queryPublishBatchList ====================

    @Test
    @DisplayName("查询发布批次列表")
    void queryPublishBatchList_ReturnsBatches() {
        PublishBatchDO batch1 = PublishBatchDO.builder()
                .id(1L).batchNum(1).batchType(PublishBatchTypeEnum.GRAY.getCode())
                .status(PublishBatchStatusEnum.COMPLETED.getCode())
                .createdBy("admin").createTime(new Date()).build();
        PublishBatchDO batch2 = PublishBatchDO.builder()
                .id(2L).batchNum(2).batchType(PublishBatchTypeEnum.FULL.getCode())
                .status(PublishBatchStatusEnum.COMPLETED.getCode())
                .createdBy("admin").createTime(new Date()).build();
        when(publishBatchDao.selectByVersionId(1L)).thenReturn(Arrays.asList(batch1, batch2));

        QueryPublishBatchListReqVO req = QueryPublishBatchListReqVO.builder().versionId(1L).build();
        Response response = grayPublishService.queryPublishBatchList(req);

        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        List<QueryPublishBatchListRspVO> list = (List<QueryPublishBatchListRspVO>) response.getData();
        assertEquals(2, list.size());
    }
}
