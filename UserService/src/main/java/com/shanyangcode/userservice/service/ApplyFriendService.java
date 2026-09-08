package com.shanyangcode.userservice.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.userservice.model.dto.ApplyFriendDTO;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.entity.ApplyFriend;

/**
 * 好友申请服务接口
 *
 * 功能说明：
 * - 管理好友申请的完整生命周期
 * - 支持申请、查询、状态修改等操作
 * - 集成Kafka通知和过期机制
 */
public interface ApplyFriendService extends IService<ApplyFriend> {

    /**
     * 发送好友申请
     * <p>
     * 处理流程：
     * 1. 验证发送者和接收者用户是否存在
     * 2. 检查是否已经是好友关系
     * 3. 检查是否已有待处理的申请
     *   4a. 没有：插入新申请记录，同时异步发出 Kafka 通知（通知链路 + 过期链路）
     *   4b. 有且已通过：返回"已是好友"
     *   4c. 有但其它状态（已读 / 已拒绝 / 已过期）：复用记录、状态回写为 UNREAD、附言更新，同时异步发出 Kafka 通知（通知链路 + 过期链路）
     * 5. 返回 applyFriendId
     *
     * @param senderId   发送者用户ID
     * @param receiverId 接收者用户ID
     * @param message    申请消息
     * @return 好友申请ID
     */
    Long sendFriendRequest(Long senderId, Long receiverId, String message);

    /**
     * 分页查询与该用户相关的好友申请列表（含对方用户信息）
     * <p>
     * 列表同时包含"我发出的"和"我收到的"两类申请，
     * 由返回结果中的 isReceiver 字段区分视角。
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 申请DTO分页结果
     */
    IPage<ApplyFriendDTO> getReceivedRequestsWithUserInfo(Long userId, PageRequest pageRequest);

    /**
     * 查询未读好友申请数量
     * <p>
     * 只统计"我收到的"且状态为未读的申请。
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    int getUnreadCount(Long userId);

    /**
     * 修改好友申请状态
     * <p>
     * 仅允许改为通过(1)、拒绝(2)、已读(3)；
     * 通过和拒绝时 senderIds 只能包含一个元素，已读时可批量。
     *
     * @param receiverId 接收者用户ID（当前操作人）
     * @param senderIds  申请发送者用户ID列表
     * @param status     目标状态码
     * @return 通过申请时返回新建的会话信息，其他情况返回 null
     */
    ModifyFriendApplicationResponse modifyApplicationStatus(Long receiverId, List<Long> senderIds, Integer status);
}
