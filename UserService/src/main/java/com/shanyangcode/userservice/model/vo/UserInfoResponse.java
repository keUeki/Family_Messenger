package com.shanyangcode.userservice.model.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * User profile response
 */
@Data
public class UserInfoResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * User id
     */
    private String userId;

    /**
     * Login account (email address)
     */
    private String account;

    /**
     * Nickname
     */
    private String nickname;

    /**
     * Avatar
     */
    private String avatar;

    /**
     * Gender: 0 female, 1 male, 2 unknown
     */
    private Integer gender;

    /**
     * Bio
     */
    private String description;
}