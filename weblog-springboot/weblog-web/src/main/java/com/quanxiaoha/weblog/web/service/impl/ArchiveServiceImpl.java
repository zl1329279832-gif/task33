package com.quanxiaoha.weblog.web.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.quanxiaoha.weblog.common.PageResponse;
import com.quanxiaoha.weblog.common.Response;
import com.quanxiaoha.weblog.common.constant.Constants;
import com.quanxiaoha.weblog.common.domain.dos.*;
import com.quanxiaoha.weblog.common.domain.mapper.ArticleMapper;
import com.quanxiaoha.weblog.web.convert.ArticleConvert;
import com.quanxiaoha.weblog.web.dao.ArticleDao;
import com.quanxiaoha.weblog.web.dao.ArticleVersionDao;
import com.quanxiaoha.weblog.web.dao.UserDao;
import com.quanxiaoha.weblog.web.model.vo.archive.QueryArchiveItemRspVO;
import com.quanxiaoha.weblog.web.model.vo.archive.QueryArchivePageListReqVO;
import com.quanxiaoha.weblog.web.model.vo.archive.QueryArchivePageListRspVO;
import com.quanxiaoha.weblog.web.service.ArchiveService;
import com.quanxiaoha.weblog.web.service.GrayResolutionContext;
import com.quanxiaoha.weblog.web.service.GrayResolutionResult;
import com.quanxiaoha.weblog.web.service.GrayResolutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-04-17 12:08
 * @description: TODO
 **/
@Service
@Slf4j
public class ArchiveServiceImpl extends ServiceImpl<ArticleMapper, ArticleDO> implements ArchiveService {

    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private ArticleConvert articleConvert;
    @Autowired
    private GrayResolutionService grayResolutionService;
    @Autowired
    private UserDao userDao;

    @Override
    public Response queryArchive(QueryArchivePageListReqVO queryArchivePageListReqVO) {
        Long current = queryArchivePageListReqVO.getCurrent();
        Long size = queryArchivePageListReqVO.getSize();

        IPage<ArticleDO> articleDOIPage = articleDao.queryArticlePageList(current, size);
        List<ArticleDO> records = articleDOIPage.getRecords();

        List<QueryArchivePageListRspVO> list = Lists.newArrayList();
        List<QueryArchiveItemRspVO> itemRspVOList = null;
        if (!CollectionUtils.isEmpty(records)) {
            itemRspVOList = records.stream()
                    .map(articleDO -> articleConvert.convert2Archive(articleDO))
                    .collect(Collectors.toList());

            // 灰度覆盖
            GrayResolutionContext grayCtx = buildGrayContext(queryArchivePageListReqVO.getPreviewToken());
            for (QueryArchiveItemRspVO item : itemRspVOList) {
                GrayResolutionResult result = grayResolutionService.resolve(item.getId(), grayCtx);
                if (result.isGrayHit()) {
                    ArticleVersionDO grayVersion = result.getGrayVersion();
                    item.setTitle(grayVersion.getTitle());
                    item.setTitleImage(grayVersion.getTitleImage());
                    grayResolutionService.logExposure(result, item.getId(), grayCtx.getUserId());
                }
            }

            Map<String, List<QueryArchiveItemRspVO>> map = itemRspVOList.stream().collect(Collectors.groupingBy(QueryArchiveItemRspVO::getCreateMonth));
            Map<String, List<QueryArchiveItemRspVO>> sortedMap = new TreeMap<>(new MonthKeyComparator());
            sortedMap.putAll(map);

            sortedMap.forEach((k, v) -> list.add(QueryArchivePageListRspVO.builder().month(k).articles(v).build()));
        }
        return PageResponse.success(articleDOIPage, list);
    }

    private GrayResolutionContext buildGrayContext(String previewToken) {
        Long userId = null;
        String username = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            username = ((UserDetails) auth.getPrincipal()).getUsername();
            userId = userDao.selectUserIdByUsername(username);
        }
        return GrayResolutionContext.builder()
                .userId(userId)
                .username(username)
                .previewToken(previewToken)
                .build();
    }

    class MonthKeyComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            // 使用 YearMonth 类将字符串解析成日期，并根据日期进行倒序排序
            YearMonth ym1 = YearMonth.parse(o1);
            YearMonth ym2 = YearMonth.parse(o2);
            return ym2.compareTo(ym1);
        }
    }
}
