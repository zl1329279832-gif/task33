package com.quanxiaoha.weblog.admin.dao;

import com.quanxiaoha.weblog.common.domain.dos.*;

import java.util.List;

public interface AdminGrayReleaseDao {

    // ========== 分群规则 ==========

    int insertSegmentRule(GraySegmentRuleDO rule);

    List<GraySegmentRuleDO> selectActiveRulesByArticleId(Long articleId);

    List<GraySegmentRuleDO> selectRulesByVersionId(Long versionId);

    int deactivateRulesByVersionId(Long versionId);

    int softDeleteRulesByVersionId(Long versionId);

    // ========== 预览令牌 ==========

    int insertPreviewToken(GrayPreviewTokenDO token);

    int insertPreviewTokenBatch(List<GrayPreviewTokenDO> tokens);

    GrayPreviewTokenDO selectByToken(String token);

    List<GrayPreviewTokenDO> selectTokensByVersionId(Long versionId);

    int softDeleteTokensByVersionId(Long versionId);

    // ========== 灰度版本查询 ==========

    ArticleVersionDO selectActiveGrayByArticleId(Long articleId);

    // ========== 曝光日志 ==========

    int insertExposureLog(GrayExposureLogDO log);

    long countExposuresByVersionId(Long versionId);

    long countExposuresByVersionIdAndAccessType(Long versionId, Integer accessType);

    // ========== 反馈 ==========

    int insertFeedback(GrayFeedbackDO feedback);

    List<GrayFeedbackDO> selectFeedbackByVersionId(Long versionId);

    long countFeedbackByVersionId(Long versionId);

    long countFeedbackByVersionIdAndType(Long versionId, Integer feedbackType);

    // ========== 回滚审计 ==========

    int insertRollbackAudit(GrayRollbackAuditDO audit);

    List<GrayRollbackAuditDO> selectRollbackAuditsByArticleId(Long articleId);
}
