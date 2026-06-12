package com.quanxiaoha.weblog.web.dao;

import com.quanxiaoha.weblog.common.domain.dos.PreviewTokenDO;

public interface PreviewTokenDao {
    PreviewTokenDO selectByToken(String token);
}
