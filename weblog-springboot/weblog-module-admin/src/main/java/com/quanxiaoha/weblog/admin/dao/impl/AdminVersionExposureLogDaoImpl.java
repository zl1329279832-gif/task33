package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminVersionExposureLogDao;
import com.quanxiaoha.weblog.common.domain.dos.VersionExposureLogDO;
import com.quanxiaoha.weblog.common.domain.mapper.VersionExposureLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminVersionExposureLogDaoImpl implements AdminVersionExposureLogDao {

    @Autowired
    private VersionExposureLogMapper versionExposureLogMapper;

    @Override
    public int insert(VersionExposureLogDO log) {
        return versionExposureLogMapper.insert(log);
    }

    @Override
    public long countByVersionId(Long versionId) {
        QueryWrapper<VersionExposureLogDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(VersionExposureLogDO::getVersionId, versionId);
        return versionExposureLogMapper.selectCount(wrapper);
    }

    @Override
    public long countByVersionIdAndBatchId(Long versionId, Long batchId) {
        QueryWrapper<VersionExposureLogDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(VersionExposureLogDO::getVersionId, versionId)
                .eq(VersionExposureLogDO::getBatchId, batchId);
        return versionExposureLogMapper.selectCount(wrapper);
    }

    @Override
    public List<VersionExposureLogDO> selectByVersionId(Long versionId) {
        QueryWrapper<VersionExposureLogDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(VersionExposureLogDO::getVersionId, versionId);
        return versionExposureLogMapper.selectList(wrapper);
    }
}
