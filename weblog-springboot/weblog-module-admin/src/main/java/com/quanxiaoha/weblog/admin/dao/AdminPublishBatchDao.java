package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.PublishBatchDO;

import java.util.Date;
import java.util.List;

public interface AdminPublishBatchDao {

    int insert(PublishBatchDO batch);

    int updateById(PublishBatchDO batch);

    PublishBatchDO selectById(Long id);

    PublishBatchDO selectActiveByVersionId(Long versionId);

    List<PublishBatchDO> selectByVersionId(Long versionId);

    int updateStatusById(Long id, String newStatus, Date completedAt);
}
