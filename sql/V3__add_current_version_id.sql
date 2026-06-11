-- 为 t_article 增加 current_version_id 字段，用于版本发布 CAS 防止并发覆盖
ALTER TABLE t_article ADD COLUMN current_version_id BIGINT(20) UNSIGNED DEFAULT NULL COMMENT '当前生效版本ID';

-- 回填：将每篇文章的 current_version_id 设置为最新已发布版本的 id
UPDATE t_article a
INNER JOIN (
    SELECT article_id, MAX(id) AS max_version_id
    FROM t_article_version
    WHERE status = 2 AND is_deleted = 0
    GROUP BY article_id
) v ON a.id = v.article_id
SET a.current_version_id = v.max_version_id;
