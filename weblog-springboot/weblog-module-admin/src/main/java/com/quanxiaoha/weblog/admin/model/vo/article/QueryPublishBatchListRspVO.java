package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 发布批次列表响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueryPublishBatchListRspVO {

    private Long batchId;

    private Integer batchNum;

    private String batchType;

    private String status;

    private String createdBy;

    private Date createTime;

    private Date completedAt;
}
