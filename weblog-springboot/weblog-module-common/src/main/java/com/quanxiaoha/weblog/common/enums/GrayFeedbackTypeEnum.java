package com.quanxiaoha.weblog.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 灰度反馈类型枚举
 */
@Getter
@AllArgsConstructor
public enum GrayFeedbackTypeEnum {

    COMMENT(1, "评论"),
    ERROR_REPORT(2, "错误反馈");

    private final int code;
    private final String description;

    public static GrayFeedbackTypeEnum valueOf(int code) {
        for (GrayFeedbackTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown GrayFeedbackTypeEnum code: " + code);
    }
}
