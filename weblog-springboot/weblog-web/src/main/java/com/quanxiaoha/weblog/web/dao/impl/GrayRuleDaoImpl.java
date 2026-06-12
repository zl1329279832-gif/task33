package com.quanxiaoha.weblog.web.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.common.domain.dos.GrayRuleDO;
import com.quanxiaoha.weblog.common.domain.mapper.GrayRuleMapper;
import com.quanxiaoha.weblog.web.dao.GrayRuleDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GrayRuleDaoImpl implements GrayRuleDao {

    @Autowired
    private GrayRuleMapper grayRuleMapper;

    @Override
    public List<GrayRuleDO> selectByVersionId(Long versionId) {
        QueryWrapper<GrayRuleDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(GrayRuleDO::getVersionId, versionId);
        return grayRuleMapper.selectList(wrapper);
    }
}
