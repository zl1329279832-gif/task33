package com.quanxiaoha.weblog.web.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.common.domain.dos.PreviewTokenDO;
import com.quanxiaoha.weblog.common.domain.mapper.PreviewTokenMapper;
import com.quanxiaoha.weblog.web.dao.PreviewTokenDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PreviewTokenDaoImpl implements PreviewTokenDao {

    @Autowired
    private PreviewTokenMapper previewTokenMapper;

    @Override
    public PreviewTokenDO selectByToken(String token) {
        QueryWrapper<PreviewTokenDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(PreviewTokenDO::getToken, token);
        return previewTokenMapper.selectOne(wrapper);
    }
}
