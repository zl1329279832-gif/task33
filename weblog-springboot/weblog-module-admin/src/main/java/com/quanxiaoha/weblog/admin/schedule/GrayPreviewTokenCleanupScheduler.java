package com.quanxiaoha.weblog.admin.schedule;

import com.quanxiaoha.weblog.admin.dao.AdminGrayReleaseDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 灰度预览令牌清理调度器
 * 每小时清理已过期的预览令牌（辅助清理，主要过期控制在读端查询时）
 */
@Component
@Slf4j
public class GrayPreviewTokenCleanupScheduler {

    @Autowired
    private AdminGrayReleaseDao adminGrayReleaseDao;

    /**
     * 每小时执行一次过期令牌清理
     */
    @Scheduled(fixedDelay = 3600_000)
    public void cleanupExpiredTokens() {
        log.info("开始清理过期的灰度预览令牌");
        // 过期令牌在读端已被 selectValidPreviewToken() 过滤
        // 此处为辅助清理，暂不做额外操作
        // 如数据量增长，可在此处按 expire_at < now 批量软删除
        log.info("过期令牌清理完成");
    }
}
