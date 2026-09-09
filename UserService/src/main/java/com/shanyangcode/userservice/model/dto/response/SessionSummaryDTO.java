package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 会话列表项 DTO
 * <p>
 * 对应前端聊天页左侧会话列表的一行。
 */
@Data
public class SessionSummaryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 最后一条消息的类型：0 文本，1 图片，2 表情包；没有消息时为 0
     */
    private Integer type;

    /**
     * 会话类型：0 单聊，1 群聊，2 AI
     */
    private Integer sessionType;

    /**
     * 最后一条消息的发送者 ID，没有消息时为空
     */
    private String senderId;

    /**
     * 单聊/AI 会话中对方的用户 ID，群聊为空
     */
    private String peerId;

    /**
     * 会话头像：单聊取对方头像，群聊取群头像
     */
    private String avatar;

    /**
     * 会话名称：单聊取对方昵称，群聊取群名称
     */
    private String name;

    /**
     * 最后一条消息的内容预览
     */
    private String lastMsgContent;

    /**
     * 最后一条消息的时间
     */
    private String lastMsgTime;

    /**
     * 未读消息数
     */
    private Integer count;
}