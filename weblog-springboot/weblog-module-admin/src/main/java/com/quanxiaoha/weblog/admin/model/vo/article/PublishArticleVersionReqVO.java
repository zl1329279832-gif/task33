package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublishArticleVersionReqVO {

    @NotNull(message = "版本ID不能为空")
    private Long versionId;

    /** 定时发布时间，null表示立即发布 */
    private Date scheduledAt;
}
