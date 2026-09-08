package com.shanyangcode.userservice.model.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 踢出群成员请求DTO
 */
@Data
public class KickGroupMembersRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     * <p>
     * 使用 String 传输，避免前端 JS Number 精度截断雪花ID
     */
    @NotNull(message = "会话ID不能为空")
    private String sessionId;

    /**
     * 操作者用户ID（群主或管理员）
     */
    @NotNull(message = "操作者ID不能为空")
    private Long operatorId;

    /**
     * 被踢出的成员用户ID列表
     */
    @NotNull(message = "成员ID列表不能为空")
    private List<Long> memberIds;
}
