package cn.lx.worldcoffee.community.domain.from;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class NotInterestedForm {
    private String sessionId;
    private String reasonType;

    @Length(max = 120, message = "原因最多120字")
    private String reason;
}
