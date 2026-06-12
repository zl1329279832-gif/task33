package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 灰度统计响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GrayStatsRspVO {

    private Long versionId;

    private Long totalExposures;

    private Long uniqueUsers;

    /** 各规则命中次数 */
    private Map<String, Long> ruleBreakdown;

    private Long batchId;
}
