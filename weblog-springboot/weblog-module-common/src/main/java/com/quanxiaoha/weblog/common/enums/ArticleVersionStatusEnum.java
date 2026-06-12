package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文章版本状态枚举
 */
@Getter
@AllArgsConstructor
public enum ArticleVersionStatusEnum {

    DRAFT(0, "草稿"),
    PENDING_PUBLISH(1, "待发布"),
    PUBLISHED(2, "已发布"),
    GRAY(3, "灰度");

    private final int code;
    private final String description;

    public static ArticleVersionStatusEnum valueOf(int code) {
        for (ArticleVersionStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ArticleVersionStatusEnum code: " + code);
    }
}
