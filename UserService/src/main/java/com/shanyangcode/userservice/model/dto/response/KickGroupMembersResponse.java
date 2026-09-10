package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * Remove-group-members response DTO
 */
@Data
public class KickGroupMembersResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Ids of the members removed successfully
     */
    private List<String> successIds;
}
