package cn.lx.worldcoffee.module.ai.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.module.ai.entity.AiConversation;
import cn.lx.worldcoffee.module.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(@RequestBody String message,
                             @RequestParam(defaultValue = "default") String chatId) {
        return aiService.chat(message, chatId);
    }

    @GetMapping("/conversations")
    public Result<List<AiConversation>> listConversations() {
        return Result.success(aiService.listConversations());
    }

    @DeleteMapping("/conversations/{chatId}")
    public Result<Void> deleteConversation(@PathVariable String chatId) {
        aiService.deleteConversation(chatId);
        return Result.success(null);
    }

    @GetMapping("/conversations/{chatId}/messages")
    public Result<List<Map<String, Object>>> getConversationMessages(@PathVariable String chatId) {
        return Result.success(aiService.getConversationMessages(chatId));
    }

    @PostMapping("/knowledge/upload")
    public Result<Void> uploadKnowledge(@RequestBody String text,
                                        @RequestParam(defaultValue = "咖啡知识") String title) {
        aiService.uploadKnowledge(text, title);
        return Result.success(null);
    }
}
