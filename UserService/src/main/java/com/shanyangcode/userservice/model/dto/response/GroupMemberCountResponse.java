package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群成员数量响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberCountResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 群成员数量
     */
    private Integer memberCount;
}
