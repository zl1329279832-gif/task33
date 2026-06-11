package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;

import java.util.Date;
import java.util.List;

public interface AdminArticleVersionDao {

    int insert(ArticleVersionDO version);

    int updateById(ArticleVersionDO version);

    ArticleVersionDO selectById(Long id);

    /** 某篇文章的最新版本（version_num 最大） */
    ArticleVersionDO selectLatestByArticleId(Long articleId);

    /** 某篇文章的最新草稿版本 */
    ArticleVersionDO selectLatestDraftByArticleId(Long articleId);

    /** 某篇文章的所有版本，按 version_num DESC 排序 */
    List<ArticleVersionDO> selectAllByArticleId(Long articleId);

    /** 某篇文章最新的已发布版本 */
    ArticleVersionDO selectLatestPublishedByArticleId(Long articleId);

    /** 某篇文章在当前版本之前的上一个已发布版本（用于回滚） */
    ArticleVersionDO selectPreviousPublishedByArticleId(Long articleId, Long currentVersionId);

    /** 所有到期的待发布版本 */
    List<ArticleVersionDO> selectPendingPublishDue(Date now);

    /** CAS 状态转换：仅当当前状态为 expectedStatus 时才更新为 newStatus */
    int updateStatusWithCas(Long id, int expectedStatus, int newStatus);

    /** 更新状态（无 CAS 保护） */
    int updateStatus(Long id, int newStatus);

    /** 首次发布后回填 articleId */
    int updateArticleId(Long versionId, Long articleId);

    /** 获取某篇文章的最大版本号 */
    Integer selectMaxVersionNum(Long articleId);

    /** 删除文章时级联软删所有版本 */
    int softDeleteByArticleId(Long articleId);
}
