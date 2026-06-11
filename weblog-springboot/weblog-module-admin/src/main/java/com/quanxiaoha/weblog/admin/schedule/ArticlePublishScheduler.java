package com.quanxiaoha.weblog.admin.schedule;

import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.admin.service.AdminArticleService;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class ArticlePublishScheduler {

    @Autowired
    private AdminArticleVersionDao articleVersionDao;
    @Autowired
    private AdminArticleService articleService;

    @Scheduled(fixedRate = 60000)
    public void processPendingPublish() {
        Date now = new Date();
        List<ArticleVersionDO> dueVersions = articleVersionDao.selectPendingPublishDueVersions(now);

        if (CollectionUtils.isEmpty(dueVersions)) {
            return;
        }

        for (ArticleVersionDO version : dueVersions) {
            try {
                articleService.publishVersionToLive(version);
                log.info("Scheduled publish succeeded for version id={}, articleId={}",
                        version.getId(), version.getArticleId());
            } catch (Exception e) {
                log.error("Scheduled publish failed for version id={}", version.getId(), e);
                version.setStatus(ArticleVersionStatusEnum.DRAFT.getCode());
                version.setScheduledAt(null);
                version.setUpdateTime(new Date());
                articleVersionDao.updateById(version);
            }
        }
    }
}
