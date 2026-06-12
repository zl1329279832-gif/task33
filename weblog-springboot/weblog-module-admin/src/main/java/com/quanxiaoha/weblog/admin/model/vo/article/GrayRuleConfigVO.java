package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GrayRuleConfigVO {

    @NotNull(message = "规则类型不能为空")
    private Integer ruleType;

    /** 标签用户规则：标签ID列表 (ruleType=1时使用) */
    private List<Long> tagIds;

    /** 百分比规则：百分比值 0-100 (ruleType=2时使用) */
    private Integer percentage;

    /** 预览链接过期时间 (ruleType=3时使用) */
    private Date previewExpireAt;

    /** 预览令牌生成数量 (ruleType=3时使用，默认1) */
    private Integer previewTokenCount;
}
