package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.RollbackAuditDO;

import java.util.List;

public interface AdminRollbackAuditDao {

    int insert(RollbackAuditDO audit);

    List<RollbackAuditDO> selectByArticleId(Long articleId);
}
