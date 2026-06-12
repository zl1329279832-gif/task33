package com.quanxiaoha.weblog.web.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.common.domain.dos.UserTagDO;
import com.quanxiaoha.weblog.common.domain.mapper.UserTagMapper;
import com.quanxiaoha.weblog.web.dao.UserTagDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class UserTagDaoImpl implements UserTagDao {

    @Autowired
    private UserTagMapper userTagMapper;

    @Override
    public List<UserTagDO> selectByUserId(Long userId) {
        QueryWrapper<UserTagDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(UserTagDO::getUserId, userId);
        return userTagMapper.selectList(wrapper);
    }
}
