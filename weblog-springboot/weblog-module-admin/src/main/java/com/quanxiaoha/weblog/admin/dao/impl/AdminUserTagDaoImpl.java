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
    public int insertBatch(List<UserTagDO> userTags) {
        return userTagMapper.insertBatchSomeColumn(userTags);
    }

    @Override
    public List<UserTagDO> selectByUsername(String username) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(UserTagDO::getUsername, username);
        return userTagMapper.selectList(wrapper);
    }

    @Override
    public List<UserTagDO> selectByTagId(Long tagId) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(UserTagDO::getTagId, tagId);
        return userTagMapper.selectList(wrapper);
    }

    @Override
    public int deleteByUsernameAndTagId(String username, Long tagId) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(UserTagDO::getUsername, username)
                .eq(UserTagDO::getTagId, tagId);
        return userTagMapper.delete(wrapper);
    }
}
