package cn.lx.worldcoffee.community.domain.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedEventCreateFrom {
    @NotNull(message = "postId is required")
    private Long postId;

    @NotBlank(message = "eventType is required")
    private String eventType;

    private String source;
    private String sessionId;
    private Long dwellMs;
}
