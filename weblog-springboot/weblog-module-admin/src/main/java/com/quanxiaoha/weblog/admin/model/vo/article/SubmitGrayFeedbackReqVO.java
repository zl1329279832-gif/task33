package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitGrayFeedbackReqVO {

    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    @NotNull(message = "反馈类型不能为空")
    private Integer feedbackType;

    @NotBlank(message = "反馈内容不能为空")
    private String content;
}
