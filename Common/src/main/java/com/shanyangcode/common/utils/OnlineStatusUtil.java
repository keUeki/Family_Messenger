package com.shanyangcode.common.utils;

import com.shanyangcode.common.constant.CommonConstant;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用户在线状态工具类
 * <p>
 * 判断依据：用户断开 WebSocket 连接时，RealTimeService 会向 Redis 写入
 * {@code user:offline:{userId}} 记录离线时间戳（见 WebSocketHandler#saveOfflineTime）。
 * 该键存在即表示用户曾经离线。
 * <p>
 * 使用场景：系统通知推送时判断是否需要转入离线持久化流程。
 */
public final class OnlineStatusUtil {

    private OnlineStatusUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 判断用户是否离线
     *
     * @param stringRedisTemplate Redis 操作模板
     * @param userId              用户 ID
     * @return true 表示用户处于离线状态
     */
    public static boolean isUserOffline(StringRedisTemplate stringRedisTemplate, Long userId) {
        if (stringRedisTemplate == null || userId == null) {
            return true;
        }
        String key = CommonConstant.OFFLINE_KEY_REDIS + userId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
}
