-- Grayscale Publishing & Reader Group Preview

-- 读者标签表（用于灰度分群）
CREATE TABLE IF NOT EXISTS `t_user_tag` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint(20) NOT NULL COMMENT '用户id',
    `tag_label` varchar(100) NOT NULL COMMENT '标签名称',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uni_user_tag` (`user_id`, `tag_label`),
    KEY `idx_tag_label` (`tag_label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签表';

-- 灰度规则表
CREATE TABLE IF NOT EXISTS `t_gray_rule` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `version_id` bigint(20) NOT NULL COMMENT '版本id',
    `rule_type` varchar(20) NOT NULL COMMENT '规则类型: TAG/PERCENTAGE/PREVIEW_TOKEN',
    `rule_value` varchar(500) NOT NULL COMMENT '规则值',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度规则表';

-- 预览令牌表
CREATE TABLE IF NOT EXISTS `t_preview_token` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `version_id` bigint(20) NOT NULL COMMENT '版本id',
    `token` varchar(64) NOT NULL COMMENT '预览令牌(UUID)',
    `expire_at` datetime NOT NULL COMMENT '过期时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_used` tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否已撤销: 0=活跃, 1=已撤销',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uni_token` (`token`),
    KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预览令牌表';

-- 发布批次表
CREATE TABLE IF NOT EXISTS `t_publish_batch` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `version_id` bigint(20) NOT NULL COMMENT '版本id',
    `batch_num` int(11) NOT NULL DEFAULT 1 COMMENT '批次号',
    `batch_type` varchar(10) NOT NULL COMMENT '批次类型: GRAY/FULL',
    `status` varchar(20) NOT NULL COMMENT '状态: ACTIVE/COMPLETED/ROLLED_BACK',
    `created_by` varchar(60) NOT NULL DEFAULT '' COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布批次表';

-- 版本曝光日志表
CREATE TABLE IF NOT EXISTS `t_version_exposure_log` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `version_id` bigint(20) NOT NULL COMMENT '版本id',
    `batch_id` bigint(20) DEFAULT NULL COMMENT '批次id',
    `article_id` bigint(20) NOT NULL COMMENT '文章id',
    `user_id` bigint(20) DEFAULT NULL COMMENT '用户id(匿名为NULL)',
    `matched_rule` varchar(100) DEFAULT NULL COMMENT '命中的规则',
    `exposed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '曝光时间',
    PRIMARY KEY (`id`),
    KEY `idx_version_batch` (`version_id`, `batch_id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_exposed_at` (`exposed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本曝光日志表';

-- 回滚审计表
CREATE TABLE IF NOT EXISTS `t_rollback_audit` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id` bigint(20) NOT NULL COMMENT '文章id',
    `from_version_id` bigint(20) NOT NULL COMMENT '回滚前版本id',
    `to_version_id` bigint(20) NOT NULL COMMENT '回滚后版本id',
    `batch_id` bigint(20) DEFAULT NULL COMMENT '关联批次id',
    `operator` varchar(60) NOT NULL DEFAULT '' COMMENT '操作人',
    `reason` varchar(500) DEFAULT NULL COMMENT '回滚原因',
    `rollback_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回滚时间',
    PRIMARY KEY (`id`),
    KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回滚审计表';
