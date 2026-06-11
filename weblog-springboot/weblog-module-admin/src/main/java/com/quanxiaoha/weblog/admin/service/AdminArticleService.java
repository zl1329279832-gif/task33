package com.quanxiaoha.weblog.admin.service;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.common.Response;

import java.util.List;

public interface AdminArticleService {
    Response publishArticle(PublishArticleReqVO publishArticleReqVO);

    Response queryArticleDetail(QueryArticleDetailReqVO queryArticleDetailReqVO);

    Response queryArticlePageList(QueryArticlePageListReqVO queryArticlePageListReqVO);

    Response deleteArticle(DeleteArticleReqVO deleteArticleReqVO);

    Response updateArticle(UpdateArticleReqVO updateArticleReqVO);

    /** 保存/更新草稿版本，不触碰线上表 */
    Response saveDraft(SaveArticleDraftReqVO req);

    /** 发布草稿版本（立即或定时） */
    Response publishVersion(PublishArticleVersionReqVO req);

    /** 获取文章版本列表 */
    Response queryArticleVersionList(Long articleId);

    /** 获取指定版本详情 */
    Response queryVersionDetail(Long versionId);

    /** 版本差异对比 */
    Response queryVersionDiff(QueryVersionDiffReqVO req);

    /** 回滚到历史版本 */
    Response rollbackToVersion(RollbackArticleVersionReqVO req);

    /** 恢复历史版本（重新发布） */
    Response recoverVersion(Long versionId);
}
