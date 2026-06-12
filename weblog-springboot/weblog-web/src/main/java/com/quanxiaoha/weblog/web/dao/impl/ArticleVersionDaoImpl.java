package com.quanxiaoha.weblog.web.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.common.domain.dos.ArticleVersionDO;
import com.quanxiaoha.weblog.common.domain.mapper.ArticleVersionMapper;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import com.quanxiaoha.weblog.web.dao.ArticleVersionDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ArticleVersionDaoImpl implements ArticleVersionDao {

    @Autowired
    private ArticleVersionMapper articleVersionMapper;

    @Override
    public ArticleVersionDO selectActiveGrayByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.GRAY.getCode())
                .eq(ArticleVersionDO::getIsDeleted, false)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }
}
