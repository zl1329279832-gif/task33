package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryVersionDiffRspVO {

    private Long leftVersionId;
    private Integer leftVersionNum;
    private Long rightVersionId;
    private Integer rightVersionNum;

    private boolean titleChanged;
    private String leftTitle;
    private String rightTitle;

    private boolean contentChanged;
    /** 内容差异（行级 unified diff） */
    private String contentDiff;

    private boolean categoryChanged;
    private Long leftCategoryId;
    private Long rightCategoryId;
    private String leftCategoryName;
    private String rightCategoryName;

    private boolean tagsChanged;
    private List<Long> leftTagIds;
    private List<Long> rightTagIds;
    private List<String> addedTagNames;
    private List<String> removedTagNames;

    private boolean descriptionChanged;
    private boolean titleImageChanged;
}
