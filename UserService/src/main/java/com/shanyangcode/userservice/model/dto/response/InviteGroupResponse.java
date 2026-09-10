package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * Group invitation response DTO
 */
@Data
public class InviteGroupResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Ids of the users invited successfully
     */
    private List<String> successIds;

    /**
     * Ids of the users who could not be invited
     */
    private List<String> failedIds;
}