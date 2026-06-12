package com.quanxiaoha.weblog.web.dao.impl;

import com.quanxiaoha.weblog.common.domain.dos.VersionExposureLogDO;
import com.quanxiaoha.weblog.common.domain.mapper.VersionExposureLogMapper;
import com.quanxiaoha.weblog.web.dao.VersionExposureLogDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VersionExposureLogDaoImpl implements VersionExposureLogDao {

    @Autowired
    private VersionExposureLogMapper versionExposureLogMapper;

    @Override
    public int insert(VersionExposureLogDO log) {
        return versionExposureLogMapper.insert(log);
    }
}
