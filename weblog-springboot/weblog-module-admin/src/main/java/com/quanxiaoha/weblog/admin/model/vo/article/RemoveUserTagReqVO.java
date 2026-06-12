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
public class RemoveUserTagReqVO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotNull(message = "标签ID不能为空")
    private Long tagId;
}
