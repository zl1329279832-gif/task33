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
public class QueryArticleVersionListRspVO {

    private Long versionId;
    private Integer versionNum;
    private String status;
    private String title;
    private Date createTime;
    private Date publishedAt;
    private Date scheduledAt;
}
