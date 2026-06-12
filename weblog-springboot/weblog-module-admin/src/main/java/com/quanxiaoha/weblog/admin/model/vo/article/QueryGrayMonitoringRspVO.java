package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryGrayMonitoringRspVO {

    private Long versionId;
    private Integer versionNum;
    private String title;

    /** 曝光统计 */
    private Long totalExposures;
    private Long tagHitExposures;
    private Long percentageExposures;
    private Long previewLinkExposures;

    /** 反馈统计 */
    private Long totalFeedback;
    private Long commentCount;
    private Long errorReportCount;

    /** 当前活跃规则 */
    private List<GrayRuleConfigVO> activeRules;

    /** 预览令牌列表 */
    private List<PreviewTokenInfoVO> previewTokens;
}
