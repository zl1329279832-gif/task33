package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublishGrayVersionReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;

    @NotEmpty(message = "分群规则不能为空")
    @Valid
    private List<GrayRuleConfigVO> rules;
}
