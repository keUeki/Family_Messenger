package com.shanyangcode.userservice.model.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * Friend-request expiry event DTO
 *
 * Responsibilities:
 * - Published to Kafka when the scheduled scan finds an expired friend request
 * - The consumer applies the expiry by updating the database status
 *
 * Topic: friend-request-expiration-topic
 */
@Data
public class FriendRequestExpirationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Friend request id
     */
    private Long applyFriendId;

    /**
     * Expiry timestamp, in milliseconds
     */
    private Long expireTime;
}