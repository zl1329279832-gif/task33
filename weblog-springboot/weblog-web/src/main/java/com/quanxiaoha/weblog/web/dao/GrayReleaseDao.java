package com.quanxiaoha.weblog.web.dao;

import com.quanxiaoha.weblog.common.domain.dos.*;

import java.util.List;

/**
 * 灰度发布读端 DAO
 */
public interface GrayReleaseDao {

    /**
     * 查询文章的活跃灰度版本
     */
    ArticleVersionDO selectActiveGrayByArticleId(Long articleId);

    /**
     * 查询文章的活跃分群规则
     */
    List<GraySegmentRuleDO> selectActiveRulesByArticleId(Long articleId);

    /**
     * 查询有效的预览令牌（未过期且未删除）
     */
    GrayPreviewTokenDO selectValidPreviewToken(String token);

    /**
     * 查询用户的标签关联
     */
    List<UserTagDO> selectUserTags(String username);

    /**
     * 插入曝光日志
     */
    int insertExposureLog(GrayExposureLogDO log);
}
