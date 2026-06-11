package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryVersionDiffReqVO {

    @NotNull(message = "左侧版本ID不能为空")
    private Long leftVersionId;

    @NotNull(message = "右侧版本ID不能为空")
    private Long rightVersionId;
}
