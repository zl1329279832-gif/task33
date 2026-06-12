package com.quanxiaoha.weblog.common.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 发布批次表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_publish_batch")
public class PublishBatchDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private Integer batchNum;

    /** 批次类型: GRAY/FULL */
    private String batchType;

    /** 状态: ACTIVE/COMPLETED/ROLLED_BACK */
    private String status;

    private String createdBy;

    private Date createTime;

    private Date completedAt;
}
