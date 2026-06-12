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
 * 灰度反馈
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_gray_feedback")
public class GrayFeedbackDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private Long articleId;

    /** 反馈类型: 1=COMMENT, 2=ERROR_REPORT */
    private Integer feedbackType;

    private String content;

    private String reporter;

    private Date createTime;

    private Boolean isDeleted;
}
