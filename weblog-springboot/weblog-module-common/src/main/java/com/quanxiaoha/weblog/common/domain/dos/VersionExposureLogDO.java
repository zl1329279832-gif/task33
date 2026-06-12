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
 * 版本曝光日志表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_version_exposure_log")
public class VersionExposureLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private Long batchId;

    private Long articleId;

    /** 用户id(匿名为NULL) */
    private Long userId;

    /** 命中的规则描述 */
    private String matchedRule;

    private Date exposedAt;
}
