package com.shanyangcode.userservice.model.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Remove-group-members request DTO
 */
@Data
public class KickGroupMembersRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Session id
     * <p>
     * Sent as a String so a JS Number on the client cannot truncate the snowflake id
     */
    @NotNull(message = "Session id must not be null")
    private String sessionId;

    /**
     * Actor's user id (the group owner or an admin)
     */
    @NotNull(message = "Actor id must not be null")
    private Long operatorId;

    /**
     * User ids of the members being removed
     */
    @NotNull(message = "Member id list must not be null")
    private List<Long> memberIds;
}
