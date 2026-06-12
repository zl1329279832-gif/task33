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
 * 灰度版本曝光日志
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_gray_exposure_log")
public class GrayExposureLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private Long articleId;

    private String readerUsername;

    /** 访问类型: 1=TAG_HIT, 2=PERCENTAGE_HIT, 3=PREVIEW_TOKEN */
    private Integer accessType;

    private String accessToken;

    private Date createTime;
}
