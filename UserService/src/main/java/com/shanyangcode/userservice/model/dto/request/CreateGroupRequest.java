package com.shanyangcode.userservice.model.dto.request;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Create-group request DTO
 */
@Data
public class CreateGroupRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creator's user id
     */
    @NotNull(message = "Creator id must not be null")
    private Long creatorId;

    /**
     * Member user ids
     */
    @NotNull(message = "Member id list must not be null")
    private List<Long> memberIds;
}