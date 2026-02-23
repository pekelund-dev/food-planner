package com.foodplanner.config;

/**
 * No explicit ChatClient bean needed here.
 *
 * Spring AI auto-configures ChatClient.Builder when spring.ai.google.genai.api-key
 * is set. GeminiService injects ChatClient.Builder directly with required=false so it
 * works both with and without the key. We previously used @ConditionalOnBean here but
 * that condition is evaluated before Spring Boot auto-configurations run, so
 * ChatClient.Builder was never visible and the chatClient bean was never created.
 */
public final class GeminiConfig {
    private GeminiConfig() {}
}
