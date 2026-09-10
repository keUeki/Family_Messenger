package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * New session notification DTO (the message body)
 * <p>
 * Scenario: once a friend request is accepted the system creates a one-to-one session and notifies the requester.
 * <p>
 * Note: fields such as sessionId and sessionType live at the top level of SystemNotificationMessage;
 * this DTO is only the content of SystemNotificationMessage.body.
 */
@Data
public class NewSessionNotificationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Session name (usually the other party's nickname)
     */
    private String sessionName;

    /**
     * Avatar URL
     */
    private String avatar;
}
