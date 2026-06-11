-- 文章版本表
CREATE TABLE `t_article_version`
(
    `id`           bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '版本id',
    `article_id`   bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '文章id，新文章首次发布前为0',
    `version_num`  int(11) UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本号，同一篇文章递增',
    `status`       tinyint(2) NOT NULL DEFAULT 0 COMMENT '版本状态: 0=草稿, 1=待发布, 2=已发布',
    `title`        varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '文章标题',
    `title_image`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '文章题图',
    `description`  varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '文章描述',
    `content`      text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '正文内容(markdown)',
    `category_id`  bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '分类id',
    `tag_ids`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '标签id列表,逗号分隔',
    `scheduled_at` datetime NULL DEFAULT NULL COMMENT '定时发布时间，NULL表示立即发布',
    `published_at` datetime NULL DEFAULT NULL COMMENT '实际发布时间',
    `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
    `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '版本更新时间',
    `is_deleted`   tinyint(2) NOT NULL DEFAULT 0 COMMENT '删除标志位',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_article_status` (`article_id`, `status`) USING BTREE,
    INDEX `idx_status_scheduled` (`status`, `scheduled_at`) USING BTREE,
    UNIQUE INDEX `uni_article_version` (`article_id`, `version_num`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章版本表' ROW_FORMAT = Dynamic;
