package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 灰度回滚请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GrayRollbackReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;

    private String reason;
}
