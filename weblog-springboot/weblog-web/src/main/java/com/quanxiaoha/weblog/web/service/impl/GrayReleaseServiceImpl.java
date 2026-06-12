package com.quanxiaoha.weblog.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.enums.GrayAccessTypeEnum;
import com.quanxiaoha.weblog.common.enums.GraySegmentRuleTypeEnum;
import com.quanxiaoha.weblog.web.dao.*;
import com.quanxiaoha.weblog.web.model.vo.article.QueryArticleDetailRspVO;
import com.quanxiaoha.weblog.web.model.vo.article.QueryArticleLinkRspVO;
import com.quanxiaoha.weblog.web.model.vo.tag.QueryTagListItemRspVO;
import com.quanxiaoha.weblog.web.service.GrayReleaseService;
import com.quanxiaoha.weblog.web.utils.MarkdownUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GrayReleaseServiceImpl implements GrayReleaseService {

    @Autowired
    private GrayReleaseDao grayReleaseDao;

    @Autowired
    private ArticleDao articleDao;

    @Autowired
    private CategoryDao categoryDao;

    @Autowired
    private ArticleCategoryRelDao articleCategoryRelDao;

    @Autowired
    private TagDao tagDao;

    @Autowired
    private ArticleTagRelDao articleTagRelDao;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ArticleVersionDO resolveGrayVersion(Long articleId, String username, String previewToken) {
        // 快速路径：查询是否有活跃灰度版本
        ArticleVersionDO grayVersion = grayReleaseDao.selectActiveGrayByArticleId(articleId);
        if (grayVersion == null) {
            return null;
        }

        // 预览令牌验证
        if (previewToken != null && !previewToken.isEmpty()) {
            GrayPreviewTokenDO tokenDO = grayReleaseDao.selectValidPreviewToken(previewToken);
            if (tokenDO != null && Objects.equals(tokenDO.getArticleId(), articleId)) {
                logExposure(grayVersion, "", GrayAccessTypeEnum.PREVIEW_TOKEN.getCode(), previewToken);
                return grayVersion;
            }
            // 令牌无效或过期，继续检查其他规则
        }

        // 匿名用户无令牌，回退稳定版
        if (username == null || username.isEmpty()) {
            return null;
        }

        // 加载活跃规则
        List<GraySegmentRuleDO> rules = grayReleaseDao.selectActiveRulesByArticleId(articleId);
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // 遍历规则，任一命中即可
        for (GraySegmentRuleDO rule : rules) {
            if (rule.getRuleType() == GraySegmentRuleTypeEnum.TAG_USERS.getCode()) {
                if (matchTagRule(rule, username)) {
                    logExposure(grayVersion, username, GrayAccessTypeEnum.TAG_HIT.getCode(), "");
                    return grayVersion;
                }
            } else if (rule.getRuleType() == GraySegmentRuleTypeEnum.PERCENTAGE.getCode()) {
                if (matchPercentageRule(rule, username)) {
                    logExposure(grayVersion, username, GrayAccessTypeEnum.PERCENTAGE_HIT.getCode(), "");
                    return grayVersion;
                }
            }
            // PREVIEW_LINK 规则已在上面令牌验证时处理
        }

        return null;
    }

    @Override
    public QueryArticleDetailRspVO buildGrayDetailResponse(ArticleVersionDO grayVersion, Long articleId) {
        QueryArticleDetailRspVO vo = QueryArticleDetailRspVO.builder()
                .title(grayVersion.getTitle())
                .updateTime(grayVersion.getUpdateTime() != null ? grayVersion.getUpdateTime() : grayVersion.getCreateTime())
                .content(MarkdownUtil.parse2Html(grayVersion.getContent()))
                .build();

        // 从灰度版本读取分类
        if (grayVersion.getCategoryId() != null && grayVersion.getCategoryId() > 0) {
            CategoryDO categoryDO = categoryDao.selectByCategoryId(grayVersion.getCategoryId());
            if (categoryDO != null) {
                vo.setCategoryId(categoryDO.getId());
                vo.setCategoryName(categoryDO.getName());
            }
        }

        // 从灰度版本读取标签
        String tagIdsStr = grayVersion.getTagIds();
        if (tagIdsStr != null && !tagIdsStr.isEmpty()) {
            List<Long> tagIds = Arrays.stream(tagIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (!tagIds.isEmpty()) {
                List<TagDO> tagDOS = tagDao.selectByTagIds(tagIds);
                List<QueryTagListItemRspVO> tags = tagDOS.stream()
                        .map(t -> QueryTagListItemRspVO.builder().id(t.getId()).name(t.getName()).build())
                        .collect(Collectors.toList());
                vo.setTags(tags);
            }
        }

        // 阅读量从线上表读取（如果文章已存在）
        if (grayVersion.getArticleId() != null && grayVersion.getArticleId() > 0) {
            ArticleDO articleDO = articleDao.selectArticleById(grayVersion.getArticleId());
            if (articleDO != null) {
                vo.setReadNum(articleDO.getReadNum());
            }
        }

        // 上一篇/下一篇（从线上表查，不影响灰度内容展示）
        if (grayVersion.getArticleId() != null && grayVersion.getArticleId() > 0) {
            ArticleDO preArticle = articleDao.selectPreArticle(grayVersion.getArticleId());
            if (preArticle != null) {
                vo.setPreArticle(QueryArticleLinkRspVO.builder()
                        .id(preArticle.getId()).title(preArticle.getTitle()).build());
            }
            ArticleDO nextArticle = articleDao.selectNextArticle(grayVersion.getArticleId());
            if (nextArticle != null) {
                vo.setNextArticle(QueryArticleLinkRspVO.builder()
                        .id(nextArticle.getId()).title(nextArticle.getTitle()).build());
            }
        }

        return vo;
    }

    // ==================== 辅助方法 ====================

    private boolean matchTagRule(GraySegmentRuleDO rule, String username) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(rule.getRuleConfig(), Map.class);
            @SuppressWarnings("unchecked")
            List<Number> tagIds = (List<Number>) config.get("tagIds");
            if (tagIds == null || tagIds.isEmpty()) {
                return false;
            }

            Set<Long> ruleTagIds = tagIds.stream()
                    .map(Number::longValue)
                    .collect(Collectors.toSet());

            List<UserTagDO> userTags = grayReleaseDao.selectUserTags(username);
            return userTags.stream()
                    .anyMatch(ut -> ruleTagIds.contains(ut.getTagId()));
        } catch (Exception e) {
            log.warn("解析标签规则失败: {}", rule.getRuleConfig(), e);
            return false;
        }
    }

    private boolean matchPercentageRule(GraySegmentRuleDO rule, String username) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(rule.getRuleConfig(), Map.class);
            Number percentage = (Number) config.get("percentage");
            if (percentage == null) {
                return false;
            }

            int bucket = Math.abs(username.hashCode()) % 100;
            return bucket < percentage.intValue();
        } catch (Exception e) {
            log.warn("解析百分比规则失败: {}", rule.getRuleConfig(), e);
            return false;
        }
    }

    private void logExposure(ArticleVersionDO grayVersion, String username, int accessType, String accessToken) {
        try {
            GrayExposureLogDO logDO = GrayExposureLogDO.builder()
                    .versionId(grayVersion.getId())
                    .articleId(grayVersion.getArticleId())
                    .readerUsername(username != null ? username : "")
                    .accessType(accessType)
                    .accessToken(accessToken != null ? accessToken : "")
                    .build();
            grayReleaseDao.insertExposureLog(logDO);
        } catch (Exception e) {
            log.warn("记录曝光日志失败", e);
        }
    }
}
