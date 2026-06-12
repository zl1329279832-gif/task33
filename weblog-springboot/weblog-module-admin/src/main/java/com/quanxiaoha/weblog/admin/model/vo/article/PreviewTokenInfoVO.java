package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PreviewTokenInfoVO {

    private String token;
    private Date expireAt;
    private String createdBy;
    private Date createTime;
    private Boolean expired;
}
