package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布批次状态枚举
 */
@Getter
@AllArgsConstructor
public enum PublishBatchStatusEnum {

    ACTIVE("ACTIVE", "进行中"),
    COMPLETED("COMPLETED", "已完成"),
    ROLLED_BACK("ROLLED_BACK", "已回滚");

    private final String code;
    private final String description;
}
