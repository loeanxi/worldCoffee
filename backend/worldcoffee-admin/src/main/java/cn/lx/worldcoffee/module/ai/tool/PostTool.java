package cn.lx.worldcoffee.module.ai.tool;

import cn.lx.worldcoffee.module.coffee.service.CoffeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostTool {

    private final CoffeeService coffeeService;

    @Tool(description = "搜索咖啡帖子，根据关键词匹配标题、咖啡名、品牌、内容，返回帖子标题和作者")
    public String searchPosts(String keyword) {
        var posts = coffeeService.search(keyword, 1, 5);
        if (posts.isEmpty()) return "没有找到相关的帖子";

        StringBuilder sb = new StringBuilder();
        for (var p : posts) {
            sb.append("- ").append(p.getTitle());
            if (p.getUsername() != null) sb.append("（by ").append(p.getUsername()).append("）");
            sb.append("\n");
        }
        return sb.toString();
    }
}
