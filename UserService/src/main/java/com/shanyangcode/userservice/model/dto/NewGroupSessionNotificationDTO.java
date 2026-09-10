package com.shanyangcode.userservice.model.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * New group session notification DTO (the message body)
 *
 * Scenario: a user is invited to a group chat and is notified
 *
 * Note: fields such as sessionId and sessionType were lifted to the top level of SystemNotificationMessage;
 * this DTO is only the content of SystemNotificationMessage.body
 */
@Data
public class NewGroupSessionNotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Group name
     */
    private String sessionName;

    /**
     * Group avatar URL
     */
    private String avatar;

    /**
     * Group owner's user id
     */
    private Long creatorId;

    /**
     * Number of group members
     */
    private Integer membersCount;
}