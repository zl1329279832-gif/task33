package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.service.impl.AdminGrayReleaseServiceImpl;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * 灰度发布与定时发布竞争冲突测试
 */
@ExtendWith(MockitoExtension.class)
class GrayScheduledPublishConflictTest {

    private AdminArticleServiceImpl articleService;
    private AdminGrayReleaseServiceImpl grayService;

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

        articleService = new AdminArticleServiceImpl(txManager);
        ReflectionTestUtils.setField(articleService, "articleDao", articleDao);
        ReflectionTestUtils.setField(articleService, "articleContentDao", articleContentDao);
        ReflectionTestUtils.setField(articleService, "articleCategoryRelDao", articleCategoryRelDao);
        ReflectionTestUtils.setField(articleService, "tagDao", tagDao);
        ReflectionTestUtils.setField(articleService, "articleTagRelDao", articleTagRelDao);
        ReflectionTestUtils.setField(articleService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(articleService, "categoryDao", categoryDao);
        ReflectionTestUtils.setField(articleService, "adminGrayReleaseDao", adminGrayReleaseDao);

        grayService = new AdminGrayReleaseServiceImpl(txManager);
        ReflectionTestUtils.setField(grayService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(grayService, "adminGrayReleaseDao", adminGrayReleaseDao);
        ReflectionTestUtils.setField(grayService, "adminUserTagDao", adminUserTagDao);
        ReflectionTestUtils.setField(grayService, "adminArticleService", articleService);
    }

    @Test
    @DisplayName("有活跃灰度版本 - 定时发布被阻止")
    void schedulePublish_blockedByActiveGray() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(3)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);

        // 存在活跃灰度版本
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(9L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        Date futureDate = new Date(System.currentTimeMillis() + 3600000L);
        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(10L).scheduledAt(futureDate).build();

        Response response = articleService.publishVersion(req);
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("灰度回滚后 - 可以定时发布")
    void schedulePublish_allowedAfterGrayRollback() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(3)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);

        // 灰度已回滚，无活跃灰度版本
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);
        when(articleVersionDao.updateById(any())).thenReturn(1);

        Date futureDate = new Date(System.currentTimeMillis() + 3600000L);
        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(10L).scheduledAt(futureDate).build();

        Response response = articleService.publishVersion(req);
        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("灰度全量发布后 - 可以定时发布")
    void schedulePublish_allowedAfterGrayFullPublish() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(4)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);

        // 灰度已全量发布，无活跃灰度版本
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);
        when(articleVersionDao.updateById(any())).thenReturn(1);

        Date futureDate = new Date(System.currentTimeMillis() + 3600000L);
        PublishArticleVersionReqVO req = PublishArticleVersionReqVO.builder()
                .versionId(10L).scheduledAt(futureDate).build();

        Response response = articleService.publishVersion(req);
        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("存在定时发布 - 灰度发布被阻止")
    void grayPublish_blockedByPendingSchedule() {
        ArticleVersionDO draft = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(3)
                .status(ArticleVersionStatusEnum.DRAFT.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(draft);
        when(adminGrayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);

        // 存在 PENDING_PUBLISH 版本
        ArticleVersionDO pending = ArticleVersionDO.builder()
                .id(8L).articleId(1L)
                .status(ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .scheduledAt(new Date(System.currentTimeMillis() + 3600000L))
                .build();
        when(articleVersionDao.selectAllByArticleId(1L)).thenReturn(Collections.singletonList(pending));

        PublishGrayVersionReqVO req = PublishGrayVersionReqVO.builder()
                .versionId(10L)
                .rules(Collections.singletonList(
                        GrayRuleConfigVO.builder()
                                .ruleType(2).percentage(20).build()))
                .build();

        Response response = grayService.publishGrayVersion(req);
        assertFalse(response.isSuccess());
    }

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
