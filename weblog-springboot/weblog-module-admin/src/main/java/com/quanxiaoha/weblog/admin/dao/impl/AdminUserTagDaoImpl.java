package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminUserTagDao;
import com.quanxiaoha.weblog.common.domain.dos.UserTagDO;
import com.quanxiaoha.weblog.common.domain.mapper.UserTagMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminUserTagDaoImpl implements AdminUserTagDao {

    @Autowired
    private UserTagMapper userTagMapper;

    @Override
    public int insert(UserTagDO userTag) {
        return userTagMapper.insert(userTag);
    }

    @Override
    public int deleteById(Long id) {
        return userTagMapper.deleteById(id);
    }

    @Override
    public List<UserTagDO> selectByUserId(Long userId) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(UserTagDO::getUserId, userId);
        return userTagMapper.selectList(wrapper);
    }

    @Override
    public List<UserTagDO> selectByTagLabels(List<String> tagLabels) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().in(UserTagDO::getTagLabel, tagLabels);
        return userTagMapper.selectList(wrapper);
    }
}
