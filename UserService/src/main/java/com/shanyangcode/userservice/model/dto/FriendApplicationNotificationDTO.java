package com.shanyangcode.userservice.model.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * Friend request notification DTO
 *
 * Scenario: user A sends user B a friend request, and user B is notified
 */
@Data
public class FriendApplicationNotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Requester's nickname
     */
    private String applyUserName;

    /**
     * Requester's user id
     */
    private Long applyUserId;

    /**
     * Message attached to the request (shown in the push notification)
     */
    private String message;

    /**
     * Requester's avatar URL
     */
    private String applyFriendAvatar;
}