package com.quanxiaoha.weblog.admin.service;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;

import java.util.List;

public interface AdminArticleService {
    Response publishArticle(PublishArticleReqVO publishArticleReqVO);

    Response queryArticleDetail(QueryArticleDetailReqVO queryArticleDetailReqVO);

    Response queryArticlePageList(QueryArticlePageListReqVO queryArticlePageListReqVO);

    Response deleteArticle(DeleteArticleReqVO deleteArticleReqVO);

    Response updateArticle(UpdateArticleReqVO updateArticleReqVO);

    Response saveDraft(SaveDraftReqVO saveDraftReqVO);

    Response publishVersion(PublishVersionReqVO publishVersionReqVO);

    Response rollbackVersion(RollbackVersionReqVO rollbackVersionReqVO);

    Response queryArticleVersionList(QueryVersionListReqVO queryVersionListReqVO);

    Response queryVersionDiff(Long versionId1, Long versionId2);

    void publishVersionToLive(ArticleVersionDO version);
}
