package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.UserTagDO;

import java.util.List;

public interface AdminUserTagDao {

    int insertBatch(List<UserTagDO> userTags);

    List<UserTagDO> selectByUsername(String username);

    List<UserTagDO> selectByTagId(Long tagId);

    int deleteByUsernameAndTagId(String username, Long tagId);
}
