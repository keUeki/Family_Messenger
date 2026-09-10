package com.shanyangcode.userservice.model.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Group invitation request DTO
 */
@Data
public class InviteGroupRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Session id
     */
    @NotNull(message = "Session id must not be null")
    private Long sessionId;

    /**
     * Inviter's user id
     */
    @NotNull(message = "Inviter id must not be null")
    private Long inviterId;

    /**
     * User ids of the people being invited
     */
    @NotNull(message = "Invitee id list must not be null")
    private List<Long> inviteeIds;
}