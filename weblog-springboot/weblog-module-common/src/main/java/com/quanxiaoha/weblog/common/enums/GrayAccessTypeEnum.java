package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 灰度访问命中类型枚举
 */
@Getter
@AllArgsConstructor
public enum GrayAccessTypeEnum {

    TAG_HIT(1, "标签命中"),
    PERCENTAGE_HIT(2, "比例命中"),
    PREVIEW_TOKEN(3, "预览令牌");

    private final int code;
    private final String description;

    public static GrayAccessTypeEnum valueOf(int code) {
        for (GrayAccessTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown GrayAccessTypeEnum code: " + code);
    }
}
