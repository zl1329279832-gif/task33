package com.quanxiaoha.weblog.common.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_article_version")
public class ArticleVersionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Integer versionNum;
    private Integer status;
    private String title;
    private String titleImage;
    private String description;
    private String content;
    private Long categoryId;
    private String tagIds;
    private Date scheduledAt;
    private Date publishedAt;
    private Date createTime;
    private Date updateTime;
    private Boolean isDeleted;
}
