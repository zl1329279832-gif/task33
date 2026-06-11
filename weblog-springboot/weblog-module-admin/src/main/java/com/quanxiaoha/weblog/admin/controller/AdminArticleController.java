package com.quanxiaoha.weblog.admin.controller;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.aspect.ApiOperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-04-19 16:06
 * @description: TODO
 **/
@RestController
@RequestMapping("/admin/article")
public class AdminArticleController {

    @Autowired
    private AdminArticleService articleService;

    @PostMapping("/publish")
    @ApiOperationLog(description = "发布文章")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response publishArticle(@RequestBody @Validated PublishArticleReqVO publishArticleReqVO) {
        return articleService.publishArticle(publishArticleReqVO);
    }

    @PostMapping("/update")
    @ApiOperationLog(description = "修改文章")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response updateArticle(@RequestBody @Validated UpdateArticleReqVO updateArticleReqVO) {
        return articleService.updateArticle(updateArticleReqVO);
    }

    @PostMapping("/detail")
    @ApiOperationLog(description = "获取文章详情")
    public Response queryArticleDetail(@RequestBody QueryArticleDetailReqVO queryArticleDetailReqVO) {
        return articleService.queryArticleDetail(queryArticleDetailReqVO);
    }

    @PostMapping("/list")
    @ApiOperationLog(description = "获取文章分页数据")
    public Response queryArticlePageList(@RequestBody QueryArticlePageListReqVO queryArticlePageListReqVO) {
        return articleService.queryArticlePageList(queryArticlePageListReqVO);
    }

    @PostMapping("/delete")
    @ApiOperationLog(description = "删除文章")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response deleteArticle(@RequestBody @Validated DeleteArticleReqVO deleteArticleReqVO) {
        return articleService.deleteArticle(deleteArticleReqVO);
    }

    @PostMapping("/version/saveDraft")
    @ApiOperationLog(description = "保存文章草稿版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response saveDraft(@RequestBody @Validated SaveDraftReqVO saveDraftReqVO) {
        return articleService.saveDraft(saveDraftReqVO);
    }

    @PostMapping("/version/publish")
    @ApiOperationLog(description = "发布文章版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response publishVersion(@RequestBody @Validated PublishVersionReqVO publishVersionReqVO) {
        return articleService.publishVersion(publishVersionReqVO);
    }

    @PostMapping("/version/rollback")
    @ApiOperationLog(description = "回滚文章版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response rollbackVersion(@RequestBody @Validated RollbackVersionReqVO rollbackVersionReqVO) {
        return articleService.rollbackVersion(rollbackVersionReqVO);
    }

    @PostMapping("/version/list")
    @ApiOperationLog(description = "获取文章版本列表")
    public Response queryArticleVersionList(@RequestBody QueryVersionListReqVO queryVersionListReqVO) {
        return articleService.queryArticleVersionList(queryVersionListReqVO);
    }

    @PostMapping("/version/diff")
    @ApiOperationLog(description = "文章版本差异对比")
    public Response queryVersionDiff(@RequestParam Long versionId1, @RequestParam Long versionId2) {
        return articleService.queryVersionDiff(versionId1, versionId2);
    }

}
