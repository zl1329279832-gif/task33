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
 * 灰度预览令牌
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_gray_preview_token")
public class GrayPreviewTokenDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private Long articleId;

    private String token;

    private Date expireAt;

    private String createdBy;

    private Date createTime;

    private Boolean isDeleted;
}
