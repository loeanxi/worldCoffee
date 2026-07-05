package cn.lx.worldcoffee.module.ai.service;

import cn.lx.worldcoffee.common.exception.ServiceException;
import cn.lx.worldcoffee.module.ai.dao.AiConversationDao;
import cn.lx.worldcoffee.module.ai.entity.AiConversation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiConversationDao aiConversationDao;
    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;

    public Long getCurrentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return Long.valueOf(auth.getPrincipal().toString());
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Flux<String> chat(String message, String chatId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");

        if (aiConversationDao.selectCount(
                new LambdaQueryWrapper<AiConversation>().eq(AiConversation::getChatId, chatId)) == 0) {
            AiConversation conv = new AiConversation();
            conv.setChatId(chatId);
            conv.setUserId(userId);
            String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
            conv.setTitle(title);
            conv.setCreatedAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            aiConversationDao.insert(conv);
        } else {
            aiConversationDao.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getChatId, chatId)
                    .set(AiConversation::getUpdatedAt, LocalDateTime.now()));
        }

        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId))
                .stream()
                .chatResponse()
                .map(response -> response.getResult().getOutput().getText())
                .onErrorResume(ex -> {
                    System.out.println("[AiService] AI 调用失败: " + ex.getMessage());
                    return Flux.just("（AI 服务繁忙，请稍后再试：" + ex.getMessage() + "）");
                });
    }

    public List<AiConversation> listConversations() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");
        return aiConversationDao.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdatedAt)
        );
    }

    public void deleteConversation(String chatId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new ServiceException("请先登录");
        AiConversation conv = aiConversationDao.selectOne(
                new LambdaQueryWrapper<AiConversation>().eq(AiConversation::getChatId, chatId));
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new ServiceException("无权操作");
        }
        aiConversationDao.deleteById(conv.getId());
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", chatId);
    }

    public List<Map<String, Object>> getConversationMessages(String chatId) {
        return jdbcTemplate.query(
                "SELECT content, type, timestamp FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ? ORDER BY timestamp ASC",
                new Object[]{chatId},
                (rs, rowNum) -> Map.of(
                        "role", rs.getString("type"),
                        "content", rs.getString("content"),
                        "time", rs.getTimestamp("timestamp").toLocalDateTime().toString()
                ));
    }

    public void uploadKnowledge(String text, String title) {
        Document doc = new Document(text, Map.of("title", title));
        vectorStore.add(List.of(doc));
    }
}
