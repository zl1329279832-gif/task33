package com.quanxiaoha.weblog.web.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.domain.mapper.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.web.dao.GrayReleaseDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class GrayReleaseDaoImpl implements GrayReleaseDao {

    @Autowired
    private ArticleVersionMapper articleVersionMapper;

    @Autowired
    private GraySegmentRuleMapper graySegmentRuleMapper;

    @Autowired
    private GrayPreviewTokenMapper grayPreviewTokenMapper;

    @Autowired
    private UserTagMapper userTagMapper;

    @Autowired
    private GrayExposureLogMapper grayExposureLogMapper;

    @Override
    public ArticleVersionDO selectActiveGrayByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.GRAY.getCode())
                .eq(ArticleVersionDO::getIsDeleted, false)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    @Override
    public List<GraySegmentRuleDO> selectActiveRulesByArticleId(Long articleId) {
        QueryWrapper<GraySegmentRuleDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GraySegmentRuleDO::getArticleId, articleId)
                .eq(GraySegmentRuleDO::getIsActive, true)
                .eq(GraySegmentRuleDO::getIsDeleted, false);
        return graySegmentRuleMapper.selectList(wrapper);
    }

    @Override
    public GrayPreviewTokenDO selectValidPreviewToken(String token) {
        QueryWrapper<GrayPreviewTokenDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayPreviewTokenDO::getToken, token)
                .gt(GrayPreviewTokenDO::getExpireAt, new Date())
                .eq(GrayPreviewTokenDO::getIsDeleted, false)
                .last("limit 1");
        return grayPreviewTokenMapper.selectOne(wrapper);
    }

    @Override
    public List<UserTagDO> selectUserTags(String username) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(UserTagDO::getUsername, username);
        return userTagMapper.selectList(wrapper);
    }

    @Override
    public int insertExposureLog(GrayExposureLogDO logDO) {
        return grayExposureLogMapper.insert(logDO);
    }
}
