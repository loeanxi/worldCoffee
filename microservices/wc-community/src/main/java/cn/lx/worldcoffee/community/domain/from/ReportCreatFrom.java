package cn.lx.worldcoffee.community.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class ReportCreatFrom {
    @NotBlank(message = "举报原因不能为空")
    @Length(max = 500, message = "最多500字")
    private String reason;
}
