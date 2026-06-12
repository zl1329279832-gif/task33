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
 * 灰度分群规则
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_gray_segment_rule")
public class GraySegmentRuleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private Long articleId;

    /** 规则类型: 1=TAG_USERS, 2=PERCENTAGE, 3=PREVIEW_LINK */
    private Integer ruleType;

    /** JSON配置 */
    private String ruleConfig;

    private Boolean isActive;

    private Date createTime;

    private Date updateTime;

    private Boolean isDeleted;
}
