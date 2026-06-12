package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 灰度规则类型枚举
 */
@Getter
@AllArgsConstructor
public enum GrayRuleTypeEnum {

    TAG("TAG", "按用户标签"),
    PERCENTAGE("PERCENTAGE", "按登录用户比例"),
    PREVIEW_TOKEN("PREVIEW_TOKEN", "按预览令牌");

    private final String code;
    private final String description;
}
