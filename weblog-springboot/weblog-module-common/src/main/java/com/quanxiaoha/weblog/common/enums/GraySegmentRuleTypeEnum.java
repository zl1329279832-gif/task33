package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 灰度分群规则类型枚举
 */
@Getter
@AllArgsConstructor
public enum GraySegmentRuleTypeEnum {

    TAG_USERS(1, "标签用户"),
    PERCENTAGE(2, "登录用户比例"),
    PREVIEW_LINK(3, "预览链接");

    private final int code;
    private final String description;

    public static GraySegmentRuleTypeEnum valueOf(int code) {
        for (GraySegmentRuleTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown GraySegmentRuleTypeEnum code: " + code);
    }
}
