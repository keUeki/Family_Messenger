package com.shanyangcode.userservice.model.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * Friend-request creation event DTO
 *
 * Responsibilities:
 * - Published to Kafka when a friend request is created
 * - The consumer registers it in the Redis ZSET delay queue
 *
 * Topic: friend-request-creation-topic
 */
@Data
public class FriendRequestCreationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Friend request id
     */
    private Long applyFriendId;

    /**
     * Creation timestamp, in milliseconds
     */
    private Long createTime;

    /**
     * Expiry timestamp, in milliseconds
     * Defaults to createTime + 24 hours
     */
    private Long expireTime;
}