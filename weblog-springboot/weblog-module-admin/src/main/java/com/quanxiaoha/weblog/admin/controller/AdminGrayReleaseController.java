package com.quanxiaoha.weblog.admin.controller;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminGrayReleaseService;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.aspect.ApiOperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 灰度发布管理控制器
 */
@RestController
@RequestMapping("/admin/gray")
public class AdminGrayReleaseController {

    @Autowired
    private AdminGrayReleaseService grayReleaseService;

    @PostMapping("/publish")
    @ApiOperationLog(description = "灰度发布版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response publishGray(@RequestBody @Validated PublishGrayVersionReqVO req) {
        return grayReleaseService.publishGrayVersion(req);
    }

    @PostMapping("/full-publish")
    @ApiOperationLog(description = "全量发布灰度版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response fullPublishGray(@RequestBody @Validated FullPublishGrayReqVO req) {
        return grayReleaseService.fullPublishGray(req);
    }

    @PostMapping("/rollback")
    @ApiOperationLog(description = "回滚灰度版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response rollbackGray(@RequestBody @Validated RollbackGrayReqVO req) {
        return grayReleaseService.rollbackGray(req);
    }

    @PostMapping("/monitoring")
    @ApiOperationLog(description = "查询灰度监控数据")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response queryMonitoring(@RequestBody QueryVersionDetailReqVO req) {
        return grayReleaseService.queryGrayMonitoring(req.getVersionId());
    }

    @PostMapping("/feedback")
    @ApiOperationLog(description = "提交灰度反馈")
    public Response submitFeedback(@RequestBody @Validated SubmitGrayFeedbackReqVO req) {
        return grayReleaseService.submitFeedback(req);
    }

    @PostMapping("/user-tag/add")
    @ApiOperationLog(description = "添加用户标签关联")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response addUserTags(@RequestBody @Validated AddUserTagReqVO req) {
        return grayReleaseService.addUserTags(req);
    }

    @PostMapping("/user-tag/remove")
    @ApiOperationLog(description = "移除用户标签关联")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response removeUserTag(@RequestBody @Validated RemoveUserTagReqVO req) {
        return grayReleaseService.removeUserTag(req.getUsername(), req.getTagId());
    }
}
