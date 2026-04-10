package com.test.rag.config;

import org.apache.tika.parser.AutoDetectParser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Spring AI beans (ChatClient, EmbeddingModel, VectorStore).
 * EmbeddingModel and VectorStore are auto-configured by their starters;
 * only ChatClient requires an explicit builder call.
 */
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public AutoDetectParser autoDetectParser() {
        return new AutoDetectParser();
    }
}
