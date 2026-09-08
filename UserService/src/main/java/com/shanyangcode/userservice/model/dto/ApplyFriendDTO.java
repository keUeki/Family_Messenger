package com.shanyangcode.userservice.model.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 好友申请DTO
 * <p>
 * 用于返回好友申请列表数据。
 * 列表同时包含"我发出的"和"我收到的"两类申请，
 * 因此 userId/nickname/avatar 始终指向"对方"，由 isReceiver 区分视角。
 */
@Data
public class ApplyFriendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对方用户ID（我是发送者时为接收者，我是接收者时为发送者）
     */
    private String userId;

    /**
     * 对方用户昵称
     */
    private String nickname;

    /**
     * 对方用户头像
     */
    private String avatar;

    /**
     * 申请附言
     */
    private String msg;

    /**
     * 申请状态 (0:未读 1:通过 2:拒绝 3:已读 4:过期)
     */
    private Integer status;

    /**
     * 更新时间
     */
    private LocalDateTime time;

    /**
     * 是否为接收者 (0:否-我是发送者 1:是-我是接收者)
     */
    private Integer isReceiver;
}
