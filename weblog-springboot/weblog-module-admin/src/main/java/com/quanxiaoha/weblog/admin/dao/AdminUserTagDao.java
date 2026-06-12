package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.UserTagDO;

import java.util.List;

public interface AdminUserTagDao {

    int insert(UserTagDO userTag);

    int deleteById(Long id);

    List<UserTagDO> selectByUserId(Long userId);

    List<UserTagDO> selectByTagLabels(List<String> tagLabels);
}
