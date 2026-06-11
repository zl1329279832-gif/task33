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
public class RollbackArticleVersionReqVO {

    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    @NotNull(message = "目标版本ID不能为空")
    private Long targetVersionId;
}
