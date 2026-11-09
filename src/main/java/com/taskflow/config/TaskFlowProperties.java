package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow")
public record TaskFlowProperties(String timezone, Jwt jwt, Ai ai, Qdrant qdrant, N8n n8n) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Ai(String provider, String openaiApiKey, int ragTopK, double minScore) {}

    public record Qdrant(String url, String apiKey, String collection) {}

    public record N8n(String apiKey, String taskAssignedWebhookUrl) {}
}
