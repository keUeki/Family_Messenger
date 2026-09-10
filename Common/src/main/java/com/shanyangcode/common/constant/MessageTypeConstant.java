package com.shanyangcode.common.constant;
/**
 * Message type constants
 *
 * Message type ranges:
 * - 0-99: chat messages (text, image, sticker, red packet, ...)
 * - 100-199: system notifications (friend requests, new sessions, ...)
 *
 * This class defines the system notification types (the 100-199 range).
 */
public class MessageTypeConstant {

    public static final int TEXT_MESSAGE = 0;

    public static final int IMAGE_MESSAGE = 1;

    public static final int EMOJI_MESSAGE = 2;

    public static final int RED_PACKET_MESSAGE = 3;

    /**
     * System notification: a friend request was received
     * Scenario: user A sends a friend request to user B
     */
    public static final int TYPE_SYSTEM_NEW_APPLY = 101;

    /**
     * System notification: a new session was created
     * Scenario: after users A and B become friends, the system creates a one-to-one session
     */
    public static final int TYPE_SYSTEM_NEW_SESSION = 102;

    /**
     * System notification: a new group session was created (group invitation notice)
     * Scenario: a user is invited to join a group chat
     */
    public static final int TYPE_SYSTEM_NEW_GROUP_SESSION = 103;

    /**
     * System notification: a member was removed from a group chat
     * Scenario: a user is kicked from a group; every member is notified, including the removed user
     */
    public static final int TYPE_SYSTEM_GROUP_KICK = 104;

    /**
     * Chat message type range: 0-99
     */
    public static final int CHAT_MESSAGE_MIN = 0;
    public static final int CHAT_MESSAGE_MAX = 99;

    /**
     * System notification type range: 100-199
     */
    public static final int SYSTEM_NOTIFICATION_MIN = 100;
    public static final int SYSTEM_NOTIFICATION_MAX = 199;

    /**
     * Tells whether the type is a system notification
     *
     * @param type message type
     * @return true if the type is a system notification
     */
    public static boolean isSystemNotification(int type) {
        return type >= SYSTEM_NOTIFICATION_MIN && type <= SYSTEM_NOTIFICATION_MAX;
    }

    /**
     * Tells whether the type is a chat message
     *
     * @param type message type
     * @return true if the type is a chat message
     */
    public static boolean isChatMessage(int type) {
        return type >= CHAT_MESSAGE_MIN && type <= CHAT_MESSAGE_MAX;
    }
}