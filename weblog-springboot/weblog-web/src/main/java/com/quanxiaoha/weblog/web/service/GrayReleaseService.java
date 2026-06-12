package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.web.model.vo.article.QueryArticleDetailRspVO;

/**
 * 灰度发布路由服务
 */
public interface GrayReleaseService {

    /**
     * 解析灰度版本：根据读者身份判断是否命中灰度规则
     *
     * @param articleId    文章ID
     * @param username     当前登录用户名（可为null）
     * @param previewToken 预览令牌（可为null）
     * @return 命中的灰度版本，未命中返回null
     */
    ArticleVersionDO resolveGrayVersion(Long articleId, String username, String previewToken);

    /**
     * 构建灰度版本的文章详情响应
     */
    QueryArticleDetailRspVO buildGrayDetailResponse(ArticleVersionDO grayVersion, Long articleId);
}
