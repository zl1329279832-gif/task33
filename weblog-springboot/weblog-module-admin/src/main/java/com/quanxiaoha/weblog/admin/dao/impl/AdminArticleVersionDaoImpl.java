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
    public int insert(ArticleVersionDO versionDO) {
        return articleVersionMapper.insert(versionDO);
    }

    @Override
    public int updateById(ArticleVersionDO versionDO) {
        return articleVersionMapper.updateById(versionDO);
    }

    @Override
    public ArticleVersionDO selectById(Long id) {
        return articleVersionMapper.selectById(id);
    }

    @Override
    public ArticleVersionDO selectLatestDraftByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.DRAFT.getCode())
                .eq(ArticleVersionDO::getIsDeleted, 0)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("LIMIT 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public ArticleVersionDO selectPublishedByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.PUBLISHED.getCode())
                .eq(ArticleVersionDO::getIsDeleted, 0)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("LIMIT 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public List<ArticleVersionDO> selectByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getIsDeleted, 0)
                .orderByDesc(ArticleVersionDO::getVersionNum);
        return articleVersionMapper.selectList(wrapper);
    }

    @Override
    public List<ArticleVersionDO> selectPendingPublishDueVersions(Date now) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.PENDING_PUBLISH.getCode())
                .le(ArticleVersionDO::getScheduledAt, now)
                .eq(ArticleVersionDO::getIsDeleted, 0);
        return articleVersionMapper.selectList(wrapper);
    }

    @Override
    public Integer selectMaxVersionNum(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.select("MAX(version_num) as version_num")
                .lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getIsDeleted, 0);
        ArticleVersionDO result = articleVersionMapper.selectOne(wrapper);
        return result != null && result.getVersionNum() != null ? result.getVersionNum() : 0;
    }

    @Override
    public int softDeleteByArticleId(Long articleId) {
        UpdateWrapper<ArticleVersionDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .set(ArticleVersionDO::getIsDeleted, 1)
                .set(ArticleVersionDO::getUpdateTime, new Date())
                .eq(ArticleVersionDO::getArticleId, articleId);
        return articleVersionMapper.update(null, wrapper);
    }

    @Override
    public List<ArticleVersionDO> selectPublishedVersionsByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.PUBLISHED.getCode())
                .eq(ArticleVersionDO::getIsDeleted, 0)
                .orderByDesc(ArticleVersionDO::getVersionNum);
        return articleVersionMapper.selectList(wrapper);
    }
}
