package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatorStatsVO {
    private Integer postCount;
    private Integer totalLikes;
    private Integer totalFavorites;
    private Integer totalComments;
    private Integer totalImpressions;
    private Integer totalClicks;
    private Integer totalDwellSeconds;
    private Integer totalReports;
}
