package com.shanyangcode.common.utils;

import com.shanyangcode.common.constant.CommonConstant;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * User online-status helper
 * <p>
 * How it works: when a user drops the WebSocket connection, RealTimeService writes
 * {@code user:offline:{userId}} to Redis with the offline timestamp (see WebSocketHandler#saveOfflineTime).
 * The presence of that key means the user has gone offline.
 * <p>
 * Used when pushing system notifications, to decide whether to fall back to offline persistence.
 */
public final class OnlineStatusUtil {

    private OnlineStatusUtil() {
        // Utility class; instantiation is not allowed
    }

    /**
     * Tells whether the user is offline
     *
     * @param stringRedisTemplate the Redis template
     * @param userId              the user id
     * @return true when the user is offline
     */
    public static boolean isUserOffline(StringRedisTemplate stringRedisTemplate, Long userId) {
        if (stringRedisTemplate == null || userId == null) {
            return true;
        }
        String key = CommonConstant.OFFLINE_KEY_REDIS + userId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
}
