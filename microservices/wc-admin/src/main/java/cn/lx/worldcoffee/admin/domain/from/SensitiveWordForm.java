package cn.lx.worldcoffee.admin.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class SensitiveWordForm {
    @NotBlank(message = "敏感词不能为空")
    @Length(max = 80, message = "敏感词最多80字")
    private String word;

    private String category;

    /**
     * 1 warn/review, 2 reject.
     */
    private Integer action;
}
