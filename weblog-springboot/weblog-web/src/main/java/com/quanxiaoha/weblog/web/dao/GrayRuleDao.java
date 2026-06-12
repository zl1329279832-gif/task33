package com.quanxiaoha.weblog.web.dao;

import com.quanxiaoha.weblog.common.domain.dos.GrayRuleDO;

import java.util.List;

public interface GrayRuleDao {
    List<GrayRuleDO> selectByVersionId(Long versionId);
}
