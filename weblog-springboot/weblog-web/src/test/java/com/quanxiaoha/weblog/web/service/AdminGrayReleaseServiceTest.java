package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.impl.AdminGrayReleaseServiceImpl;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.GrayAccessTypeEnum;
import com.quanxiaoha.weblog.common.enums.GrayFeedbackTypeEnum;
import com.quanxiaoha.weblog.common.enums.GraySegmentRuleTypeEnum;
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
 * AdminGrayReleaseService 灰度管理服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class AdminGrayReleaseServiceTest {

    private AdminGrayReleaseServiceImpl grayService;
    private AdminArticleServiceImpl articleService;

    @Mock private AdminArticleVersionDao articleVersionDao;
    @Mock private AdminGrayReleaseDao adminGrayReleaseDao;
    @Mock private AdminUserTagDao adminUserTagDao;
    @Mock private AdminArticleDao articleDao;
    @Mock private AdminArticleContentDao articleContentDao;
    @Mock private AdminArticleCategoryRelDao articleCategoryRelDao;
    @Mock private AdminTagDao tagDao;
    @Mock private AdminArticleTagRelDao articleTagRelDao;
    @Mock private AdminCategoryDao categoryDao;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = mockTransactionManager();

        // 初始化 AdminArticleServiceImpl
        articleService = new AdminArticleServiceImpl(txManager);
        ReflectionTestUtils.setField(articleService, "articleDao", articleDao);
        ReflectionTestUtils.setField(articleService, "articleContentDao", articleContentDao);
        ReflectionTestUtils.setField(articleService, "articleCategoryRelDao", articleCategoryRelDao);
        ReflectionTestUtils.setField(articleService, "tagDao", tagDao);
        ReflectionTestUtils.setField(articleService, "articleTagRelDao", articleTagRelDao);
        ReflectionTestUtils.setField(articleService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(articleService, "categoryDao", categoryDao);
        ReflectionTestUtils.setField(articleService, "adminGrayReleaseDao", adminGrayReleaseDao);

        // 初始化 AdminGrayReleaseServiceImpl
        grayService = new AdminGrayReleaseServiceImpl(txManager);
        ReflectionTestUtils.setField(grayService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(grayService, "adminGrayReleaseDao", adminGrayReleaseDao);
        ReflectionTestUtils.setField(grayService, "adminUserTagDao", adminUserTagDao);
        ReflectionTestUtils.setField(grayService, "adminArticleService", articleService);
    }

    // ==================== 灰度发布测试 ====================

    @Test
    @DisplayName("草稿发布灰度 - 带标签和百分比规则")
    void publishGray_draftToGrayWithRules() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .title("灰度标题").content("灰度内容").categoryId(1L).tagIds("1,2")
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);
        when(articleVersionDao.selectAllByArticleId(1L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.insertSegmentRule(any())).thenReturn(1);

        PublishGrayVersionReqVO req = PublishGrayVersionReqVO.builder()
                .versionId(10L)
                .rules(Arrays.asList(
                        GrayRuleConfigVO.builder().ruleType(GraySegmentRuleTypeEnum.TAG_USERS.getCode())
                                .tagIds(Arrays.asList(1L, 2L)).build(),
                        GrayRuleConfigVO.builder().ruleType(GraySegmentRuleTypeEnum.PERCENTAGE.getCode())
                                .percentage(20).build()))
                .build();

        Response response = grayService.publishGrayVersion(req);
        assertTrue(response.isSuccess());

        // 验证版本状态更新为GRAY
        ArgumentCaptor<ArticleVersionDO> versionCaptor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).updateById(versionCaptor.capture());
        assertEquals(ArticleVersionStatusEnum.GRAY.getCode(), versionCaptor.getValue().getStatus());

        // 验证插入了2条规则
        verify(adminGrayReleaseDao, times(2)).insertSegmentRule(any());
    }

    @Test
    @DisplayName("灰度发布 - 带预览令牌生成")
    void publishGray_withPreviewTokens() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);
        when(articleVersionDao.selectAllByArticleId(1L)).thenReturn(Collections.emptyList());
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.insertSegmentRule(any())).thenReturn(1);
        when(adminGrayReleaseDao.insertPreviewTokenBatch(anyList())).thenReturn(3);

        Date expireAt = new Date(System.currentTimeMillis() + 86400000L);
        PublishGrayVersionReqVO req = PublishGrayVersionReqVO.builder()
                .versionId(10L)
                .rules(Collections.singletonList(
                        GrayRuleConfigVO.builder()
                                .ruleType(GraySegmentRuleTypeEnum.PREVIEW_LINK.getCode())
                                .previewExpireAt(expireAt)
                                .previewTokenCount(3)
                                .build()))
                .build();

        Response response = grayService.publishGrayVersion(req);
        assertTrue(response.isSuccess());

        // 验证生成了3个预览令牌
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GrayPreviewTokenDO>> tokenCaptor = ArgumentCaptor.forClass(List.class);
        verify(adminGrayReleaseDao).insertPreviewTokenBatch(tokenCaptor.capture());
        assertEquals(3, tokenCaptor.getValue().size());
    }

    @Test
    @DisplayName("非草稿版本 - 拒绝灰度发布")
    void publishGray_rejectsNonDraft() {
        ArticleVersionDO published = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(published);

        PublishGrayVersionReqVO req = PublishGrayVersionReqVO.builder()
                .versionId(10L)
                .rules(Collections.singletonList(
                        GrayRuleConfigVO.builder().ruleType(1).tagIds(Arrays.asList(1L)).build()))
                .build();

        Response response = grayService.publishGrayVersion(req);
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("已有灰度版本 - 拒绝创建第二个灰度")
    void publishGray_rejectsExistingGray() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);
        ArticleVersionDO existingGray = ArticleVersionDO.builder()
                .id(9L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(existingGray);

        PublishGrayVersionReqVO req = PublishGrayVersionReqVO.builder()
                .versionId(10L)
                .rules(Collections.singletonList(
                        GrayRuleConfigVO.builder().ruleType(1).tagIds(Arrays.asList(1L)).build()))
                .build();

        Response response = grayService.publishGrayVersion(req);
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("存在定时发布 - 拒绝灰度")
    void publishGray_rejectsPendingPublishConflict() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);

        // 模拟存在PENDING_PUBLISH版本
        ArticleVersionDO pending = ArticleVersionDO.builder()
                .id(8L).articleId(1L)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .build();
        when(articleVersionDao.selectAllByArticleId(1L)).thenReturn(Collections.singletonList(pending));

        PublishGrayVersionReqVO req = PublishGrayVersionReqVO.builder()
                .versionId(10L)
                .rules(Collections.singletonList(
                        GrayRuleConfigVO.builder().ruleType(1).tagIds(Arrays.asList(1L)).build()))
                .build();

        Response response = grayService.publishGrayVersion(req);
        assertFalse(response.isSuccess());
    }

    // ==================== 全量发布测试 ====================

    @Test
    @DisplayName("全量发布灰度版本 - 物化并停用规则")
    void fullPublish_materializesAndDeactivatesRules() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(3)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .title("灰度标题").content("灰度内容").categoryId(1L).tagIds("1,2")
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleDao.updateById(any())).thenReturn(1);
        when(articleContentDao.updateByArticleId(any())).thenReturn(1);
        when(articleCategoryRelDao.selectByArticleId(1L)).thenReturn(
                ArticleCategoryRelDO.builder().articleId(1L).categoryId(1L).build());
        when(articleTagRelDao.selectByArticleId(1L)).thenReturn(Collections.emptyList());
        when(articleCategoryRelDao.deleteByArticleId(1L)).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        when(articleTagRelDao.deleteByArticleId(1L)).thenReturn(0);

        FullPublishGrayReqVO req = FullPublishGrayReqVO.builder().grayVersionId(10L).build();
        Response response = grayService.fullPublishGray(req);
        assertTrue(response.isSuccess());

        // 验证状态更新为PUBLISHED
        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao, atLeastOnce()).updateById(captor.capture());
        boolean hasPublished = captor.getAllValues().stream()
                .anyMatch(v -> v.getStatus() == ArticleVersionStatusEnum.PUBLISHED.getCode());
        assertTrue(hasPublished);

        verify(adminGrayReleaseDao).deactivateRulesByVersionId(10L);
        verify(adminGrayReleaseDao).softDeleteTokensByVersionId(10L);
    }

    @Test
    @DisplayName("全量发布幂等 - 已发布版本再次全量发布")
    void fullPublish_idempotent() {
        ArticleVersionDO alreadyPublished = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.PUBLISHED.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(alreadyPublished);

        FullPublishGrayReqVO req = FullPublishGrayReqVO.builder().grayVersionId(10L).build();
        Response response = grayService.fullPublishGray(req);
        assertTrue(response.isSuccess());

        // 不应调用物化
        verify(articleDao, never()).updateById(any());
    }

    @Test
    @DisplayName("全量发布新文章灰度 - articleId=0")
    void fullPublish_newArticle() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(0L).versionNum(1)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .title("新文章灰度").content("内容").categoryId(1L).tagIds("1")
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);

        // 模拟 materializeVersion
        when(articleDao.insertArticle(any())).thenAnswer(inv -> {
            ArticleDO a = inv.getArgument(0);
            a.setId(100L);
            return 1;
        });
        when(articleContentDao.insertArticleContent(any())).thenReturn(1);
        when(articleCategoryRelDao.insert(any())).thenReturn(1);
        doNothing().when(articleTagRelDao).insertBatch(any());
        when(articleVersionDao.updateArticleId(10L, 100L)).thenReturn(1);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(0);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(0);

        FullPublishGrayReqVO req = FullPublishGrayReqVO.builder().grayVersionId(10L).build();
        Response response = grayService.fullPublishGray(req);
        assertTrue(response.isSuccess());

        verify(articleDao).insertArticle(any());
    }

    // ==================== 回滚测试 ====================

    @Test
    @DisplayName("灰度回滚 - 取消灰度保持线上稳定")
    void rollbackGray_cancelsGrayKeepsStable() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(
                ArticleVersionDO.builder().id(5L).build());
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).reason("效果不好").build();
        Response response = grayService.rollbackGray(req);
        assertTrue(response.isSuccess());

        // 验证灰度版本变为DRAFT
        ArgumentCaptor<ArticleVersionDO> captor = ArgumentCaptor.forClass(ArticleVersionDO.class);
        verify(articleVersionDao).updateById(captor.capture());
        assertEquals(ArticleVersionStatusEnum.DRAFT.getCode(), captor.getValue().getStatus());

        // 线上表不应被修改
        verify(articleDao, never()).updateById(any());
    }

    @Test
    @DisplayName("灰度回滚 - 创建审计记录")
    void rollbackGray_createsAuditRecord() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(
                ArticleVersionDO.builder().id(5L).build());
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).reason("数据异常").build();
        grayService.rollbackGray(req);

        ArgumentCaptor<GrayRollbackAuditDO> auditCaptor = ArgumentCaptor.forClass(GrayRollbackAuditDO.class);
        verify(adminGrayReleaseDao).insertRollbackAudit(auditCaptor.capture());
        GrayRollbackAuditDO audit = auditCaptor.getValue();
        assertEquals(1L, audit.getArticleId());
        assertEquals(10L, audit.getGrayVersionId());
        assertEquals(5L, audit.getRestoredVersionId());
        assertEquals("数据异常", audit.getReason());
    }

    // ==================== 监控查询测试 ====================

    @Test
    @DisplayName("查询灰度监控 - 返回统计数据")
    void queryMonitoring_returnsMetrics() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(3).title("灰度文章")
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(adminGrayReleaseDao.countExposuresByVersionId(10L)).thenReturn(100L);
        when(adminGrayReleaseDao.countExposuresByVersionIdAndAccessType(10L, GrayAccessTypeEnum.TAG_HIT.getCode())).thenReturn(50L);
        when(adminGrayReleaseDao.countExposuresByVersionIdAndAccessType(10L, GrayAccessTypeEnum.PERCENTAGE_HIT.getCode())).thenReturn(30L);
        when(adminGrayReleaseDao.countExposuresByVersionIdAndAccessType(10L, GrayAccessTypeEnum.PREVIEW_TOKEN.getCode())).thenReturn(20L);
        when(adminGrayReleaseDao.countFeedbackByVersionId(10L)).thenReturn(5L);
        when(adminGrayReleaseDao.countFeedbackByVersionIdAndType(10L, GrayFeedbackTypeEnum.COMMENT.getCode())).thenReturn(3L);
        when(adminGrayReleaseDao.countFeedbackByVersionIdAndType(10L, GrayFeedbackTypeEnum.ERROR_REPORT.getCode())).thenReturn(2L);

        GraySegmentRuleDO rule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.PERCENTAGE.getCode())
                .ruleConfig("{\"percentage\":20}")
                .isActive(true)
                .build();
        when(adminGrayReleaseDao.selectRulesByVersionId(10L)).thenReturn(Collections.singletonList(rule));
        when(adminGrayReleaseDao.selectTokensByVersionId(10L)).thenReturn(Collections.emptyList());

        Response response = grayService.queryGrayMonitoring(10L);
        assertTrue(response.isSuccess());

        @SuppressWarnings("unchecked")
        QueryGrayMonitoringRspVO rsp = (QueryGrayMonitoringRspVO) response.getData();
        assertEquals(100L, rsp.getTotalExposures());
        assertEquals(50L, rsp.getTagHitExposures());
        assertEquals(30L, rsp.getPercentageExposures());
        assertEquals(20L, rsp.getPreviewLinkExposures());
        assertEquals(5L, rsp.getTotalFeedback());
    }

    // ==================== 定时发布冲突测试 ====================

    @Test
    @DisplayName("定时发布被灰度阻止")
    void scheduledPublish_blockedByGray() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(2)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);

        // 模拟存在活跃灰度版本
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(9L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        // 尝试定时发布
        Date futureDate = new Date(System.currentTimeMillis() + 3600000L);
        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(10L).scheduledAt(futureDate).build();

        Response response = articleService.publishVersion(req);
        assertFalse(response.isSuccess());
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
