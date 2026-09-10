package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Group member count response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberCountResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Number of group members
     */
    private Integer memberCount;
}
