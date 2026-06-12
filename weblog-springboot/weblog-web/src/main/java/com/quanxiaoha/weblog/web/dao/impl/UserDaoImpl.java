package com.quanxiaoha.weblog.web.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.common.domain.dos.UserDO;
import com.quanxiaoha.weblog.common.domain.mapper.UserMapper;
import com.quanxiaoha.weblog.web.dao.UserDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserDaoImpl implements UserDao {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Long selectUserIdByUsername(String username) {
        QueryWrapper<UserDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(UserDO::getUsername, username);
        UserDO user = userMapper.selectOne(wrapper);
        return user != null ? user.getId() : null;
    }
}
