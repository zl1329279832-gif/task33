-- V3: 灰度发布与读者分群预览
-- 扩展 t_article_version 状态注释
ALTER TABLE `t_article_version` COMMENT = '文章版本表 (status: 0=DRAFT,1=PENDING_PUBLISH,2=PUBLISHED,3=GRAY)';

-- 灰度分群规则
CREATE TABLE `t_gray_segment_rule`
(
    `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `version_id`  bigint(20) UNSIGNED NOT NULL COMMENT '关联 t_article_version.id',
    `article_id`  bigint(20) UNSIGNED NOT NULL,
    `rule_type`   tinyint(2) NOT NULL COMMENT '1=TAG_USERS, 2=PERCENTAGE, 3=PREVIEW_LINK',
    `rule_config` varchar(1000) NOT NULL DEFAULT '{}' COMMENT 'JSON配置: {"tagIds":[1,2]} / {"percentage":20} / {}',
    `is_active`   tinyint(2) NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  tinyint(2) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_version_id` (`version_id`),
    INDEX `idx_article_active` (`article_id`, `is_active`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度分群规则';

-- 预览令牌
CREATE TABLE `t_gray_preview_token`
(
    `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `version_id`  bigint(20) UNSIGNED NOT NULL,
    `article_id`  bigint(20) UNSIGNED NOT NULL,
    `token`       varchar(64) NOT NULL COMMENT 'UUID令牌',
    `expire_at`   datetime NOT NULL,
    `created_by`  varchar(60) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_deleted`  tinyint(2) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uni_token` (`token`),
    INDEX `idx_version_id` (`version_id`),
    INDEX `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度预览令牌';

-- 版本曝光日志
CREATE TABLE `t_gray_exposure_log`
(
    `id`               bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `version_id`       bigint(20) UNSIGNED NOT NULL,
    `article_id`       bigint(20) UNSIGNED NOT NULL,
    `reader_username`  varchar(60) NOT NULL DEFAULT '',
    `access_type`      tinyint(2) NOT NULL COMMENT '1=TAG_HIT, 2=PERCENTAGE_HIT, 3=PREVIEW_TOKEN',
    `access_token`     varchar(64) NOT NULL DEFAULT '',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_version_id` (`version_id`),
    INDEX `idx_article_create` (`article_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度版本曝光日志';

-- 灰度反馈
CREATE TABLE `t_gray_feedback`
(
    `id`            bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `version_id`    bigint(20) UNSIGNED NOT NULL,
    `article_id`    bigint(20) UNSIGNED NOT NULL,
    `feedback_type` tinyint(2) NOT NULL COMMENT '1=COMMENT, 2=ERROR_REPORT',
    `content`       text NULL,
    `reporter`      varchar(60) NOT NULL DEFAULT '',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_deleted`    tinyint(2) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_version_id` (`version_id`),
    INDEX `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度反馈';

-- 灰度回滚审计
CREATE TABLE `t_gray_rollback_audit`
(
    `id`                  bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `article_id`          bigint(20) UNSIGNED NOT NULL,
    `gray_version_id`     bigint(20) UNSIGNED NOT NULL,
    `restored_version_id` bigint(20) UNSIGNED NOT NULL DEFAULT 0,
    `reason`              varchar(500) NOT NULL DEFAULT '',
    `operator`            varchar(60) NOT NULL DEFAULT '',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度回滚审计';

-- 用户标签关联
CREATE TABLE `t_user_tag`
(
    `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `username`    varchar(60) NOT NULL,
    `tag_id`      bigint(20) UNSIGNED NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uni_username_tag` (`username`, `tag_id`),
    INDEX `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签关联';
