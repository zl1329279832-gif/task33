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
 * 灰度回滚审计
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_gray_rollback_audit")
public class GrayRollbackAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long grayVersionId;

    private Long restoredVersionId;

    private String reason;

    private String operator;

    private Date createTime;
}
