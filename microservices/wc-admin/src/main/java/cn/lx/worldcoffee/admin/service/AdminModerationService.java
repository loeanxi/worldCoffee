package cn.lx.worldcoffee.admin.service;

import cn.hutool.json.JSONUtil;
import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.community.dao.CoffeePostDao;
import cn.lx.worldcoffee.community.dao.CoffeePostTopicDao;
import cn.lx.worldcoffee.community.dao.CoffeeTopicDao;
import cn.lx.worldcoffee.community.dao.PostReportDao;
import cn.lx.worldcoffee.community.domain.CoffeePost;
import cn.lx.worldcoffee.community.domain.CoffeePostTopic;
import cn.lx.worldcoffee.community.domain.CoffeeTopic;
import cn.lx.worldcoffee.community.domain.PostReport;
import cn.lx.worldcoffee.community.domain.from.ReportHandleFrom;
import cn.lx.worldcoffee.community.domain.vo.ReportReviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final PostReportDao postReportDao;
    private final CoffeePostDao postDao;
    private final CoffeePostTopicDao postTopicDao;
    private final CoffeeTopicDao topicDao;

    public List<ReportReviewVO> listReports(Integer status, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<PostReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(PostReport::getStatus, status);
        wrapper.orderByAsc(PostReport::getStatus)
                .orderByDesc(PostReport::getCreateTime)
                .last("LIMIT " + (safePage - 1) * safeSize + "," + safeSize);
        return postReportDao.selectList(wrapper).stream()
                .map(this::toReportReviewVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, ReportHandleFrom from) {
        PostReport report = postReportDao.selectById(reportId);
        if (report == null) throw new ServiceException("举报记录不存在");
        String action = from.getAction() == null ? "" : from.getAction().trim().toUpperCase();
        if (!"IGNORE".equals(action) && !"REMOVE_POST".equals(action)) {
            throw new ServiceException("不支持的审核动作");
        }

        if ("REMOVE_POST".equals(action)) {
            CoffeePost post = postDao.selectById(report.getPostId());
            if (post != null) {
                post.setStatus(0);
                postDao.updateById(post);
                detachPostTopics(post.getId());
            }
        }

        report.setStatus("REMOVE_POST".equals(action) ? 2 : 1);
        report.setRemark(from.getRemark());
        report.setHandleTime(LocalDateTime.now());
        postReportDao.updateById(report);
    }

    private ReportReviewVO toReportReviewVO(PostReport report) {
        CoffeePost post = postDao.selectById(report.getPostId());
        String statusText = report.getStatus() == null || report.getStatus() == 0 ? "pending"
                : report.getStatus() == 2 ? "post removed" : "ignored";
        return ReportReviewVO.builder()
                .id(report.getId())
                .postId(report.getPostId())
                .postTitle(post == null ? null : post.getTitle())
                .postContent(post == null ? null : post.getContent())
                .postImages(post == null ? List.of() : parseImages(post.getImages()))
                .postAuthorId(post == null ? null : post.getUserId())
                .reporterId(report.getReporterId())
                .reason(report.getReason())
                .status(report.getStatus())
                .statusText(statusText)
                .remark(report.getRemark())
                .createTime(report.getCreateTime())
                .handleTime(report.getHandleTime())
                .build();
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return List.of();
        try {
            return JSONUtil.toList(imagesJson, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void detachPostTopics(Long postId) {
        List<CoffeePostTopic> oldRelations = postTopicDao.selectList(new LambdaQueryWrapper<CoffeePostTopic>()
                .eq(CoffeePostTopic::getPostId, postId));
        Set<Long> topicIds = oldRelations.stream()
                .map(CoffeePostTopic::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        postTopicDao.delete(new LambdaQueryWrapper<CoffeePostTopic>().eq(CoffeePostTopic::getPostId, postId));
        refreshTopicPostCounts(topicIds);
    }

    private void refreshTopicPostCounts(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long topicId : topicIds) {
            CoffeeTopic topic = topicDao.selectById(topicId);
            if (topic == null) continue;
            Long count = postTopicDao.selectCount(new LambdaQueryWrapper<CoffeePostTopic>()
                    .eq(CoffeePostTopic::getTopicId, topicId));
            topic.setPostCount(count == null ? 0 : count.intValue());
            topic.setUpdateTime(now);
            topicDao.updateById(topic);
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) return 10;
        return Math.min(Math.max(size, 1), 50);
    }
}
