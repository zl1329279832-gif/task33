package com.quanxiaoha.weblog.web.service;

/**
 * 灰度解析服务
 */
public interface GrayResolutionService {

    GrayResolutionResult resolve(Long articleId, GrayResolutionContext ctx);

    void logExposure(GrayResolutionResult result, Long articleId, Long userId);
}
