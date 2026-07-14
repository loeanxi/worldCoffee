package cn.lx.worldcoffee.community.domain.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportHandleFrom {
    @NotBlank(message = "action is required")
    private String action;
    private String remark;
}
