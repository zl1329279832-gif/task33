package com.quanxiaoha.weblog.admin.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.quanxiaoha.weblog.admin.dao.AdminGrayReleaseDao;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.domain.mapper.*;
import com.quanxiaoha.weblog.common.enums.ArticleVersionStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AdminGrayReleaseDaoImpl implements AdminGrayReleaseDao {

    @Autowired
    private GraySegmentRuleMapper graySegmentRuleMapper;

    @Autowired
    private GrayPreviewTokenMapper grayPreviewTokenMapper;

    @Autowired
    private ArticleVersionMapper articleVersionMapper;

    @Autowired
    private GrayExposureLogMapper grayExposureLogMapper;

    @Autowired
    private GrayFeedbackMapper grayFeedbackMapper;

    @Autowired
    private GrayRollbackAuditMapper grayRollbackAuditMapper;

    // ========== 分群规则 ==========

    @Override
    public int insertSegmentRule(GraySegmentRuleDO rule) {
        return graySegmentRuleMapper.insert(rule);
    }

    @Override
    public List<GraySegmentRuleDO> selectActiveRulesByArticleId(Long articleId) {
        QueryWrapper<GraySegmentRuleDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GraySegmentRuleDO::getArticleId, articleId)
                .eq(GraySegmentRuleDO::getIsActive, true)
                .eq(GraySegmentRuleDO::getIsDeleted, false);
        return graySegmentRuleMapper.selectList(wrapper);
    }

    @Override
    public List<GraySegmentRuleDO> selectRulesByVersionId(Long versionId) {
        QueryWrapper<GraySegmentRuleDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GraySegmentRuleDO::getVersionId, versionId)
                .eq(GraySegmentRuleDO::getIsDeleted, false);
        return graySegmentRuleMapper.selectList(wrapper);
    }

    @Override
    public int deactivateRulesByVersionId(Long versionId) {
        UpdateWrapper<GraySegmentRuleDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(GraySegmentRuleDO::getVersionId, versionId)
                .eq(GraySegmentRuleDO::getIsDeleted, false)
                .set(GraySegmentRuleDO::getIsActive, false);
        return graySegmentRuleMapper.update(null, wrapper);
    }

    @Override
    public int softDeleteRulesByVersionId(Long versionId) {
        UpdateWrapper<GraySegmentRuleDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(GraySegmentRuleDO::getVersionId, versionId)
                .eq(GraySegmentRuleDO::getIsDeleted, false)
                .set(GraySegmentRuleDO::getIsDeleted, true);
        return graySegmentRuleMapper.update(null, wrapper);
    }

    // ========== 预览令牌 ==========

    @Override
    public int insertPreviewToken(GrayPreviewTokenDO token) {
        return grayPreviewTokenMapper.insert(token);
    }

    @Override
    public int insertPreviewTokenBatch(List<GrayPreviewTokenDO> tokens) {
        int count = 0;
        for (GrayPreviewTokenDO token : tokens) {
            count += grayPreviewTokenMapper.insert(token);
        }
        return count;
    }

    @Override
    public GrayPreviewTokenDO selectByToken(String token) {
        QueryWrapper<GrayPreviewTokenDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayPreviewTokenDO::getToken, token)
                .eq(GrayPreviewTokenDO::getIsDeleted, false)
                .last("limit 1");
        return grayPreviewTokenMapper.selectOne(wrapper);
    }

    @Override
    public List<GrayPreviewTokenDO> selectTokensByVersionId(Long versionId) {
        QueryWrapper<GrayPreviewTokenDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayPreviewTokenDO::getVersionId, versionId)
                .eq(GrayPreviewTokenDO::getIsDeleted, false);
        return grayPreviewTokenMapper.selectList(wrapper);
    }

    @Override
    public int softDeleteTokensByVersionId(Long versionId) {
        UpdateWrapper<GrayPreviewTokenDO> wrapper = new UpdateWrapper<>();
        wrapper.lambda()
                .eq(GrayPreviewTokenDO::getVersionId, versionId)
                .eq(GrayPreviewTokenDO::getIsDeleted, false)
                .set(GrayPreviewTokenDO::getIsDeleted, true);
        return grayPreviewTokenMapper.update(null, wrapper);
    }

    // ========== 灰度版本查询 ==========

    @Override
    public ArticleVersionDO selectActiveGrayByArticleId(Long articleId) {
        QueryWrapper<ArticleVersionDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(ArticleVersionDO::getArticleId, articleId)
                .eq(ArticleVersionDO::getStatus, ArticleVersionStatusEnum.GRAY.getCode())
                .eq(ArticleVersionDO::getIsDeleted, false)
                .orderByDesc(ArticleVersionDO::getVersionNum)
                .last("limit 1");
        return articleVersionMapper.selectOne(wrapper);
    }

    // ========== 曝光日志 ==========

    @Override
    public int insertExposureLog(GrayExposureLogDO logDO) {
        return grayExposureLogMapper.insert(logDO);
    }

    @Override
    public long countExposuresByVersionId(Long versionId) {
        QueryWrapper<GrayExposureLogDO> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(GrayExposureLogDO::getVersionId, versionId);
        return grayExposureLogMapper.selectCount(wrapper);
    }

    @Override
    public long countExposuresByVersionIdAndAccessType(Long versionId, Integer accessType) {
        QueryWrapper<GrayExposureLogDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayExposureLogDO::getVersionId, versionId)
                .eq(GrayExposureLogDO::getAccessType, accessType);
        return grayExposureLogMapper.selectCount(wrapper);
    }

    // ========== 反馈 ==========

    @Override
    public int insertFeedback(GrayFeedbackDO feedback) {
        return grayFeedbackMapper.insert(feedback);
    }

    @Override
    public List<GrayFeedbackDO> selectFeedbackByVersionId(Long versionId) {
        QueryWrapper<GrayFeedbackDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayFeedbackDO::getVersionId, versionId)
                .eq(GrayFeedbackDO::getIsDeleted, false)
                .orderByDesc(GrayFeedbackDO::getCreateTime);
        return grayFeedbackMapper.selectList(wrapper);
    }

    @Override
    public long countFeedbackByVersionId(Long versionId) {
        QueryWrapper<GrayFeedbackDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayFeedbackDO::getVersionId, versionId)
                .eq(GrayFeedbackDO::getIsDeleted, false);
        return grayFeedbackMapper.selectCount(wrapper);
    }

    @Override
    public long countFeedbackByVersionIdAndType(Long versionId, Integer feedbackType) {
        QueryWrapper<GrayFeedbackDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayFeedbackDO::getVersionId, versionId)
                .eq(GrayFeedbackDO::getFeedbackType, feedbackType)
                .eq(GrayFeedbackDO::getIsDeleted, false);
        return grayFeedbackMapper.selectCount(wrapper);
    }

    // ========== 回滚审计 ==========

    @Override
    public int insertRollbackAudit(GrayRollbackAuditDO audit) {
        return grayRollbackAuditMapper.insert(audit);
    }

    @Override
    public List<GrayRollbackAuditDO> selectRollbackAuditsByArticleId(Long articleId) {
        QueryWrapper<GrayRollbackAuditDO> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(GrayRollbackAuditDO::getArticleId, articleId)
                .orderByDesc(GrayRollbackAuditDO::getCreateTime);
        return grayRollbackAuditMapper.selectList(wrapper);
    }
}
