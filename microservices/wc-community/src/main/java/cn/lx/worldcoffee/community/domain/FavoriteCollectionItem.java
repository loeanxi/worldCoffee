package cn.lx.worldcoffee.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("favorite_collection_item")
public class FavoriteCollectionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long collectionId;
    private Long userId;
    private Long postId;
    private LocalDateTime createTime;
}
