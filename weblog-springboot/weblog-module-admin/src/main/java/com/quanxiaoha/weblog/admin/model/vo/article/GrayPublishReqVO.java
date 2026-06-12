package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 灰度发布请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GrayPublishReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;

    private List<GrayRuleItemVO> rules;
}
