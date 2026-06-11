package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ArticleVersionStatusEnum {
    DRAFT(0, "草稿"),
    PENDING_PUBLISH(1, "待发布"),
    PUBLISHED(2, "已发布"),
    ROLLBACK(3, "已回滚");

    private final int code;
    private final String description;
}
