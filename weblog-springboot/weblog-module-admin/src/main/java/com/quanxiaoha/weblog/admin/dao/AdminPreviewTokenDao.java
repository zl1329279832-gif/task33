package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.PreviewTokenDO;

import java.util.List;

public interface AdminPreviewTokenDao {

    int insert(PreviewTokenDO token);

    PreviewTokenDO selectByToken(String token);

    List<PreviewTokenDO> selectByVersionId(Long versionId);

    int revokeByVersionId(Long versionId);
}
