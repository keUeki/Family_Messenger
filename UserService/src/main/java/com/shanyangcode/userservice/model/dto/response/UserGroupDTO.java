package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * DTO describing a group the user belongs to
 */
@Data
public class UserGroupDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Session id
     */
    private String sessionId;

    /**
     * Group owner's user id
     */
    private String creatorId;

    /**
     * Group name
     */
    private String sessionName;

    /**
     * Group avatar
     */
    private String avatar;

    /**
     * The current user's role in the group: 0 owner, 1 admin, 2 regular member
     */
    private Integer role;

    /**
     * Number of group members
     */
    private Integer memberCount;

    /**
     * Time the user joined the group
     */
    private String createdTime;
}
