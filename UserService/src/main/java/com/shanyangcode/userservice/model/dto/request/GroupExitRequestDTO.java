package com.shanyangcode.userservice.model.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * 退出群聊请求DTO
 */
@Data
public class GroupExitRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /**
     * 退出群聊的用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
