package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.FriendApplicationNotificationDTO;
import com.shanyangcode.userservice.model.dto.GroupKickNotificationDTO;
import com.shanyangcode.userservice.model.dto.NewGroupSessionNotificationDTO;
import com.shanyangcode.userservice.model.dto.NewSessionNotificationDTO;

/**
 * Notification push service
 * <p>
 * Responsibilities:
 * - Publishes system notifications asynchronously over Kafka
 * - RealTimeService consumes them and pushes them to online users
 * - Supports several kinds of system notification
 * - Follows the notification design used across this IM project
 */
public interface NotificationService {

    /**
     * Pushes a friend request notification
     * <p>
     * Scenario: a user receives a new friend request
     *
     * @param userId       the id of the user receiving the notification
     * @param notification the friend request notification payload
     */
    void pushNewApply(Long userId, FriendApplicationNotificationDTO notification);

    /**
     * Pushes a new-session notification
     * <p>
     * Scenario: once a friend request is accepted the system creates a one-to-one session and notifies the requester
     *
     * @param senderId     the id of the user who triggered the session (the party who accepted)
     * @param userId       the id of the user receiving the notification (the party who made the request)
     * @param sessionId    the session id
     * @param sessionType  the session type (0 one-to-one, 1 group, 2 bot)
     * @param notification the new-session notification payload (carries sessionName and avatar)
     */
    void pushNewSession(Long senderId, Long userId, Long sessionId, Integer sessionType, NewSessionNotificationDTO notification);

    /**
     * Pushes a new-group-session notification
     * <p>
     * Scenario: a user is invited to join a group chat
     *
     * @param userId       the id of the user receiving the notification
     * @param sessionId    the group session id
     * @param notification the new-group-session notification payload (carries sessionName and avatar)
     */
    void pushGroupNewSession(Long userId, Long sessionId, NewGroupSessionNotificationDTO notification);

    /**
     * Pushes a group removal/leave notification
     * <p>
     * Scenario: a member is removed (operatorId is the actor) or leaves voluntarily (operatorId is null)
     *
     * @param userId       the id of the user receiving the notification
     * @param sessionId    the group session id
     * @param notification the removal/leave notification payload (carries memberIds and operatorId)
     */
    void pushGroupKickNotification(Long userId, Long sessionId, GroupKickNotificationDTO notification);
}
