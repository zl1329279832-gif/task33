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
 * 用户标签表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_user_tag")
public class UserTagDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tagLabel;

    private Date createTime;
}
