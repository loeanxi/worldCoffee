package cn.lx.worldcoffee.community.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FavoriteCollectionVO {
    private Long id;
    private String name;
    private String description;
    private Integer itemCount;
    private Boolean defaultCollection;
    private LocalDateTime updateTime;
}
