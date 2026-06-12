package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.GrayRuleDO;

import java.util.List;

public interface AdminGrayRuleDao {

    int insert(GrayRuleDO rule);

    int deleteByVersionId(Long versionId);

    List<GrayRuleDO> selectByVersionId(Long versionId);

    int deleteById(Long id);
}
