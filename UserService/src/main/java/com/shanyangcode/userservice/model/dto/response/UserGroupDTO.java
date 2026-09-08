package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户加入的群聊信息DTO
 */
@Data
public class UserGroupDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 群主用户ID
     */
    private String creatorId;

    /**
     * 群名称
     */
    private String sessionName;

    /**
     * 群头像
     */
    private String avatar;

    /**
     * 当前用户在群内的角色：0 群主，1 管理员，2 普通成员
     */
    private Integer role;

    /**
     * 群成员数量
     */
    private Integer memberCount;

    /**
     * 加入群聊的时间
     */
    private String createdTime;
}
