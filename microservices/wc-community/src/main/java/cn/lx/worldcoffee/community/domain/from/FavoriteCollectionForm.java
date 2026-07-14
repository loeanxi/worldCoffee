package cn.lx.worldcoffee.community.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class FavoriteCollectionForm {
    @NotBlank(message = "收藏夹名称不能为空")
    @Length(max = 40, message = "收藏夹名称最多40字")
    private String name;

    @Length(max = 120, message = "收藏夹描述最多120字")
    private String description;
}
