package com.foodplanner.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    /**
     * Expose a ChatClient bean only when Spring AI's Google GenAI
     * auto-configuration has provided a ChatClient.Builder (i.e. when
     * spring.ai.google.genai.api-key is configured).  When the key is absent
     * the bean is simply not registered and GeminiService falls back to its
     * built-in sample data.
     */
    @Bean
    @ConditionalOnBean(ChatClient.Builder.class)
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
