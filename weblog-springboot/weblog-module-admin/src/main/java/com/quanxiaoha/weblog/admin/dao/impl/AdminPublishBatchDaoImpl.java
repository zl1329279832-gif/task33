package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminPublishBatchDao;
import com.quanxiaoha.weblog.common.domain.dos.PublishBatchDO;
import com.quanxiaoha.weblog.common.domain.mapper.PublishBatchMapper;
import com.quanxiaoha.weblog.common.enums.PublishBatchStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class AdminPublishBatchDaoImpl implements AdminPublishBatchDao {

    @Autowired
    private PublishBatchMapper publishBatchMapper;

    @Override
    public int insert(PublishBatchDO batch) {
        return publishBatchMapper.insert(batch);
    }

    @Override
    public int updateById(PublishBatchDO batch) {
        return publishBatchMapper.updateById(batch);
    }

    @Override
    public PublishBatchDO selectById(Long id) {
        return publishBatchMapper.selectById(id);
    }

    @Override
    public PublishBatchDO selectActiveByVersionId(Long versionId) {
        QueryWrapper<PublishBatchDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PublishBatchDO::getVersionId, versionId)
                .eq(PublishBatchDO::getStatus, PublishBatchStatusEnum.ACTIVE.getCode())
                .last("limit 1");
        return publishBatchMapper.selectOne(wrapper);
    }

    @Override
    public List<PublishBatchDO> selectByVersionId(Long versionId) {
        QueryWrapper<PublishBatchDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PublishBatchDO::getVersionId, versionId)
                .orderByDesc(PublishBatchDO::getBatchNum);
        return publishBatchMapper.selectList(wrapper);
    }

    @Override
    public int updateStatusById(Long id, String newStatus, Date completedAt) {
        UpdateWrapper<PublishBatchDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(PublishBatchDO::getId, id)
                .set(PublishBatchDO::getStatus, newStatus)
                .set(PublishBatchDO::getCompletedAt, completedAt);
        return publishBatchMapper.update(null, wrapper);
    }
}
