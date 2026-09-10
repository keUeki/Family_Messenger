package com.shanyangcode.aiservice.Monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;




@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    // Key used to carry the start time from the request to the response
    private static final String START_TIME_KEY = "request_start_time";

    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;


    @Override
    public void onRequest(ChatModelRequestContext requestContext) {

        requestContext.attributes().put(START_TIME_KEY, Instant.now());
        // Read the details from the monitoring context
        MonitorContext context = MonitorContextHolder.getContext();

        if (context == null) {
            // Log the error
            log.error("MonitorContext is null when processing request");
            return;
        }
        String userId = context.getUserId() != null ? context.getUserId().toString() : "unknown";
        String sessionId = context.getSessionId() != null ? context.getSessionId().toString() : "unknown";
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        // Resolve the model name
        String modelName = requestContext.chatRequest().modelName();

        log.info(">>> AI request started | user: {} | session: {} | model: {}", userId, sessionId, modelName);
        // Record the request metric
        aiModelMetricsCollector.recordRequest(userId, sessionId, modelName, "started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        String modelName = responseContext.chatResponse().metadata().modelName();
        // Read the monitoring details from the attributes (stored by onRequest)
        Map<Object, Object> attributes = responseContext.attributes();
        // 1. Read the details from the monitoring context
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);

        if (context == null) {
            log.warn("Monitoring context is missing; cannot record the response metric - Model: {}", responseContext.chatResponse().modelName());
            return;
        }
        
        String userId = context.getUserId().toString();
        String sessionId = context.getSessionId().toString();
        // 2. Compute the elapsed time
        Duration durationMs = calculateDuration(attributes);

        // 3. Read the token usage
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();

        // 4. Emit the formatted log line
        log.info("<<< AI request succeeded | user: {} | session: {} | model: {} | elapsed: {}ms | Tokens: [In:{}, Out:{}, Total:{}]", userId, sessionId, modelName, durationMs.toMillis(), tokenUsage != null ? tokenUsage.inputTokenCount() : 0, tokenUsage != null ? tokenUsage.outputTokenCount() : 0, tokenUsage != null ? tokenUsage.totalTokenCount() : 0);
        aiModelMetricsCollector.recordRequest(userId, sessionId, modelName, "success");
        aiModelMetricsCollector.recordResponseTime(userId, sessionId, modelName, durationMs);

        if (tokenUsage != null) {
            aiModelMetricsCollector.recordTokenUsage(userId, sessionId, modelName, "input", tokenUsage.inputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, sessionId, modelName, "output", tokenUsage.outputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, sessionId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        MonitorContext context = MonitorContextHolder.getContext();

        Map<Object, Object> attributes = errorContext.attributes();
        Duration durationMs = calculateDuration(attributes);

        if (context == null) {
            // Fall back to the attributes
            context = (MonitorContext) errorContext.attributes().get(MONITOR_CONTEXT_KEY);
        }

        if (context == null) {
            log.warn("Monitoring context is missing; cannot record the error metric - Error: {}", errorContext.error().getMessage());
            return;
        }
        
        String userId = context.getUserId().toString();
        String sessionId = context.getSessionId().toString();
        String modelName = errorContext.chatRequest().modelName();
        String errorMessage = errorContext.error().getMessage();
        log.error("AI request failed | elapsed: {}ms | cause: {}", durationMs.toMillis(), errorContext.error().getMessage());

        // Record the failed request
        aiModelMetricsCollector.recordRequest(userId, sessionId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, sessionId, modelName, errorMessage);
        aiModelMetricsCollector.recordResponseTime(userId, sessionId, modelName, durationMs);
    }

    /**
     * Reads the start time from the attributes and returns the elapsed interval
     */
    private Duration calculateDuration(Map<Object, Object> attributes) {
        Instant startTime = (Instant) attributes.get(START_TIME_KEY);
        if (startTime != null) {
            return Duration.between(startTime, Instant.now());
        }
        return Duration.ZERO;
    }
}