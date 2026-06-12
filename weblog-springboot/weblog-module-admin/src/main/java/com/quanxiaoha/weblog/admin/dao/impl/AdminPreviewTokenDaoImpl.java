package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminPreviewTokenDao;
import com.quanxiaoha.weblog.common.domain.dos.PreviewTokenDO;
import com.quanxiaoha.weblog.common.domain.mapper.PreviewTokenMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminPreviewTokenDaoImpl implements AdminPreviewTokenDao {

    @Autowired
    private PreviewTokenMapper previewTokenMapper;

    @Override
    public int insert(PreviewTokenDO token) {
        return previewTokenMapper.insert(token);
    }

    @Override
    public PreviewTokenDO selectByToken(String token) {
        QueryWrapper<PreviewTokenDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(PreviewTokenDO::getToken, token);
        return previewTokenMapper.selectOne(wrapper);
    }

    @Override
    public List<PreviewTokenDO> selectByVersionId(Long versionId) {
        QueryWrapper<PreviewTokenDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(PreviewTokenDO::getVersionId, versionId);
        return previewTokenMapper.selectList(wrapper);
    }

    @Override
    public int revokeByVersionId(Long versionId) {
        UpdateWrapper<PreviewTokenDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(PreviewTokenDO::getVersionId, versionId)
                .eq(PreviewTokenDO::getIsUsed, false)
                .set(PreviewTokenDO::getIsUsed, true);
        return previewTokenMapper.update(null, wrapper);
    }
}
