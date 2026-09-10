package com.shanyangcode.aiservice.Monitor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorContextHolder {

    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new InheritableThreadLocal<>();

    /**
     * Sets the monitoring context
     */
    public static void setContext(MonitorContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * Returns the current monitoring context
     */
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * Clears the monitoring context
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}