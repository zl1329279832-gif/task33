-- 为存量文章补建已发布版本记录
INSERT INTO t_article_version (article_id, version_num, status, title, title_image, description,
    content, category_id, tag_ids, published_at, create_time, update_time, is_deleted)
SELECT
    a.id AS article_id,
    1 AS version_num,
    2 AS status,
    a.title,
    a.title_image,
    a.description,
    ac.content,
    IFNULL(acr.category_id, 0),
    IFNULL(GROUP_CONCAT(atr.tag_id ORDER BY atr.tag_id SEPARATOR ','), ''),
    a.create_time AS published_at,
    NOW() AS create_time,
    NOW() AS update_time,
    0 AS is_deleted
FROM t_article a
LEFT JOIN t_article_content ac ON a.id = ac.article_id
LEFT JOIN t_article_category_rel acr ON a.id = acr.article_id
LEFT JOIN t_article_tag_rel atr ON a.id = atr.article_id
WHERE a.is_deleted = 0
GROUP BY a.id, a.title, a.title_image, a.description, ac.content, acr.category_id, a.create_time;
