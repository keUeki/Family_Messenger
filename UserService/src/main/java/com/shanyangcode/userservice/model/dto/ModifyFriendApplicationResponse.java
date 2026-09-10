package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Friend-request update response DTO
 * <p>
 * Returns the session created once a friend request is accepted.
 * userId and sessionId are Strings rather than Longs, because a JS Number has only
 * 53 bits of precision and would truncate a snowflake id.
 */
@Data
public class ModifyFriendApplicationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The other party's user id
     */
    private String userId;

    /**
     * Session id
     */
    private String sessionId;

    /**
     * Session type (0: one-to-one, 1: group)
     */
    private Integer sessionType;

    /**
     * Session name
     */
    private String sessionName;

    /**
     * Avatar URL
     */
    private String avatar;
}
