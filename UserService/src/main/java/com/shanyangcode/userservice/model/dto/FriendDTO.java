package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Friend DTO
 *
 * Carries a row of the friend list
 */
@Data
public class FriendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Friend's user id
     */
    private String userId;

    /**
     * Friend's nickname
     */
    private String nickname;

    /**
     * Friend's avatar URL
     */
    private String avatar;

    /**
     * Friendship status (0: friend, 1: blocked, 2: deleted)
     */
    private Integer status;

    /**
     * Bio
     */
    private String signature;

    /**
     * Session id
     */
    private String sessionId;
}