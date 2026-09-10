package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Session list item DTO
 * <p>
 * One row of the session list on the left of the chat page.
 */
@Data
public class SessionSummaryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Session id
     */
    private String sessionId;

    /**
     * Type of the last message: 0 text, 1 image, 2 sticker; 0 when there is no message
     */
    private Integer type;

    /**
     * Session type: 0 one-to-one, 1 group, 2 AI
     */
    private Integer sessionType;

    /**
     * Sender id of the last message; empty when there is no message
     */
    private String senderId;

    /**
     * The other party's user id in a one-to-one or AI session; empty for a group
     */
    private String peerId;

    /**
     * Session avatar: the other party's avatar for a one-to-one chat, the group avatar for a group
     */
    private String avatar;

    /**
     * Session name: the other party's nickname for a one-to-one chat, the group name for a group
     */
    private String name;

    /**
     * Preview of the last message's content
     */
    private String lastMsgContent;

    /**
     * Time of the last message
     */
    private String lastMsgTime;

    /**
     * Number of unread messages
     */
    private Integer count;
}