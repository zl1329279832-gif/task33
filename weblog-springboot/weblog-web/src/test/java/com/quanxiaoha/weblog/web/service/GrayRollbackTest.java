package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.admin.dao.*;
import com.quanxiaoha.weblog.admin.service.impl.AdminGrayReleaseServiceImpl;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.admin.model.vo.article.RollbackGrayReqVO;
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
 * 灰度回滚测试：验证回滚恢复所有字段
 */
@ExtendWith(MockitoExtension.class)
class GrayRollbackTest {

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
    @DisplayName("回滚灰度 - 保留线上正文不变")
    void rollbackGray_restoresAllFields() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L).versionNum(3)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .title("灰度新标题").content("灰度新内容").categoryId(2L).tagIds("3,4")
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(
                ArticleVersionDO.builder().id(5L).title("稳定标题").content("稳定内容").categoryId(1L).tagIds("1,2").build());
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).reason("回滚").build();
        Response response = grayService.rollbackGray(req);
        assertTrue(response.isSuccess());

        // 线上表完全不被修改
        verify(articleDao, never()).updateById(any());
        verify(articleContentDao, never()).updateByArticleId(any());
        verify(articleCategoryRelDao, never()).deleteByArticleId(any());
        verify(articleTagRelDao, never()).deleteByArticleId(any());
    }

    @Test
    @DisplayName("回滚灰度 - 描述/SEO字段保留")
    void rollbackGray_descriptionSeoPreserved() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .description("灰度描述")
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(
                ArticleVersionDO.builder().id(5L).description("稳定描述").build());
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).build();
        grayService.rollbackGray(req);

        // 线上的 description (SEO) 不应被改动
        verify(articleDao, never()).updateById(any());
    }

    @Test
    @DisplayName("回滚灰度 - 分类关系保留")
    void rollbackGray_categoryPreserved() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .categoryId(99L) // 灰度版本的分类
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(
                ArticleVersionDO.builder().id(5L).categoryId(1L).build());
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).build();
        grayService.rollbackGray(req);

        // 分类关系表不应被操作
        verify(articleCategoryRelDao, never()).deleteByArticleId(anyLong());
        verify(articleCategoryRelDao, never()).insert(any());
    }

    @Test
    @DisplayName("回滚灰度 - 标签关系保留")
    void rollbackGray_tagsPreserved() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .tagIds("10,20") // 灰度版本的标签
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(1);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(1);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(
                ArticleVersionDO.builder().id(5L).tagIds("1,2").build());
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).build();
        grayService.rollbackGray(req);

        // 标签关系表不应被操作
        verify(articleTagRelDao, never()).deleteByArticleId(anyLong());
        verify(articleTagRelDao, never()).insertBatch(any());
    }

    @Test
    @DisplayName("回滚灰度 - 预览令牌失效")
    void rollbackGray_cancelsPreviewTokens() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L).articleId(1L)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .build();
        when(articleVersionDao.selectById(10L)).thenReturn(grayVersion);
        when(articleVersionDao.updateById(any())).thenReturn(1);
        when(adminGrayReleaseDao.deactivateRulesByVersionId(10L)).thenReturn(2);
        when(adminGrayReleaseDao.softDeleteTokensByVersionId(10L)).thenReturn(3);
        when(articleVersionDao.selectLatestPublishedByArticleId(1L)).thenReturn(null);
        when(adminGrayReleaseDao.insertRollbackAudit(any())).thenReturn(1);

        RollbackGrayReqVO req = RollbackGrayReqVO.builder()
                .articleId(1L).grayVersionId(10L).build();
        grayService.rollbackGray(req);

        // 验证预览令牌被软删除
        verify(adminGrayReleaseDao).softDeleteTokensByVersionId(10L);
        // 验证规则被停用
        verify(adminGrayReleaseDao).deactivateRulesByVersionId(10L);
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
