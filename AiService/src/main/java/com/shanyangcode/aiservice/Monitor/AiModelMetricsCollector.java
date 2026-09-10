package com.shanyangcode.aiservice.Monitor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiModelMetricsCollector {

    @Resource
    private MeterRegistry meterRegistry;

    // Cache the meters already created so they are not rebuilt (one cache per meter type)
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();

    /**
     * Records the request count
     */
    public void recordRequest(String userId, String sessionId, String modelName, String status) {
        // Important: Micrometer tags may not be null
        String safeUserId = (userId == null) ? "unknown" : userId;
        String safeSessionId = (sessionId == null) ? "unknown" : sessionId;
        String safeModel = (modelName == null) ? "unknown" : modelName;
        String safeStatus = (status == null) ? "unknown" : status;

        String key = String.format("%s_%s_%s_%s", safeUserId, safeSessionId, safeModel, safeStatus);
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_requests_total")
                        .tag("user_id", safeUserId)
                        .tag("session_id", safeSessionId)
                        .tag("model_name", safeModel)
                        .tag("status", safeStatus)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Records an error
     */
    public void recordError(String userId, String sessionId, String modelName, String errorMessage) {
        String key = String.format("%s_%s_%s_%s", userId, sessionId, modelName, errorMessage);
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_errors_total")
                        .description("Number of AI model errors")
                        .tag("user_id", userId)
                        .tag("session_id", sessionId)
                        .tag("model_name", modelName)
                        .tag("error_message", errorMessage)
                        .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Records token consumption
     */
    public void recordTokenUsage(String userId, String sessionId, String modelName,
                                 String tokenType, long tokenCount) {
        String key = String.format("%s_%s_%s_%s", userId, sessionId, modelName, tokenType);
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                Counter.builder("ai_model_tokens_total")
                        .description("Total tokens consumed by the AI model")
                        .tag("user_id", userId)
                        .tag("session_id", sessionId)
                        .tag("model_name", modelName)
                        .tag("token_type", tokenType)
                        .register(meterRegistry)
        );
        counter.increment(tokenCount);
    }

    /**
     * Records the response time
     */
    public void recordResponseTime(String userId, String sessionId, String modelName, Duration duration) {
        String key = String.format("%s_%s_%s", userId, sessionId, modelName);
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                Timer.builder("ai_model_response_duration_seconds")
                        .description("AI model response time")
                        .tag("user_id", userId)
                        .tag("session_id", sessionId)
                        .tag("model_name", modelName)
                        .register(meterRegistry)
        );
        timer.record(duration);
    }
}