package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 灰度规则项
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GrayRuleItemVO {

    /** 规则类型: TAG/PERCENTAGE/PREVIEW_TOKEN */
    private String ruleType;

    /** 规则值: 标签名CSV / 百分比0-100 / 令牌UUID */
    private String ruleValue;
}
