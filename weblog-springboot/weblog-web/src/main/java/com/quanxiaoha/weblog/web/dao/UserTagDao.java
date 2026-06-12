package com.quanxiaoha.weblog.web.dao;

import com.quanxiaoha.weblog.common.domain.dos.UserTagDO;

import java.util.List;

public interface UserTagDao {
    List<UserTagDO> selectByUserId(Long userId);
}
