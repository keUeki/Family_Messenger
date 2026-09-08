package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 群成员信息DTO
 */
@Data
public class GroupMemberDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成员用户ID
     */
    private String userId;

    /**
     * 成员昵称
     */
    private String nickname;

    /**
     * 成员头像
     */
    private String avatar;

    /**
     * 成员在群内的角色：0 群主，1 管理员，2 普通成员
     */
    private Integer role;
}
