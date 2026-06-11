package com.quanxiaoha.weblog.admin.exception;

/**
 * 版本 CAS 失败异常：当前 live 表已被更新的版本占据，本次物化被拒绝
 */
public class StaleVersionException extends RuntimeException {
    public StaleVersionException(Long articleId, Long versionId, Long currentVersionId) {
        super(String.format("文章 %d 当前生效版本为 %d，版本 %d 已过时，物化被拒绝",
                articleId, currentVersionId, versionId));
    }
}
