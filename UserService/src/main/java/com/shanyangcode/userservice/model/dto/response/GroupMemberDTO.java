package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Group member DTO
 */
@Data
public class GroupMemberDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Member's user id
     */
    private String userId;

    /**
     * Member's nickname
     */
    private String nickname;

    /**
     * Member's avatar
     */
    private String avatar;

    /**
     * Member's role in the group: 0 owner, 1 admin, 2 regular member
     */
    private Integer role;
}
