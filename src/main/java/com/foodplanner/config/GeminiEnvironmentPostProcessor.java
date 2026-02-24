package com.foodplanner.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Prevents Spring AI Google GenAI auto-configuration from failing at startup when
 * GEMINI_API_KEY is not set.
 *
 * <p>Spring AI 2.0.0-M2's {@code GoogleGenAiChatAutoConfiguration.googleGenAiClient()} throws
 * "Google GenAI project-id must be set!" when the api-key is empty and no Vertex AI
 * project-id is provided. This failure cascades through {@code ChatClientAutoConfiguration}
 * to {@code GeminiService}, preventing the app from starting even though the service is
 * designed to fall back gracefully when AI is unconfigured.
 *
 * <p>When {@code GEMINI_API_KEY} is absent we disable both auto-configurations by injecting
 * two properties at the highest priority before Spring evaluates any {@code @ConditionalOn*}
 * conditions:
 * <ul>
 *   <li>{@code spring.ai.model.chat=none} — skips {@code GoogleGenAiChatAutoConfiguration}
 *       (its {@code @ConditionalOnProperty(name="spring.ai.model.chat", havingValue="google-genai",
 *       matchIfMissing=true)} then fails to match)
 *   <li>{@code spring.ai.chat.client.enabled=false} — skips {@code ChatClientAutoConfiguration}
 *       (its {@code @ConditionalOnProperty(prefix="spring.ai.chat.client", name="enabled",
 *       havingValue="true", matchIfMissing=true)} then fails to match)
 * </ul>
 * With both configurations skipped, no {@code ChatClient.Builder} bean is created, so
 * {@code GeminiService}'s {@code @Autowired(required=false) ChatClient.Builder} receives
 * {@code null} and the app starts with AI features disabled (sample data fallback).
 */
public class GeminiEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "gemini-ai-disabled",
                    Map.of(
                            "spring.ai.model.chat", "none",
                            "spring.ai.chat.client.enabled", "false"
                    )
            ));
        }
    }
}
