package cn.lx.worldcoffee.module.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.lx.worldcoffee.module.ai.Prompts;
import cn.lx.worldcoffee.module.ai.tool.PostTool;
import cn.lx.worldcoffee.module.ai.tool.ProductTool;
import redis.clients.jedis.JedisPooled;

@Configuration
public class SpringAIConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
                                  VectorStore vectorStore, ProductTool productTool, PostTool postTool){
        return builder
                .defaultSystem(Prompts.COFFEE_EXPERT)
                .defaultTools(productTool, postTool)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(0.75d)
                                        .topK(3)
                                        .build())
                                .build()
                )
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository){
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(new JedisPooled("localhost", 6379), embeddingModel)
                .initializeSchema(true)
                .indexName("coffee-knowledge")
                .prefix("knowledge:")
                .build();
    }
}
