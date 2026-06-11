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
 * 文章版本表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_article_version")
public class ArticleVersionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文章id，新文章首次发布前为0 */
    private Long articleId;

    /** 版本号，同一篇文章递增 */
    private Integer versionNum;

    /** 版本状态: 0=DRAFT, 1=PENDING_PUBLISH, 2=PUBLISHED */
    private Integer status;

    private String title;

    private String titleImage;

    private String description;

    /** 正文内容(markdown) */
    private String content;

    private Long categoryId;

    /** 标签id列表，逗号分隔 */
    private String tagIds;

    /** 定时发布时间，NULL表示立即发布 */
    private Date scheduledAt;

    /** 实际发布时间 */
    private Date publishedAt;

    private Date createTime;

    private Date updateTime;

    private Boolean isDeleted;
}
