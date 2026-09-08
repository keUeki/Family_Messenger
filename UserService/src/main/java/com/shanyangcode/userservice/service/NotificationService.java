package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.FriendApplicationNotificationDTO;
import com.shanyangcode.userservice.model.dto.GroupKickNotificationDTO;
import com.shanyangcode.userservice.model.dto.NewGroupSessionNotificationDTO;
import com.shanyangcode.userservice.model.dto.NewSessionNotificationDTO;

/**
 * 通知推送服务接口
 * <p>
 * 功能说明：
 * - 通过Kafka异步发送系统通知消息
 * - 由RealTimeService消费并推送给在线用户
 * - 支持多种类型的系统通知
 * - 符合IM项目通知消息设计方案
 */
public interface NotificationService {

    /**
     * 推送好友申请通知
     * <p>
     * 场景：用户收到新的好友申请
     *
     * @param userId       接收通知的用户ID
     * @param notification 好友申请通知信息
     */
    void pushNewApply(Long userId, FriendApplicationNotificationDTO notification);

    /**
     * 推送新会话通知
     * <p>
     * 场景：好友申请通过后，系统创建新的单聊会话，通知申请方
     *
     * @param senderId     触发该会话的用户ID（同意申请的一方）
     * @param userId       接收通知的用户ID（发起申请的一方）
     * @param sessionId    会话ID
     * @param sessionType  会话类型（0-单聊，1-群聊，2-机器人）
     * @param notification 新会话通知信息（包含sessionName和avatar）
     */
    void pushNewSession(Long senderId, Long userId, Long sessionId, Integer sessionType, NewSessionNotificationDTO notification);

    /**
     * 推送新群聊会话通知
     * <p>
     * 场景：用户被邀请加入群聊
     *
     * @param userId       接收通知的用户ID
     * @param sessionId    群聊会话ID
     * @param notification 新群聊会话通知信息（包含sessionName和avatar）
     */
    void pushGroupNewSession(Long userId, Long sessionId, NewGroupSessionNotificationDTO notification);

    /**
     * 推送群聊踢出/退出通知
     * <p>
     * 场景：成员被踢出群聊（operatorId 为操作者），或成员主动退出群聊（operatorId 为 null）
     *
     * @param userId       接收通知的用户ID
     * @param sessionId    群聊会话ID
     * @param notification 踢出/退出通知信息（包含memberIds和operatorId）
     */
    void pushGroupKickNotification(Long userId, Long sessionId, GroupKickNotificationDTO notification);
}
