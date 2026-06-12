package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminGrayRuleDao;
import com.quanxiaoha.weblog.common.domain.dos.GrayRuleDO;
import com.quanxiaoha.weblog.common.domain.mapper.GrayRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminGrayRuleDaoImpl implements AdminGrayRuleDao {

    @Autowired
    private GrayRuleMapper grayRuleMapper;

    @Override
    public int insert(GrayRuleDO rule) {
        return grayRuleMapper.insert(rule);
    }

    @Override
    public int deleteByVersionId(Long versionId) {
        QueryWrapper<GrayRuleDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(GrayRuleDO::getVersionId, versionId);
        return grayRuleMapper.delete(wrapper);
    }

    @Override
    public List<GrayRuleDO> selectByVersionId(Long versionId) {
        QueryWrapper<GrayRuleDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(GrayRuleDO::getVersionId, versionId);
        return grayRuleMapper.selectList(wrapper);
    }

    @Override
    public int deleteById(Long id) {
        return grayRuleMapper.deleteById(id);
    }
}
