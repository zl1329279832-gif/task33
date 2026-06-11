package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryVersionListRspVO {
    private Long id;
    private Long articleId;
    private Integer versionNum;
    private Integer status;
    private String title;
    private String description;
    private Long categoryId;
    private String tagIds;
    private Date scheduledAt;
    private Date publishedAt;
    private Date createTime;
}
