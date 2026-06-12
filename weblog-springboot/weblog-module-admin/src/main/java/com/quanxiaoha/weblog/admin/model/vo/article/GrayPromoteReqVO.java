package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 灰度全量发布请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GrayPromoteReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;
}
