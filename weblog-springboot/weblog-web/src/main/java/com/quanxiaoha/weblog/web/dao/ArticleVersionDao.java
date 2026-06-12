package com.quanxiaoha.weblog.web.dao;

import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;

public interface ArticleVersionDao {
    ArticleVersionDO selectActiveGrayByArticleId(Long articleId);
}
