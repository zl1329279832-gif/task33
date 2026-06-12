package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.GrayRuleTypeEnum;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.service.impl.GrayResolutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 灰度解析服务测试
 * 验证: TAG/PERCENTAGE/PREVIEW_TOKEN 命中/未命中，令牌过期，匿名回退，OR逻辑，曝光记录
 */
@ExtendWith(MockitoExtension.class)
class GrayReadResolutionTest {

    private GrayResolutionServiceImpl grayResolutionService;

    @Mock private ArticleVersionDao articleVersionDao;
    @Mock private GrayRuleDao grayRuleDao;
    @Mock private PreviewTokenDao previewTokenDao;
    @Mock private UserTagDao userTagDao;
    @Mock private VersionExposureLogDao exposureLogDao;

    private ArticleVersionDO grayVersion;

    @BeforeEach
    void setUp() {
        grayResolutionService = new GrayResolutionServiceImpl();
        ReflectionTestUtils.setField(grayResolutionService, "articleVersionDao", articleVersionDao);
        ReflectionTestUtils.setField(grayResolutionService, "grayRuleDao", grayRuleDao);
        ReflectionTestUtils.setField(grayResolutionService, "previewTokenDao", previewTokenDao);
        ReflectionTestUtils.setField(grayResolutionService, "userTagDao", userTagDao);
        ReflectionTestUtils.setField(grayResolutionService, "exposureLogDao", exposureLogDao);

        grayVersion = ArticleVersionDO.builder()
                .id(100L).articleId(10L).versionNum(2)
                .title("灰度标题").content("灰度内容")
                .build();
    }

    // ==================== 无灰度版本 ====================

    @Test
    @DisplayName("无活跃灰度版本 - 返回 miss")
    void resolve_NoGrayVersion_ReturnsMiss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(null);

        GrayResolutionContext ctx = GrayResolutionContext.builder().userId(1L).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
        assertNull(result.getGrayVersion());
    }

    // ==================== PREVIEW_TOKEN ====================

    @Test
    @DisplayName("预览令牌命中 - 有效令牌")
    void resolve_PreviewToken_ValidToken_Hit() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        PreviewTokenDO token = PreviewTokenDO.builder()
                .versionId(100L).token("abc123").isUsed(false)
                .expireAt(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        when(previewTokenDao.selectByToken("abc123")).thenReturn(token);

        GrayResolutionContext ctx = GrayResolutionContext.builder()
                .previewToken("abc123").build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertTrue(result.isGrayHit());
        assertEquals("PREVIEW_TOKEN", result.getMatchedRule());
        assertEquals(grayVersion, result.getGrayVersion());
    }

    @Test
    @DisplayName("预览令牌过期 - 不命中")
    void resolve_PreviewToken_Expired_Miss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        PreviewTokenDO token = PreviewTokenDO.builder()
                .versionId(100L).token("expired123").isUsed(false)
                .expireAt(new Date(System.currentTimeMillis() - 3600_000)) // 已过期
                .build();
        when(previewTokenDao.selectByToken("expired123")).thenReturn(token);

        GrayResolutionContext ctx = GrayResolutionContext.builder()
                .previewToken("expired123").build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
    }

    @Test
    @DisplayName("预览令牌已撤销 - 不命中")
    void resolve_PreviewToken_Revoked_Miss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        PreviewTokenDO token = PreviewTokenDO.builder()
                .versionId(100L).token("revoked123").isUsed(true) // 已撤销
                .expireAt(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        when(previewTokenDao.selectByToken("revoked123")).thenReturn(token);

        GrayResolutionContext ctx = GrayResolutionContext.builder()
                .previewToken("revoked123").build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
    }

    @Test
    @DisplayName("预览令牌版本不匹配 - 不命中")
    void resolve_PreviewToken_WrongVersion_Miss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        PreviewTokenDO token = PreviewTokenDO.builder()
                .versionId(999L).token("wrong123").isUsed(false) // 版本不匹配
                .expireAt(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        when(previewTokenDao.selectByToken("wrong123")).thenReturn(token);

        GrayResolutionContext ctx = GrayResolutionContext.builder()
                .previewToken("wrong123").build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
    }

    // ==================== TAG ====================

    @Test
    @DisplayName("TAG规则命中 - 用户标签匹配")
    void resolve_TagRule_Hit() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        GrayRuleDO tagRule = GrayRuleDO.builder()
                .ruleType(GrayRuleTypeEnum.TAG.getCode()).ruleValue("vip,beta").build();
        when(grayRuleDao.selectByVersionId(100L)).thenReturn(Arrays.asList(tagRule));

        UserTagDO userTag = UserTagDO.builder().userId(1L).tagLabel("vip").build();
        when(userTagDao.selectByUserId(1L)).thenReturn(Arrays.asList(userTag));

        GrayResolutionContext ctx = GrayResolutionContext.builder().userId(1L).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertTrue(result.isGrayHit());
        assertEquals("TAG:vip", result.getMatchedRule());
    }

    @Test
    @DisplayName("TAG规则未命中 - 用户标签不匹配")
    void resolve_TagRule_Miss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        GrayRuleDO tagRule = GrayRuleDO.builder()
                .ruleType(GrayRuleTypeEnum.TAG.getCode()).ruleValue("vip,beta").build();
        when(grayRuleDao.selectByVersionId(100L)).thenReturn(Arrays.asList(tagRule));

        UserTagDO userTag = UserTagDO.builder().userId(1L).tagLabel("normal").build();
        when(userTagDao.selectByUserId(1L)).thenReturn(Arrays.asList(userTag));

        GrayResolutionContext ctx = GrayResolutionContext.builder().userId(1L).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
    }

    // ==================== PERCENTAGE ====================

    @Test
    @DisplayName("PERCENTAGE规则命中 - 用户ID哈希在范围内")
    void resolve_PercentageRule_Hit() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        // 100% 规则，所有用户命中
        GrayRuleDO percentageRule = GrayRuleDO.builder()
                .ruleType(GrayRuleTypeEnum.PERCENTAGE.getCode()).ruleValue("100").build();
        when(grayRuleDao.selectByVersionId(100L)).thenReturn(Arrays.asList(percentageRule));

        GrayResolutionContext ctx = GrayResolutionContext.builder().userId(42L).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertTrue(result.isGrayHit());
        assertTrue(result.getMatchedRule().startsWith("PERCENTAGE:"));
    }

    @Test
    @DisplayName("PERCENTAGE规则未命中 - 0%范围")
    void resolve_PercentageRule_ZeroPercent_Miss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        // 0% 规则，无用户命中
        GrayRuleDO percentageRule = GrayRuleDO.builder()
                .ruleType(GrayRuleTypeEnum.PERCENTAGE.getCode()).ruleValue("0").build();
        when(grayRuleDao.selectByVersionId(100L)).thenReturn(Arrays.asList(percentageRule));

        GrayResolutionContext ctx = GrayResolutionContext.builder().userId(42L).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
    }

    // ==================== OR 逻辑 ====================

    @Test
    @DisplayName("OR逻辑 - TAG未命中但PERCENTAGE命中")
    void resolve_OrLogic_TagMissPercentageHit() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        GrayRuleDO tagRule = GrayRuleDO.builder()
                .ruleType(GrayRuleTypeEnum.TAG.getCode()).ruleValue("vip").build();
        GrayRuleDO percentageRule = GrayRuleDO.builder()
                .ruleType(GrayRuleTypeEnum.PERCENTAGE.getCode()).ruleValue("100").build();
        when(grayRuleDao.selectByVersionId(100L)).thenReturn(Arrays.asList(tagRule, percentageRule));

        // 用户没有 vip 标签
        UserTagDO userTag = UserTagDO.builder().userId(1L).tagLabel("normal").build();
        when(userTagDao.selectByUserId(1L)).thenReturn(Arrays.asList(userTag));

        GrayResolutionContext ctx = GrayResolutionContext.builder().userId(1L).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        // TAG 未命中，但 PERCENTAGE 100% 命中
        assertTrue(result.isGrayHit());
        assertTrue(result.getMatchedRule().startsWith("PERCENTAGE:"));
    }

    // ==================== 匿名用户 ====================

    @Test
    @DisplayName("匿名用户 - 无预览令牌则返回 miss")
    void resolve_AnonymousUser_NoToken_Miss() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        GrayResolutionContext ctx = GrayResolutionContext.builder()
                .userId(null).previewToken(null).build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertFalse(result.isGrayHit());
    }

    @Test
    @DisplayName("匿名用户 - 有效预览令牌仍命中")
    void resolve_AnonymousUser_WithValidToken_Hit() {
        when(articleVersionDao.selectActiveGrayByArticleId(10L)).thenReturn(grayVersion);

        PreviewTokenDO token = PreviewTokenDO.builder()
                .versionId(100L).token("anon_token").isUsed(false)
                .expireAt(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        when(previewTokenDao.selectByToken("anon_token")).thenReturn(token);

        GrayResolutionContext ctx = GrayResolutionContext.builder()
                .userId(null).previewToken("anon_token").build();
        GrayResolutionResult result = grayResolutionService.resolve(10L, ctx);

        assertTrue(result.isGrayHit());
        assertEquals("PREVIEW_TOKEN", result.getMatchedRule());
    }

    // ==================== 曝光日志 ====================

    @Test
    @DisplayName("曝光日志 - 命中时记录")
    void logExposure_RecordsOnHit() {
        GrayResolutionResult hitResult = GrayResolutionResult.builder()
                .grayHit(true).grayVersion(grayVersion)
                .matchedRule("TAG:vip").batchId(100L).build();

        grayResolutionService.logExposure(hitResult, 10L, 1L);

        verify(exposureLogDao).insert(argThat(log -> {
            VersionExposureLogDO l = (VersionExposureLogDO) log;
            return l.getVersionId().equals(100L)
                    && l.getArticleId().equals(10L)
                    && l.getUserId().equals(1L)
                    && "TAG:vip".equals(l.getMatchedRule());
        }));
    }

    @Test
    @DisplayName("曝光日志 - 未命中不记录")
    void logExposure_SkipsOnMiss() {
        GrayResolutionResult missResult = GrayResolutionResult.miss();

        grayResolutionService.logExposure(missResult, 10L, 1L);

        verify(exposureLogDao, never()).insert(any());
    }

    @Test
    @DisplayName("曝光日志 - 写入异常不影响读取")
    void logExposure_ExceptionDoesNotBreak() {
        GrayResolutionResult hitResult = GrayResolutionResult.builder()
                .grayHit(true).grayVersion(grayVersion)
                .matchedRule("TAG:vip").build();

        doThrow(new RuntimeException("DB error")).when(exposureLogDao).insert(any());

        // 不应抛出异常
        assertDoesNotThrow(() -> grayResolutionService.logExposure(hitResult, 10L, 1L));
    }
}
