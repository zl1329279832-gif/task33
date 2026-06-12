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
 * 回滚审计表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_rollback_audit")
public class RollbackAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long fromVersionId;

    private Long toVersionId;

    private Long batchId;

    private String operator;

    private String reason;

    private Date rollbackTime;
}
