package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布批次类型枚举
 */
@Getter
@AllArgsConstructor
public enum PublishBatchTypeEnum {

    GRAY("GRAY", "灰度发布"),
    FULL("FULL", "全量发布");

    private final String code;
    private final String description;
}
