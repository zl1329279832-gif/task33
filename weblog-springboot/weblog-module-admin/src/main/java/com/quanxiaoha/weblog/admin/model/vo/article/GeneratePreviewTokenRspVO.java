package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 生成预览令牌响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeneratePreviewTokenRspVO {

    private String token;

    private String previewUrl;

    private Date expireAt;
}
