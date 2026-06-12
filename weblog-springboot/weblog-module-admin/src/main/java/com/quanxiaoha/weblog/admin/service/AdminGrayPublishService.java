package com.quanxiaoha.weblog.admin.service;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.common.Response;

/**
 * 灰度发布管理服务
 */
public interface AdminGrayPublishService {

    Response grayPublish(GrayPublishReqVO req);

    Response updateGrayRules(GrayRuleUpdateReqVO req);

    Response generatePreviewToken(GeneratePreviewTokenReqVO req);

    Response promoteGrayToFull(GrayPromoteReqVO req);

    Response rollbackGray(GrayRollbackReqVO req);

    Response queryGrayStats(Long versionId);

    Response queryPublishBatchList(QueryPublishBatchListReqVO req);
}
