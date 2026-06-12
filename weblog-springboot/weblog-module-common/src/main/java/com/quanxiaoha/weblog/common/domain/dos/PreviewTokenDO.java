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
 * 预览令牌表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_preview_token")
public class PreviewTokenDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    /** 预览令牌(UUID) */
    private String token;

    /** 过期时间 */
    private Date expireAt;

    private Date createTime;

    /** 是否已撤销: false=活跃, true=已撤销 */
    private Boolean isUsed;
}
