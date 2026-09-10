package com.shanyangcode.realtimeservice.consumer;

import java.util.Map;

import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.utils.OnlineStatusUtil;
import com.shanyangcode.realtimeservice.websocket.ChannelManager;

import cn.hutool.json.JSONUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * System notification consumer
 * <p>
 * Responsibilities:
 * - Consumes the system notifications published by UserService
 * - Forwards the complete message over WebSocket to online users
 * - Follows the notification design used across this IM project
 * - Supports several notification types (new session, friend request, group invite, ...)
 * <p>
 * Message types:
 * - 101: friend request received
 * - 102: new session created
 * - 103: new group session created
 */
@Slf4j
@Component
public class SystemNotificationConsumer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public SystemNotificationConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                      StringRedisTemplate stringRedisTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Consumes a system notification message
     * <p>
     * Processing steps:
     * 1. Receive the Kafka message (a complete SystemNotificationMessage JSON payload)
     * 2. Parse it to obtain the receiver id
     * 3. Look up the matching WebSocket channel for that receiver
     * 4. Forward the complete message over the WebSocket
     * 5. Publish to the Kafka store-notification-topic for persistence
     * <p>
     * Message shape:
     * {
     * "messageId": "msg_1699999999000_123456789",
     * "sessionId": 123,
     * "senderId": 0,
     * "receiverId": 1,
     * "type": 102,
     * "sessionType": 0,
     * "timestamp": 1699999999000,
     * "body": {
     * "sessionName": "Alice",
     * "avatar": "http://..."
     * }
     * }
     *
     * @param message the Kafka message (a complete SystemNotificationMessage JSON payload)
     */
    @KafkaListener(
            topics = CommonConstant.KAFKA_SYSTEM_NOTIFICATION_TOPIC,
            groupId = "system-notification-consumer-group",
            concurrency = "3"
    )
    public void consumeSystemNotification(String message) {
        try {
            log.debug("System notification received: {}", message);

            // 1. Parse the message to obtain the receiver id and messageId (used for logging)
            Map<String, Object> notificationMap = JSONUtil.toBean(message, Map.class);
            String messageId = (String) notificationMap.get("messageId");
            Integer type = (Integer) notificationMap.get("type");
            Long receiverId = Long.parseLong(notificationMap.get("receiverId").toString());

            // 2. Look up the user's WebSocket channel (via the static accessor)
            Channel channel = ChannelManager.getChannelByUserId(String.valueOf(receiverId));

            if (channel != null && channel.isActive()) {
                // 3. The user is online, forward the complete message straight away
                TextWebSocketFrame frame = new TextWebSocketFrame(message);
                channel.writeAndFlush(frame).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.info("System notification pushed, messageId: {}, receiverId: {}, type: {}",
                                messageId, receiverId, type);
                    } else {
                        log.error("System notification push failed, messageId: {}, receiverId: {}, type: {}, error: {}",
                                messageId, receiverId, type,
                                future.cause() != null ? future.cause().getMessage() : "unknown error");
                    }
                });
            } else {
                // 4. The user is offline (no usable channel), so always fall back to the persistence topic.
                //    Note: the user:offline: marker is only written when a user disconnects, so a user who
                //    never connected has no marker; gating persistence on it would silently drop notifications.
                boolean offlineMarked = OnlineStatusUtil.isUserOffline(stringRedisTemplate, receiverId);
                log.info("User is offline, publishing the system notification to Kafka for persistence, messageId: {}, receiverId: {}, type: {}, offline marker present: {}",
                        messageId, receiverId, type, offlineMarked);

                kafkaTemplate.send(CommonConstant.KAFKA_STORE_NOTIFICATION_TOPIC, message)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("System notification persisted successfully, messageId: {}, receiverId: {}, type: {}",
                                        messageId, receiverId, type);
                            } else {
                                log.error("Failed to persist the system notification, messageId: {}, receiverId: {}, type: {}, error: {}",
                                        messageId, receiverId, type, ex.getMessage());
                            }
                        });
            }

        } catch (Exception e) {
            log.error("Failed to handle the system notification, message: {}, error: {}", message, e.getMessage(), e);
        }
    }
}