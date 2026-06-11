package com.quanxiaoha.weblog.admin.schedule;

import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.admin.service.impl.AdminArticleServiceImpl;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 文章定时发布调度器
 */
@Component
@Slf4j
public class ArticlePublishScheduler {

    @Autowired
    private AdminArticleVersionDao articleVersionDao;

    @Autowired
    private AdminArticleServiceImpl articleService;

    /**
     * 每30秒扫描一次到期的待发布版本
     */
    @Scheduled(fixedDelay = 30_000)
    public void processPendingPublish() {
        Date now = new Date();
        List<ArticleVersionDO> pendingVersions = articleVersionDao.selectPendingPublishDue(now);

        if (pendingVersions.isEmpty()) {
            return;
        }

        for (ArticleVersionDO version : pendingVersions) {
            try {
                log.info("处理定时发布: 版本 {} (文章 {})", version.getId(), version.getArticleId());

                // CAS: PENDING_PUBLISH → PUBLISHED，防止并发重复处理
                int updated = articleVersionDao.updateStatusWithCas(
                        version.getId(),
                        ArticleVersionStatusEnum.PENDING_PUBLISH.getCode(),
                        ArticleVersionStatusEnum.PUBLISHED.getCode()
                );
                if (updated == 0) {
                    log.info("版本 {} 已被其他实例处理", version.getId());
                    continue;
                }

                // 过期检查：如果已有更新的已发布版本（如回滚产生），放弃此版本
                if (articleService.isVersionStale(version)) {
                    log.info("版本 {} 已过期（存在更新的已发布版本），标记为草稿", version.getId());
                    articleVersionDao.updateStatus(version.getId(), ArticleVersionStatusEnum.DRAFT.getCode());
                    continue;
                }

                // 物化到 live 表（状态已在 CAS 中更新）
                articleService.publishScheduledVersion(version);

                log.info("版本 {} 定时发布成功", version.getId());
            } catch (Exception e) {
                log.error("版本 {} 定时发布失败: {}", version.getId(), e.getMessage(), e);
                // 使用集中式恢复逻辑：回滚线上 + 标记草稿
                articleService.handlePublishFailure(version);
            }
        }
    }
}
