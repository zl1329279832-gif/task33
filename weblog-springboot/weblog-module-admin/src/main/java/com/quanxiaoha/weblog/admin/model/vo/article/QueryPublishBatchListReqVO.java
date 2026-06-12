package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 查询发布批次列表请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueryPublishBatchListReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;
}
