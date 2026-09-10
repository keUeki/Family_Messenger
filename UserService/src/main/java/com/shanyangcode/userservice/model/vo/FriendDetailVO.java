package com.shanyangcode.userservice.model.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Friend detail VO
 *
 * Carries the detailed information about a friend
 */
@Data
public class FriendDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * User id
     */
    private String userId;

    /**
     * Nickname
     */
    private String nickname;

    /**
     * Avatar URL
     */
    private String avatar;

    /**
     * Email address
     */
    private String email;

    /**
     * Phone number
     */
    private String phone;

    /**
     * Bio
     */
    private String signature;

    /**
     * Gender (0: female, 1: male, 2: unknown)
     */
    private Integer gender;

    /**
     * Session id
     */
    private String sessionId;

    /**
     * Friendship status (0: friend, 1: blocked, 2: deleted, -1: not a friend)
     */
    private Integer status;
}