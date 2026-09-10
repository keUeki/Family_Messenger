package com.shanyangcode.userservice.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * System notification message DTO
 *
 * Responsibilities:
 * - The single system notification format used across this IM project
 * - The type field distinguishes the kinds of system notification
 * - Travels over Kafka; RealTimeService consumes it and pushes it to the user
 *
 * Message types (the type field):
 * - 101: friend request received
 * - 102: new session created
 * - 103: new group session created (a group invitation)
 */
@Data
public class SystemNotificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique message id
     * Format: msg_{timestamp}_{snowflakeId}
     * Used for tracing and de-duplication
     */
    private String messageId;

    /**
     * Session id (may be null)
     * - friend request notification: null
     * - new session notification: the session id
     * - group invitation notification: the group session id
     */
    private Long sessionId;

    /**
     * Sender id
     * - system message: 0
     * - friend request: the requester's id
     * - new session: the id of whoever created it
     * - otherwise: whatever the use case calls for
     */
    private Long senderId;

    /**
     * Receiver's user id (the user this notification is for)
     */
    private Long receiverId;

    /**
     * Message type
     * 101: friend request received
     * 102: new session created
     * 103: new group session created
     */
    private Integer type;

    /**
     * Session type (may be null)
     * 0: one-to-one
     * 1: group
     * 2: bot
     * null: not tied to a session (a friend request, for example)
     */
    private Integer sessionType;

    /**
     * Message creation timestamp, in milliseconds
     */
    private Long timestamp;

    /**
     * Message body (the payload)
     * The body differs per notification type:
     * - type=101: {nickname: "Alice", avatar: "http://...", msg: "Hi, I'm xxx"}
     * - type=102: {sessionName: "Alice", avatar: "http://..."}
     * - type=103: {sessionName: "Engineering chat", avatar: "http://...", creatorId: 123, membersCount: 5}
     */
    private Map<String, Object> body;
}