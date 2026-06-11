package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminArticleVersionDao;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.common.domain.mapper.ArticleVersionMapper;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class AdminArticleVersionDaoImpl implements AdminArticleVersionDao {

    @Autowired
    private ArticleVersionMapper articleVersionMapper;

    @Override
    public int insert(ArticleVersionDO version) {
        return articleVersionMapper.insert(version);
    }

    @Override
    public int updateById(ArticleVersionDO version) {
        return articleVersionMapper.updateById(version);
    }

    @Override
    public ArticleVersionDO selectById(Long id) {
        return articleVersionMapper.selectById(id);
    }

    @Override
    public ArticleVersionDO selectLatestByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getIsDeleted, false)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public ArticleVersionDO selectLatestDraftByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.DRAFT.getCode())
                .eq(ArticleVersionDO::getIsDeleted, false)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public List<ArticleVersionDO> selectAllByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getIsDeleted, false)
                .orderByDesc(ArticleVersionDO::getVersionNum);
        return articleVersionMapper.selectList(wrapper);
    }

    @Override
    public ArticleVersionDO selectLatestPublishedByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.PUBLISHED.getCode())
                .eq(ArticleVersionDO::getIsDeleted, false)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public ArticleVersionDO selectPreviousPublishedByArticleId(Long articleId, Long currentVersionId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.PUBLISHED.getCode())
                .eq(ArticleVersionDO::getIsDeleted, false)
                .lt(ArticleVersionDO::getId, currentVersionId)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public List<ArticleVersionDO> selectPendingPublishDue(Date now) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .le(ArticleVersionDO::getScheduledAt, now)
                .eq(ArticleVersionDO::getIsDeleted, false);
        return articleVersionMapper.selectList(wrapper);
    }

    @Override
    public int updateStatusWithCas(Long id, int expectedStatus, int newStatus) {
        UpdateWrapper<ArticleVersionDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getId, id)
                .eq(ArticleVersionDO::getStatus, expectedStatus)
                .set(ArticleVersionDO::getStatus, newStatus);
        return articleVersionMapper.update(null, wrapper);
    }

    @Override
    public int updateStatus(Long id, int newStatus) {
        UpdateWrapper<ArticleVersionDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getId, id)
                .set(ArticleVersionDO::getStatus, newStatus);
        return articleVersionMapper.update(null, wrapper);
    }

    @Override
    public int updateArticleId(Long versionId, Long articleId) {
        UpdateWrapper<ArticleVersionDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getId, versionId)
                .set(ArticleVersionDO::getArticleId, articleId);
        return articleVersionMapper.update(null, wrapper);
    }

    @Override
    public Integer selectMaxVersionNum(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.select("MAX(version_num) as version_num")
                .lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getIsDeleted, false);
        ArticleVersionDO result = articleVersionMapper.selectOne(wrapper);
        return (result != null && result.getVersionNum() != null) ? result.getVersionNum() : 0;
    }

    @Override
    public int softDeleteByArticleId(Long articleId) {
        UpdateWrapper<ArticleVersionDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getIsDeleted, false)
                .set(ArticleVersionDO::getIsDeleted, true);
        return articleVersionMapper.update(null, wrapper);
    }
}
