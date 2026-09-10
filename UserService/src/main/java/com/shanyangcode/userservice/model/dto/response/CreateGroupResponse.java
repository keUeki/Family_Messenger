package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * Create-group response DTO
 */
@Data
public class CreateGroupResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creator's id
     */
    private String creatorId;

    /**
     * Session id
     */
    private String sessionId;

    /**
     * Session name (the group name)
     */
    private String sessionName;

    /**
     * Session type (1: group)
     */
    private Integer sessionType;

    /**
     * Group avatar URL
     */
    private String avatar;

    /**
     * Ids of the members who could not be invited
     */
    private List<String> failedMemberIds;
}