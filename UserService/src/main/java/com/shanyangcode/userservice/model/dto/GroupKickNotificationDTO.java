package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 群聊踢出/退出通知DTO
 * <p>
 * 场景：成员被踢出群聊，或成员主动退出群聊
 * <p>
 * 注意：sessionId、sessionType 等字段位于 SystemNotificationMessage 顶层，
 * 此DTO仅作为 SystemNotificationMessage.body 的内容。
 */
@Data
public class GroupKickNotificationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 离开群聊的成员ID列表
     */
    private List<Long> memberIds;

    /**
     * 操作者用户ID
     * <p>
     * 被踢出时为执行踢出操作的群主/管理员ID；主动退出时为 null。
     */
    private Long operatorId;
}
