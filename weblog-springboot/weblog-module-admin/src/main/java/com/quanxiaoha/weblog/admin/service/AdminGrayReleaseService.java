package com.quanxiaoha.weblog.admin.service;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.common.Response;

/**
 * 灰度发布管理服务
 */
public interface AdminGrayReleaseService {

    /**
     * 灰度发布版本
     */
    Response publishGrayVersion(PublishGrayVersionReqVO req);

    /**
     * 全量发布灰度版本
     */
    Response fullPublishGray(FullPublishGrayReqVO req);

    /**
     * 回滚灰度版本
     */
    Response rollbackGray(RollbackGrayReqVO req);

    /**
     * 查询灰度监控数据
     */
    Response queryGrayMonitoring(Long grayVersionId);

    /**
     * 提交灰度反馈
     */
    Response submitFeedback(SubmitGrayFeedbackReqVO req);

    /**
     * 添加用户标签关联
     */
    Response addUserTags(AddUserTagReqVO req);

    /**
     * 移除用户标签关联
     */
    Response removeUserTag(String username, Long tagId);
}
