package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.VersionExposureLogDO;

import java.util.List;

public interface AdminVersionExposureLogDao {

    int insert(VersionExposureLogDO log);

    long countByVersionId(Long versionId);

    long countByVersionIdAndBatchId(Long versionId, Long batchId);

    List<VersionExposureLogDO> selectByVersionId(Long versionId);
}
