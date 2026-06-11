package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;

import java.util.Date;
import java.util.List;

public interface AdminArticleVersionDao {
    int insert(ArticleVersionDO versionDO);

    int updateById(ArticleVersionDO versionDO);

    ArticleVersionDO selectById(Long id);

    ArticleVersionDO selectLatestDraftByArticleId(Long articleId);

    ArticleVersionDO selectPublishedByArticleId(Long articleId);

    List<ArticleVersionDO> selectByArticleId(Long articleId);

    List<ArticleVersionDO> selectPendingPublishDueVersions(Date now);

    Integer selectMaxVersionNum(Long articleId);

    int softDeleteByArticleId(Long articleId);

    List<ArticleVersionDO> selectPublishedVersionsByArticleId(Long articleId);
}
