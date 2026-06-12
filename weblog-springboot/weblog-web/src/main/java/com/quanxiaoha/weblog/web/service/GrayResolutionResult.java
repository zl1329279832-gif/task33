package com.quanxiaoha.weblog.web.service;

import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 灰度解析结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GrayResolutionResult {

    private boolean grayHit;

    private ArticleVersionDO grayVersion;

    private String matchedRule;

    private Long batchId;

    public static GrayResolutionResult miss() {
        return GrayResolutionResult.builder().grayHit(false).build();
    }
}
