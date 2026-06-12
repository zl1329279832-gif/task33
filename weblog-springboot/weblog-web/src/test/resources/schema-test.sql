-- H2 compatible schema for testing

CREATE TABLE IF NOT EXISTS t_article
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    title       varchar(200) NOT NULL DEFAULT '',
    title_image varchar(200) DEFAULT '',
    description varchar(160) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  tinyint NOT NULL DEFAULT 0,
    read_num    int NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS t_article_content
(
    id         bigint AUTO_INCREMENT PRIMARY KEY,
    article_id bigint NOT NULL,
    content    text NULL
);

CREATE TABLE IF NOT EXISTS t_article_category_rel
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    article_id  bigint NOT NULL,
    category_id bigint NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uni_article_id ON t_article_category_rel(article_id);

CREATE TABLE IF NOT EXISTS t_article_tag_rel
(
    id         bigint AUTO_INCREMENT PRIMARY KEY,
    article_id bigint NOT NULL,
    tag_id     bigint NOT NULL
);

CREATE TABLE IF NOT EXISTS t_article_version
(
    id           bigint AUTO_INCREMENT PRIMARY KEY,
    article_id   bigint NOT NULL DEFAULT 0,
    version_num  int NOT NULL DEFAULT 1,
    status       tinyint NOT NULL DEFAULT 0,
    title        varchar(200) NOT NULL DEFAULT '',
    title_image  varchar(200) DEFAULT '',
    description  varchar(160) NOT NULL DEFAULT '',
    content      text NULL,
    category_id  bigint NOT NULL DEFAULT 0,
    tag_ids      varchar(500) NOT NULL DEFAULT '',
    scheduled_at datetime NULL DEFAULT NULL,
    published_at datetime NULL DEFAULT NULL,
    create_time  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted   tinyint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_category
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    name        varchar(60) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  tinyint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_tag
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    name        varchar(60) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  tinyint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_blog_setting
(
    id           bigint AUTO_INCREMENT PRIMARY KEY,
    blog_name    varchar(60) NOT NULL DEFAULT '',
    author       varchar(60) NOT NULL DEFAULT '',
    introduction varchar(200) NOT NULL DEFAULT '',
    avatar       varchar(160) NOT NULL DEFAULT '',
    github_home  varchar(120) DEFAULT '',
    csdn_home    varchar(120) DEFAULT '',
    gitee_home   varchar(120) DEFAULT '',
    zhihu_home   varchar(120) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS t_statistics_article_pv
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    pv_date     date NOT NULL,
    pv_count    bigint NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_user
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    username    varchar(60) NOT NULL,
    password    varchar(60) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  tinyint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_user_role
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    username    varchar(60) NOT NULL,
    role        varchar(60) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 灰度分群规则
CREATE TABLE IF NOT EXISTS t_gray_segment_rule
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    version_id  bigint NOT NULL,
    article_id  bigint NOT NULL,
    rule_type   tinyint NOT NULL,
    rule_config varchar(1000) NOT NULL DEFAULT '{}',
    is_active   tinyint NOT NULL DEFAULT 1,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  tinyint NOT NULL DEFAULT 0
);

-- 预览令牌
CREATE TABLE IF NOT EXISTS t_gray_preview_token
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    version_id  bigint NOT NULL,
    article_id  bigint NOT NULL,
    token       varchar(64) NOT NULL,
    expire_at   datetime NOT NULL,
    created_by  varchar(60) NOT NULL DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  tinyint NOT NULL DEFAULT 0
);

-- 版本曝光日志
CREATE TABLE IF NOT EXISTS t_gray_exposure_log
(
    id               bigint AUTO_INCREMENT PRIMARY KEY,
    version_id       bigint NOT NULL,
    article_id       bigint NOT NULL,
    reader_username  varchar(60) NOT NULL DEFAULT '',
    access_type      tinyint NOT NULL,
    access_token     varchar(64) NOT NULL DEFAULT '',
    create_time      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 灰度反馈
CREATE TABLE IF NOT EXISTS t_gray_feedback
(
    id            bigint AUTO_INCREMENT PRIMARY KEY,
    version_id    bigint NOT NULL,
    article_id    bigint NOT NULL,
    feedback_type tinyint NOT NULL,
    content       text NULL,
    reporter      varchar(60) NOT NULL DEFAULT '',
    create_time   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    tinyint NOT NULL DEFAULT 0
);

-- 灰度回滚审计
CREATE TABLE IF NOT EXISTS t_gray_rollback_audit
(
    id                  bigint AUTO_INCREMENT PRIMARY KEY,
    article_id          bigint NOT NULL,
    gray_version_id     bigint NOT NULL,
    restored_version_id bigint NOT NULL DEFAULT 0,
    reason              varchar(500) NOT NULL DEFAULT '',
    operator            varchar(60) NOT NULL DEFAULT '',
    create_time         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户标签关联
CREATE TABLE IF NOT EXISTS t_user_tag
(
    id          bigint AUTO_INCREMENT PRIMARY KEY,
    username    varchar(60) NOT NULL,
    tag_id      bigint NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);
