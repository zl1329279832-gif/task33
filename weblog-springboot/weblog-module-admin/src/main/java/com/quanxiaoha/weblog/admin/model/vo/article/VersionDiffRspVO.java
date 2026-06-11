package com.quanxiaoha.weblog.admin.model.vo.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VersionDiffRspVO {
    private String oldTitle;
    private String newTitle;
    private String oldContent;
    private String newContent;
    private String oldDescription;
    private String newDescription;
    private Long oldCategoryId;
    private Long newCategoryId;
    private String oldTagIds;
    private String newTagIds;
    private boolean titleChanged;
    private boolean contentChanged;
    private boolean descriptionChanged;
    private boolean categoryChanged;
    private boolean tagsChanged;
}
