package com.quanxiaoha.weblog.web.service.impl;

import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.GrayRuleTypeEnum;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.service.GrayResolutionContext;
import com.quanxiaoha.weblog.web.service.GrayResolutionResult;
import com.quanxiaoha.weblog.web.service.GrayResolutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 灰度解析服务实现
 */
@Service
@Slf4j
public class GrayResolutionServiceImpl implements GrayResolutionService {

    @Autowired
    private ArticleVersionDao articleVersionDao;
    @Autowired
    private GrayRuleDao grayRuleDao;
    @Autowired
    private PreviewTokenDao previewTokenDao;
    @Autowired
    private UserTagDao userTagDao;
    @Autowired
    private VersionExposureLogDao exposureLogDao;

    @Override
    public GrayResolutionResult resolve(Long articleId, GrayResolutionContext ctx) {
        // 快速路径：查找该文章是否有活跃灰度版本
        ArticleVersionDO grayVersion = articleVersionDao.selectActiveGrayByArticleId(articleId);
        if (grayVersion == null) {
            return GrayResolutionResult.miss();
        }

        // Step 1: 预览令牌检查（最高优先级）
        if (ctx.getPreviewToken() != null && !ctx.getPreviewToken().isEmpty()) {
            PreviewTokenDO tokenDO = previewTokenDao.selectByToken(ctx.getPreviewToken());
            if (tokenDO != null
                    && tokenDO.getVersionId().equals(grayVersion.getId())
                    && !tokenDO.getIsUsed()
                    && tokenDO.getExpireAt().after(new Date())) {
                return GrayResolutionResult.builder()
                        .grayHit(true)
                        .grayVersion(grayVersion)
                        .matchedRule("PREVIEW_TOKEN")
                        .build();
            }
        }

        // Step 2: 登录用户检查灰度规则（OR逻辑）
        if (ctx.getUserId() != null) {
            List<GrayRuleDO> rules = grayRuleDao.selectByVersionId(grayVersion.getId());
            for (GrayRuleDO rule : rules) {
                if (GrayRuleTypeEnum.TAG.getCode().equals(rule.getRuleType())) {
                    // TAG规则：检查用户标签是否匹配
                    String matchedLabel = matchTagRule(ctx.getUserId(), rule.getRuleValue());
                    if (matchedLabel != null) {
                        return GrayResolutionResult.builder()
                                .grayHit(true)
                                .grayVersion(grayVersion)
                                .matchedRule("TAG:" + matchedLabel)
                                .build();
                    }
                } else if (GrayRuleTypeEnum.PERCENTAGE.getCode().equals(rule.getRuleType())) {
                    // PERCENTAGE规则：按用户ID哈希取模
                    if (matchPercentageRule(ctx.getUserId(), rule.getRuleValue())) {
                        return GrayResolutionResult.builder()
                                .grayHit(true)
                                .grayVersion(grayVersion)
                                .matchedRule("PERCENTAGE:" + rule.getRuleValue())
                                .build();
                    }
                }
                // PREVIEW_TOKEN类型已在 Step 1 处理
            }
        }

        // Step 3: 无命中，返回 miss
        return GrayResolutionResult.miss();
    }

    @Override
    public void logExposure(GrayResolutionResult result, Long articleId, Long userId) {
        if (!result.isGrayHit() || result.getGrayVersion() == null) {
            return;
        }
        try {
            VersionExposureLogDO log = VersionExposureLogDO.builder()
                    .versionId(result.getGrayVersion().getId())
                    .batchId(result.getBatchId())
                    .articleId(articleId)
                    .userId(userId)
                    .matchedRule(result.getMatchedRule())
                    .exposedAt(new Date())
                    .build();
            exposureLogDao.insert(log);
        } catch (Exception e) {
            // 曝光日志写入失败不影响读取
            log.warn("写入曝光日志失败: articleId={}, versionId={}", articleId,
                    result.getGrayVersion().getId(), e);
        }
    }

    /**
     * 检查用户标签是否匹配TAG规则
     * @return 匹配的标签名，未匹配返回null
     */
    private String matchTagRule(Long userId, String ruleValue) {
        if (ruleValue == null || ruleValue.isEmpty()) {
            return null;
        }
        Set<String> ruleLabels = Arrays.stream(ruleValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        List<UserTagDO> userTags = userTagDao.selectByUserId(userId);
        for (UserTagDO userTag : userTags) {
            if (ruleLabels.contains(userTag.getTagLabel())) {
                return userTag.getTagLabel();
            }
        }
        return null;
    }

    /**
     * 检查用户ID是否命中百分比规则
     */
    private boolean matchPercentageRule(Long userId, String ruleValue) {
        try {
            int percentage = Integer.parseInt(ruleValue.trim());
            int hash = Math.abs(userId.hashCode()) % 100;
            return hash < percentage;
        } catch (NumberFormatException e) {
            log.warn("百分比规则值解析失败: {}", ruleValue);
            return false;
        }
    }
}
