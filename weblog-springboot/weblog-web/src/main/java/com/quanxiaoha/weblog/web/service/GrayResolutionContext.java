package com.quanxiaoha.weblog.web.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 灰度解析上下文
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GrayResolutionContext {

    /** 用户ID（匿名为null） */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 预览令牌（从请求参数/Header获取） */
    private String previewToken;
}
