package com.quanxiaoha.weblog.common.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 灰度规则表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_gray_rule")
public class GrayRuleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    /** 规则类型: TAG/PERCENTAGE/PREVIEW_TOKEN */
    private String ruleType;

    /** 规则值: 标签名CSV / 百分比0-100 / 令牌UUID */
    private String ruleValue;

    private Date createTime;
}
