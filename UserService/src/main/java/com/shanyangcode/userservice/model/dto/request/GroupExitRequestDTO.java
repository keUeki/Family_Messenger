package com.shanyangcode.userservice.model.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * Leave-group request DTO
 */
@Data
public class GroupExitRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Session id
     */
    @NotNull(message = "Session id must not be null")
    private Long sessionId;

    /**
     * Id of the user leaving the group
     */
    @NotNull(message = "User id must not be null")
    private Long userId;
}
