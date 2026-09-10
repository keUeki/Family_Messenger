package com.shanyangcode.userservice.service.impl;


import com.shanyangcode.common.constant.MessageTypeConstant;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.utils.SnowflakeUtil;
import com.shanyangcode.userservice.constants.KafkaTopicConstant;
import com.shanyangcode.userservice.model.dto.FriendApplicationNotificationDTO;
import com.shanyangcode.userservice.model.dto.GroupKickNotificationDTO;
import com.shanyangcode.userservice.model.dto.NewGroupSessionNotificationDTO;
import com.shanyangcode.userservice.model.dto.NewSessionNotificationDTO;
import com.shanyangcode.userservice.model.dto.SystemNotificationMessage;
import com.shanyangcode.userservice.service.NotificationService;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Notification push service implementation
 * <p>
 * Implementation notes:
 * - Publishes system notifications asynchronously over Kafka
 * - Replaces the earlier synchronous HTTP calls
 * - Follows the notification design used across this IM project
 * - Improves throughput and reliability
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public NotificationServiceImpl(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    /**
     * Pushes a friend request notification
     *
     * Steps:
     * 1. Build the complete SystemNotificationMessage
     * 2. Generate a unique messageId
     * 3. Set senderId to the requester's id
     * 4. Publish it to the Kafka system-notification-topic
     * 5. RealTimeService consumes it and pushes it to the online user
     *
     * @param userId       the id of the user receiving the notification
     * @param notification the friend request notification payload
     */
    @Override
    public void pushNewApply(Long userId, FriendApplicationNotificationDTO notification) {
        try {
            SystemNotificationMessage message = new SystemNotificationMessage();
            message.setMessageId(generateMessageId());
            message.setSessionId(null); // a friend request does not belong to any session
            message.setSenderId(notification.getApplyUserId()); // the requester's id
            message.setReceiverId(userId);
            message.setType(MessageTypeConstant.TYPE_SYSTEM_NEW_APPLY); // 101
            message.setSessionType(null); // not tied to a session
            message.setTimestamp(System.currentTimeMillis());

            // Build the body
            Map<String, Object> body = new HashMap<>();
            body.put("nickname", notification.getApplyUserName());
            body.put("avatar", notification.getApplyFriendAvatar());
            body.put("msg", notification.getMessage());
            message.setBody(body);

            sendNotification(message, "friend request notification");

        } catch (Exception e) {
            log.error("Failed to publish the friend request notification, user id: {}, error: {}", userId, e.getMessage(), e);
        }
    }


    /**
     * Pushes a new-session notification
     * <p>
     * Steps:
     * 1. Build the complete SystemNotificationMessage
     * 2. sessionId and sessionType sit at the top level; the body carries only the session name and avatar
     * 3. senderId is the party who accepted, receiverId the party who made the request
     * 4. Publish it to the Kafka system-notification-topic
     * 5. RealTimeService consumes it and pushes it to the online user
     *
     * @param senderId     the id of the user who triggered the session (the party who accepted)
     * @param userId       the id of the user receiving the notification (the party who made the request)
     * @param sessionId    the session id
     * @param sessionType  the session type (0 one-to-one, 1 group, 2 bot)
     * @param notification the new-session notification payload
     */
    @Override
    public void pushNewSession(Long senderId, Long userId, Long sessionId, Integer sessionType,
                               NewSessionNotificationDTO notification) {
        try {
            SystemNotificationMessage message = new SystemNotificationMessage();
            message.setMessageId(generateMessageId());
            message.setSessionId(sessionId);
            message.setSenderId(senderId);
            message.setReceiverId(userId);
            message.setType(MessageTypeConstant.TYPE_SYSTEM_NEW_SESSION); // 102
            message.setSessionType(sessionType);
            message.setTimestamp(System.currentTimeMillis());

            // Build the body
            Map<String, Object> body = new HashMap<>();
            body.put("sessionName", notification.getSessionName());
            body.put("avatar", notification.getAvatar());
            message.setBody(body);

            sendNotification(message, "new session notification");

        } catch (Exception e) {
            log.error("Failed to publish the new-session notification, user id: {}, session id: {}, error: {}", userId, sessionId, e.getMessage(), e);
        }
    }


    /**
     * Pushes a new-group-session notification
     *
     * Steps:
     * 1. Build the complete SystemNotificationMessage
     * 2. Generate a unique messageId
     * 3. Lift sessionId and sessionType to the top level
     * 4. Publish it to the Kafka system-notification-topic
     * 5. RealTimeService consumes it and pushes it to the online user
     *
     * @param userId       the id of the user receiving the notification
     * @param sessionId    the group session id
     * @param notification the new-group-session notification payload (carries only sessionName and avatar)
     */
    @Override
    public void pushGroupNewSession(Long userId, Long sessionId, NewGroupSessionNotificationDTO notification) {
        try {
            SystemNotificationMessage message = new SystemNotificationMessage();
            message.setMessageId(generateMessageId());
            message.setSessionId(sessionId);
            message.setSenderId(null); // system message
            message.setReceiverId(userId);
            message.setType(MessageTypeConstant.TYPE_SYSTEM_NEW_GROUP_SESSION); // 103
            message.setSessionType(SessionTypeConstant.GROUP_TYPE); // always 1 for a group chat
            message.setTimestamp(System.currentTimeMillis());

            // Build the body
            Map<String, Object> body = new HashMap<>();
            body.put("sessionName", notification.getSessionName());
            body.put("avatar", notification.getAvatar());
            body.put("creatorId", notification.getCreatorId());
            body.put("membersCount", notification.getMembersCount());
            message.setBody(body);

            sendNotification(message, "group invitation notification");

        } catch (Exception e) {
            log.error("Failed to publish the new-group-session notification, user id: {}, session id: {}, error: {}", userId, sessionId, e.getMessage(), e);
        }
    }


    /**
     * Pushes a group removal/leave notification
     *
     * Steps:
     * 1. Build the complete SystemNotificationMessage
     * 2. Generate a unique messageId
     * 3. Lift sessionId and sessionType to the top level
     * 4. Publish it to the Kafka system-notification-topic
     * 5. RealTimeService consumes it and pushes it to the online user
     *
     * @param userId       the id of the user receiving the notification
     * @param sessionId    the group session id
     * @param notification the removal/leave notification payload (a null operatorId means the member left voluntarily)
     */
    @Override
    public void pushGroupKickNotification(Long userId, Long sessionId, GroupKickNotificationDTO notification) {
        try {
            SystemNotificationMessage message = new SystemNotificationMessage();
            message.setMessageId(generateMessageId());
            message.setSessionId(sessionId);
            message.setSenderId(null); // system message
            message.setReceiverId(userId);
            message.setType(MessageTypeConstant.TYPE_SYSTEM_GROUP_KICK); // 104
            message.setSessionType(SessionTypeConstant.GROUP_TYPE); // always 1 for a group chat
            message.setTimestamp(System.currentTimeMillis());

            // Build the body
            Map<String, Object> body = new HashMap<>();
            body.put("memberIds", notification.getMemberIds());
            body.put("operatorId", notification.getOperatorId());
            message.setBody(body);

            sendNotification(message, "group removal notification");

        } catch (Exception e) {
            log.error("Failed to publish the group removal notification, user id: {}, session id: {}, error: {}", userId, sessionId, e.getMessage(), e);
        }
    }


    /**
     * Generates a unique message id
     *
     * Format: msg_{timestamp}_{snowflakeId}
     *
     * @return the message id
     */
    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + SnowflakeUtil.nextId();
    }

    /**
     * Publishes the notification message to Kafka
     *
     * @param message          the system notification message
     * @param notificationName the notification's name, used for logging
     */
    private void sendNotification(SystemNotificationMessage message, String notificationName) {
        String messageJson = JSONUtil.toJsonStr(message);

        kafkaTemplate.send(
                KafkaTopicConstant.TOPIC_SYSTEM_NOTIFICATION,
                String.valueOf(message.getReceiverId()), // use receiverId as the key, so one user's messages stay ordered
                messageJson
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published {} successfully, messageId: {}, user id: {}, type: {}",
                        notificationName, message.getMessageId(), message.getReceiverId(), message.getType());
            } else {
                log.error("Failed to publish {}, messageId: {}, user id: {}, error: {}",
                        notificationName, message.getMessageId(), message.getReceiverId(), ex.getMessage());
            }
        });
    }


}