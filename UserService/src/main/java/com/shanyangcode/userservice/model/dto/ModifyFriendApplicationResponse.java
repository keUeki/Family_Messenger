package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 修改好友申请响应DTO
 * <p>
 * 用于返回通过好友申请后新建的会话信息。
 * userId 与 sessionId 使用 String 而非 Long，避免前端 JS Number
 * 只有 53 位有效精度、截断雪花ID的问题。
 */
@Data
public class ModifyFriendApplicationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对方用户ID
     */
    private String userId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 会话类型 (0:单聊 1:群聊)
     */
    private Integer sessionType;

    /**
     * 会话名称
     */
    private String sessionName;

    /**
     * 头像URL
     */
    private String avatar;
}
