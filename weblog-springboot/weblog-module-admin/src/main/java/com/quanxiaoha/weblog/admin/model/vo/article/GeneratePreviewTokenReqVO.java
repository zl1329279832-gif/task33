package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 生成预览令牌请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeneratePreviewTokenReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;

    /** 过期小时数，默认24 */
    private Integer expireHours;
}
