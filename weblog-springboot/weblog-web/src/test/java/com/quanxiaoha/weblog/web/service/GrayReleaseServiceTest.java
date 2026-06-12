package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.common.enums.GrayAccessTypeEnum;
import com.quanxiaoha.weblog.common.enums.GraySegmentRuleTypeEnum;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.model.vo.article.QueryArticleDetailRspVO;
import com.quanxiaoha.weblog.web.model.vo.tag.QueryTagListItemRspVO;
import com.quanxiaoha.weblog.web.service.impl.GrayReleaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GrayReleaseService 灰度路由单元测试
 */
@ExtendWith(MockitoExtension.class)
class GrayReleaseServiceTest {

    private GrayReleaseServiceImpl grayReleaseService;

    @Mock private GrayReleaseDao grayReleaseDao;
    @Mock private ArticleDao articleDao;
    @Mock private CategoryDao categoryDao;
    @Mock private ArticleCategoryRelDao articleCategoryRelDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagRelDao articleTagRelDao;

    @BeforeEach
    void setUp() {
        grayReleaseService = new GrayReleaseServiceImpl();
        ReflectionTestUtils.setField(grayReleaseService, "grayReleaseDao", grayReleaseDao);
        ReflectionTestUtils.setField(grayReleaseService, "articleDao", articleDao);
        ReflectionTestUtils.setField(grayReleaseService, "categoryDao", categoryDao);
        ReflectionTestUtils.setField(grayReleaseService, "articleCategoryRelDao", articleCategoryRelDao);
        ReflectionTestUtils.setField(grayReleaseService, "tagDao", tagDao);
        ReflectionTestUtils.setField(grayReleaseService, "articleTagRelDao", articleTagRelDao);
    }

    // ==================== 灰度命中测试 ====================

    @Test
    @DisplayName("无灰度版本 - 回退到稳定版本")
    void resolveGray_noGrayVersion_returnsNull() {
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(null);

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, "testuser", null);
        assertNull(result);
    }

    @Test
    @DisplayName("标签命中 - 返回灰度版本")
    void resolveGray_tagHit_returnsGrayVersion() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        GraySegmentRuleDO rule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.TAG_USERS.getCode())
                .ruleConfig("{\"tagIds\":[1,2,3]}")
                .isActive(true)
                .build();
        when(grayReleaseDao.selectActiveRulesByArticleId(1L)).thenReturn(Collections.singletonList(rule));
        when(grayReleaseDao.selectUserTags("tagUser")).thenReturn(
                Collections.singletonList(UserTagDO.builder().username("tagUser").tagId(1L).build()));

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, "tagUser", null);

        assertNotNull(result);
        assertEquals(grayVersion.getId(), result.getId());

        // 验证曝光日志记录
        ArgumentCaptor<GrayExposureLogDO> logCaptor = ArgumentCaptor.forClass(GrayExposureLogDO.class);
        verify(grayReleaseDao).insertExposureLog(logCaptor.capture());
        assertEquals(GrayAccessTypeEnum.TAG_HIT.getCode(), logCaptor.getValue().getAccessType());
    }

    @Test
    @DisplayName("标签未命中 - 回退稳定版本")
    void resolveGray_tagMiss_returnsNull() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        GraySegmentRuleDO rule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.TAG_USERS.getCode())
                .ruleConfig("{\"tagIds\":[1,2]}")
                .isActive(true)
                .build();
        when(grayReleaseDao.selectActiveRulesByArticleId(1L)).thenReturn(Collections.singletonList(rule));
        when(grayReleaseDao.selectUserTags("missUser")).thenReturn(
                Collections.singletonList(UserTagDO.builder().username("missUser").tagId(5L).build()));

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, "missUser", null);
        assertNull(result);
    }

    @Test
    @DisplayName("百分比命中 - 返回灰度版本")
    void resolveGray_percentageHit_returnsGrayVersion() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        // 使用100%确保任何用户名都命中
        GraySegmentRuleDO rule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.PERCENTAGE.getCode())
                .ruleConfig("{\"percentage\":100}")
                .isActive(true)
                .build();
        when(grayReleaseDao.selectActiveRulesByArticleId(1L)).thenReturn(Collections.singletonList(rule));

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, "anyUser", null);

        assertNotNull(result);
        ArgumentCaptor<GrayExposureLogDO> logCaptor = ArgumentCaptor.forClass(GrayExposureLogDO.class);
        verify(grayReleaseDao).insertExposureLog(logCaptor.capture());
        assertEquals(GrayAccessTypeEnum.PERCENTAGE_HIT.getCode(), logCaptor.getValue().getAccessType());
    }

    @Test
    @DisplayName("百分比未命中 - 回退稳定版本")
    void resolveGray_percentageMiss_returnsNull() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        // 使用0%确保任何用户名都不命中
        GraySegmentRuleDO rule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.PERCENTAGE.getCode())
                .ruleConfig("{\"percentage\":0}")
                .isActive(true)
                .build();
        when(grayReleaseDao.selectActiveRulesByArticleId(1L)).thenReturn(Collections.singletonList(rule));

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, "anyUser", null);
        assertNull(result);
    }

    @Test
    @DisplayName("有效预览令牌 - 返回灰度版本")
    void resolveGray_validPreviewToken_returnsGray() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        GrayPreviewTokenDO tokenDO = GrayPreviewTokenDO.builder()
                .articleId(1L)
                .token("valid-token-123")
                .expireAt(new Date(System.currentTimeMillis() + 86400000))
                .build();
        when(grayReleaseDao.selectValidPreviewToken("valid-token-123")).thenReturn(tokenDO);

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, null, "valid-token-123");

        assertNotNull(result);
        ArgumentCaptor<GrayExposureLogDO> logCaptor = ArgumentCaptor.forClass(GrayExposureLogDO.class);
        verify(grayReleaseDao).insertExposureLog(logCaptor.capture());
        assertEquals(GrayAccessTypeEnum.PREVIEW_TOKEN.getCode(), logCaptor.getValue().getAccessType());
    }

    @Test
    @DisplayName("过期预览令牌 - 拒绝访问")
    void resolveGray_expiredPreviewToken_returnsNull() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        // selectValidPreviewToken 返回 null 表示令牌过期或不存在
        when(grayReleaseDao.selectValidPreviewToken("expired-token")).thenReturn(null);

        // 匿名用户 + 过期令牌 → null
        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, null, "expired-token");
        assertNull(result);
    }

    @Test
    @DisplayName("匿名用户无令牌 - 回退稳定版本")
    void resolveGray_anonymousNoToken_returnsNull() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, null, null);
        assertNull(result);
    }

    @Test
    @DisplayName("多规则任一命中 - TAG未命中但PERCENTAGE命中")
    void resolveGray_multipleRules_anyMatch() {
        ArticleVersionDO grayVersion = buildGrayVersion(1L, 10L, 3);
        when(grayReleaseDao.selectActiveGrayByArticleId(1L)).thenReturn(grayVersion);

        GraySegmentRuleDO tagRule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.TAG_USERS.getCode())
                .ruleConfig("{\"tagIds\":[1,2]}")
                .isActive(true)
                .build();
        GraySegmentRuleDO pctRule = GraySegmentRuleDO.builder()
                .ruleType(GraySegmentRuleTypeEnum.PERCENTAGE.getCode())
                .ruleConfig("{\"percentage\":100}")
                .isActive(true)
                .build();
        when(grayReleaseDao.selectActiveRulesByArticleId(1L)).thenReturn(Arrays.asList(tagRule, pctRule));
        when(grayReleaseDao.selectUserTags("multiUser")).thenReturn(
                Collections.singletonList(UserTagDO.builder().username("multiUser").tagId(99L).build()));

        ArticleVersionDO result = grayReleaseService.resolveGrayVersion(1L, "multiUser", null);
        assertNotNull(result, "TAG未命中但PERCENTAGE应命中");
    }

    @Test
    @DisplayName("灰度详情构建 - Markdown转HTML")
    void buildGrayDetailResponse_convertsMarkdown() {
        ArticleVersionDO grayVersion = ArticleVersionDO.builder()
                .id(10L)
                .articleId(1L)
                .versionNum(3)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .title("灰度标题")
                .content("# 标题\n正文内容")
                .categoryId(1L)
                .tagIds("1,2")
                .createTime(new Date())
                .build();

        CategoryDO category = CategoryDO.builder().id(1L).name("技术").build();
        when(categoryDao.selectByCategoryId(1L)).thenReturn(category);

        TagDO tag1 = TagDO.builder().id(1L).name("Java").build();
        TagDO tag2 = TagDO.builder().id(2L).name("Spring").build();
        when(tagDao.selectByTagIds(anyList())).thenReturn(Arrays.asList(tag1, tag2));

        ArticleDO articleDO = ArticleDO.builder().id(1L).readNum(100L).build();
        when(articleDao.selectArticleById(1L)).thenReturn(articleDO);
        when(articleDao.selectPreArticle(1L)).thenReturn(null);
        when(articleDao.selectNextArticle(1L)).thenReturn(null);

        QueryArticleDetailRspVO vo = grayReleaseService.buildGrayDetailResponse(grayVersion, 1L);

        assertEquals("灰度标题", vo.getTitle());
        assertNotNull(vo.getContent());
        assertTrue(vo.getContent().contains("标题"));
        assertEquals(1L, vo.getCategoryId());
        assertEquals("技术", vo.getCategoryName());
        assertEquals(2, vo.getTags().size());
        assertEquals("Java", vo.getTags().get(0).getName());
    }

    // ==================== 辅助方法 ====================

    private ArticleVersionDO buildGrayVersion(Long articleId, Long versionId, int versionNum) {
        return ArticleVersionDO.builder()
                .id(versionId)
                .articleId(articleId)
                .versionNum(versionNum)
                .status(ArticleVersionStatusEnum.GRAY.getCode())
                .title("灰度标题")
                .content("灰度内容")
                .categoryId(1L)
                .tagIds("1,2")
                .createTime(new Date())
                .build();
    }
}
