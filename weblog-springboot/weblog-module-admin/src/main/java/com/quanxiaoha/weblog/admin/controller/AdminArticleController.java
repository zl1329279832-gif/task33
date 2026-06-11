package com.quanxiaoha.weblog.admin.controller;

import com.quanxiaoha.weblog.admin.model.vo.article.*;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
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

    // ==================== 版本化发布相关接口 ====================

    @PostMapping("/draft/save")
    @ApiOperationLog(description = "保存文章草稿")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response saveDraft(@RequestBody @Validated SaveArticleDraftReqVO req) {
        return articleService.saveDraft(req);
    }

    @PostMapping("/version/publish")
    @ApiOperationLog(description = "发布文章版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response publishVersion(@RequestBody @Validated PublishArticleVersionReqVO req) {
        return articleService.publishVersion(req);
    }

    @PostMapping("/version/list")
    @ApiOperationLog(description = "获取文章版本列表")
    public Response queryArticleVersionList(@RequestBody QueryArticleDetailReqVO req) {
        return articleService.queryArticleVersionList(req.getArticleId());
    }

    @PostMapping("/version/detail")
    @ApiOperationLog(description = "获取版本详情")
    public Response queryVersionDetail(@RequestBody QueryVersionDetailReqVO req) {
        return articleService.queryVersionDetail(req.getVersionId());
    }

    @PostMapping("/version/diff")
    @ApiOperationLog(description = "版本差异对比")
    public Response queryVersionDiff(@RequestBody @Validated QueryVersionDiffReqVO req) {
        return articleService.queryVersionDiff(req);
    }

    @PostMapping("/version/rollback")
    @ApiOperationLog(description = "回滚到历史版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response rollbackToVersion(@RequestBody @Validated RollbackArticleVersionReqVO req) {
        return articleService.rollbackToVersion(req);
    }

    @PostMapping("/version/recover")
    @ApiOperationLog(description = "恢复历史版本")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Response recoverVersion(@RequestBody @Validated RollbackArticleVersionReqVO req) {
        return articleService.recoverVersion(req.getTargetVersionId());
    }
}
