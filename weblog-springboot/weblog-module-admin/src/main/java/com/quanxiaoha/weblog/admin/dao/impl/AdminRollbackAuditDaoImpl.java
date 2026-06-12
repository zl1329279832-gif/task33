package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminRollbackAuditDao;
import com.quanxiaoha.weblog.common.domain.dos.RollbackAuditDO;
import com.quanxiaoha.weblog.common.domain.mapper.RollbackAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminRollbackAuditDaoImpl implements AdminRollbackAuditDao {

    @Autowired
    private RollbackAuditMapper rollbackAuditMapper;

    @Override
    public int insert(RollbackAuditDO audit) {
        return rollbackAuditMapper.insert(audit);
    }

    @Override
    public List<RollbackAuditDO> selectByArticleId(Long articleId) {
        QueryWrapper<RollbackAuditDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(RollbackAuditDO::getArticleId, articleId)
                .orderByDesc(RollbackAuditDO::getRollbackTime);
        return rollbackAuditMapper.selectList(wrapper);
    }
}
