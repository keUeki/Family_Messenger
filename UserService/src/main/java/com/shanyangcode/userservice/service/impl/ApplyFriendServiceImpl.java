package com.shanyangcode.userservice.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.utils.SnowflakeUtil;
import com.shanyangcode.userservice.constants.FriendApplicationStatusEnum;
import com.shanyangcode.userservice.constants.KafkaTopicConstant;
import com.shanyangcode.userservice.mapper.ApplyFriendMapper;
import com.shanyangcode.userservice.model.dto.ApplyFriendDTO;
import com.shanyangcode.userservice.model.dto.FriendApplicationNotificationDTO;
import com.shanyangcode.userservice.model.dto.FriendRequestCreationEvent;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.entity.ApplyFriend;
import com.shanyangcode.userservice.model.entity.Friend;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.service.ApplyFriendService;
import com.shanyangcode.userservice.service.FriendService;
import com.shanyangcode.userservice.service.NotificationService;
import com.shanyangcode.userservice.service.UserService;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友申请服务实现类
 */
@Slf4j
@Service
public class ApplyFriendServiceImpl extends ServiceImpl<ApplyFriendMapper, ApplyFriend> implements ApplyFriendService {

    private final UserService userService;
    private final FriendService friendService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplyFriendMapper applyFriendMapper;

    /**
     * 申请列表中的视角标识：我是发送者 / 我是接收者
     */
    private static final int IS_RECEIVER_NO = 0;
    private static final int IS_RECEIVER_YES = 1;

    /**
     * 好友申请过期时间（24小时）
     */
    private static final long FRIEND_REQUEST_EXPIRATION_HOURS = 24L;

    public ApplyFriendServiceImpl(FriendService friendService,
                                  UserService userService,
                                  NotificationService notificationService,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ApplyFriendMapper applyFriendMapper) {
        this.friendService = friendService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.kafkaTemplate = kafkaTemplate;
        this.applyFriendMapper = applyFriendMapper;
    }


    /**
     * 发送好友申请
     * <p>
     * 处理流程：
     * 1. 验证发送者和接收者用户是否存在，是否为同一个用户
     * 2. 检查是否已经是好友关系
     * 3. 检查是否已有待处理的申请
     * 4a. 没有：插入新申请记录，同时异步发出 Kafka 通知（通知链路 + 过期链路）
     * 4b. 有且已通过：返回"已是好友"
     * 4c. 有但其它状态（已读 / 已拒绝 / 已过期）：复用记录、状态回写为 UNREAD、附言更新，同时异步发出 Kafka 通知（通知链路 + 过期链路）
     * 5. 返回 applyFriendId
     *
     * @param senderId   发送者用户ID
     * @param receiverId 接收者用户ID
     * @param message    申请消息
     * @return 好友申请ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendFriendRequest(Long senderId, Long receiverId, String message) {
        // 1. 验证用户存在性
        User sender = userService.getById(senderId);
        ThrowUtils.throwIf(sender == null, ErrorCode.NOT_FOUND_ERROR, "发送者用户不存在");

        User receiver = userService.getById(receiverId);
        ThrowUtils.throwIf(receiver == null, ErrorCode.NOT_FOUND_ERROR, "接收者用户不存在");

        // 检查是否为同一个用户
        ThrowUtils.throwIf(senderId.equals(receiverId), ErrorCode.OPERATION_ERROR, "不能添加自己");

        // 2. 检查是否已经是好友关系且是否已被拉黑
        boolean isFriend = friendService.lambdaQuery()
                .eq(Friend::getUserId, senderId)
                .eq(Friend::getFriendId, receiverId)
                .exists();
        ThrowUtils.throwIf(isFriend, ErrorCode.OPERATION_ERROR, "已经是好友关系，无需重复添加");


        // 3. 检查是否已有待处理的申请
        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getSenderId, senderId)
                .eq(ApplyFriend::getReceiverId, receiverId);
        ApplyFriend existingApplyFriend = this.getOne(queryWrapper);

        Long applyFriendId = null;
        if (existingApplyFriend == null) {
            // 4a. 没有：插入新申请记录，同时异步发出 Kafka 通知（通知链路 + 过期链路）
            applyFriendId = handleNewFriendApplication(senderId, receiverId, message, sender);
        } else if (existingApplyFriend.getStatus().equals(FriendApplicationStatusEnum.ACCEPTED.getCode())) {
            // 4b. 有且已通过：返回"已是好友"
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "已经是好友关系，无需重复添加");
        } else {
            // 4c. 有但其它状态（已读 / 已拒绝 / 已过期）：复用记录、状态回写为 UNREAD、附言更新，同时异步发出 Kafka 通知（通知链路 + 过期链路）
            applyFriendId = handleExistingFriendApplication(existingApplyFriend, message, sender);
        }

        // 5. 返回 applyFriendId
        return applyFriendId;
    }

    /**
     * 处理新的好友申请
     *
     * @param senderId   发送者ID
     * @param receiverId 接收者ID
     * @param message    申请消息
     * @param sender     发送者用户对象
     * @return 好友申请ID
     */
    private Long handleNewFriendApplication(Long senderId, Long receiverId, String message, User sender) {
        // 1. 创建好友申请记录
        ApplyFriend applyFriend = new ApplyFriend();
        Long applyFriendId = SnowflakeUtil.nextId();
        applyFriend.setApplyFriendId(applyFriendId);
        applyFriend.setSenderId(senderId);
        applyFriend.setReceiverId(receiverId);
        applyFriend.setMessage(message);
        applyFriend.setStatus(FriendApplicationStatusEnum.UNREAD.getCode());
        applyFriend.setCreatedTime(LocalDateTime.now());
        applyFriend.setUpdatedTime(LocalDateTime.now());

        boolean saved = this.save(applyFriend);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "创建好友申请失败");

        // 2. 发送Kafka通知（异步）
        sendFriendApplicationNotification(receiverId, sender, message);

        // 3. 发送过期任务注册事件（异步）
        registerExpirationTask(applyFriendId);

        return applyFriendId;
    }


    /**
     * 处理已有的好友申请
     *
     * @param existingApplyFriend 现有的好友申请
     * @param message             申请消息
     * @param sender              发送者用户对象
     * @return 好友申请ID
     */
    private Long handleExistingFriendApplication(ApplyFriend existingApplyFriend, String message, User sender) {
        // 1. 更新好友申请记录
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode())
                .set(ApplyFriend::getMessage, message)
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getApplyFriendId, existingApplyFriend.getApplyFriendId());

        boolean updated = this.update(updateWrapper);
        ThrowUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR, "更新好友申请失败");

        // 2. 发送Kafka通知（异步）
        sendFriendApplicationNotification(existingApplyFriend.getReceiverId(), sender, message);

        // 3. 重新注册过期任务（异步）
        registerExpirationTask(existingApplyFriend.getApplyFriendId());

        return existingApplyFriend.getApplyFriendId();
    }


    /**
     * 发送好友申请通知（通过Kafka）
     *
     * @param receiverId 接收者ID
     * @param sender     发送者用户对象
     * @param message    申请附言
     */
    private void sendFriendApplicationNotification(Long receiverId, User sender, String message) {
        try {
            FriendApplicationNotificationDTO notification = new FriendApplicationNotificationDTO();
            notification.setApplyUserName(sender.getNickname());
            notification.setApplyUserId(sender.getUserId());
            notification.setApplyFriendAvatar(sender.getAvatar());
            notification.setMessage(message);

            notificationService.pushNewApply(receiverId, notification);
            log.info("发送好友申请通知成功，接收者ID：{}，发送者ID：{}", receiverId, sender.getUserId());
        } catch (Exception e) {
            log.warn("发送好友申请通知失败，接收者ID：{}，发送者ID：{}，原因：{}",
                    receiverId, sender.getUserId(), e.getMessage());
        }
    }

    /**
     * 注册好友申请过期任务（通过Kafka）
     *
     * @param applyFriendId 好友申请ID
     */
    private void registerExpirationTask(Long applyFriendId) {
        try {
            // 计算过期时间（当前时间 + 24小时）
            long createTime = System.currentTimeMillis();
            long expireTime = createTime + (FRIEND_REQUEST_EXPIRATION_HOURS * 60 * 60 * 1000);

            // 构建过期事件
            FriendRequestCreationEvent event = new FriendRequestCreationEvent();
            event.setApplyFriendId(applyFriendId);
            event.setCreateTime(createTime);
            event.setExpireTime(expireTime);

            // 发送到Kafka topic
            kafkaTemplate.send(
                    KafkaTopicConstant.TOPIC_FRIEND_REQUEST_CREATION,
                    String.valueOf(applyFriendId),
                    JSONUtil.toJsonStr(event)
            );

            log.info("注册好友申请过期任务成功，申请ID：{}，过期时间：{}", applyFriendId, expireTime);
        } catch (Exception e) {
            log.error("注册好友申请过期任务失败，申请ID：{}，原因：{}", applyFriendId, e.getMessage());
        }
    }


    /**
     * 分页查询与该用户相关的好友申请列表（含对方用户信息）
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 申请DTO分页结果
     */
    @Override
    public IPage<ApplyFriendDTO> getReceivedRequestsWithUserInfo(Long userId, PageRequest pageRequest) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();

        // 1. 查询好友申请实体分页
        IPage<ApplyFriend> applyFriendPage = getReceivedRequests(userId, pageNum, pageSize);

        // 2. 转换为DTO（批量回填对方用户信息）
        List<ApplyFriendDTO> dtoList = mapApplyFriendsToDTO(applyFriendPage.getRecords(), userId);

        // 3. 构建分页DTO结果，沿用实体分页的 total
        Page<ApplyFriendDTO> dtoPage = new Page<>(pageNum, pageSize, applyFriendPage.getTotal());
        dtoPage.setRecords(dtoList);

        return dtoPage;
    }

    /**
     * 分页查询与该用户相关的好友申请实体
     * <p>
     * 注意：查询条件是 senderId = userId OR receiverId = userId，
     * 即同时包含"我发出的"和"我收到的"。这是有意为之——
     * 正因如此 DTO 中的 isReceiver 字段才有意义。
     * 与之相对，getUnreadCount 只统计"我收到的"。
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 申请实体分页结果
     */
    private IPage<ApplyFriend> getReceivedRequests(Long userId, int pageNum, int pageSize) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq(ApplyFriend::getSenderId, userId)
                        .or()
                        .eq(ApplyFriend::getReceiverId, userId))
                .orderByDesc(ApplyFriend::getUpdatedTime);

        // 分页插件（Common 模块的 MybatisPlusConfig）会自动补 LIMIT 并计算 total
        Page<ApplyFriend> page = new Page<>(pageNum, pageSize);
        return applyFriendMapper.selectPage(page, queryWrapper);
    }

    /**
     * 将好友申请记录映射为DTO列表
     * <p>
     * 处理"我是发方还是收方"的视角切换：DTO 中的用户信息始终指向对方。
     *
     * @param applyFriends 好友申请记录
     * @param userId       当前用户ID
     * @return DTO列表
     */
    private List<ApplyFriendDTO> mapApplyFriendsToDTO(List<ApplyFriend> applyFriends, Long userId) {
        if (applyFriends == null || applyFriends.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 先收集所有"对方"的用户ID，避免在循环里逐条 getById 造成 N+1
        Set<Long> targetUserIds = new HashSet<>();
        for (ApplyFriend applyFriend : applyFriends) {
            Long targetId = applyFriend.getSenderId().equals(userId)
                    ? applyFriend.getReceiverId()
                    : applyFriend.getSenderId();
            if (targetId != null) {
                targetUserIds.add(targetId);
            }
        }

        // 2. 一次性批量查询用户信息
        Map<Long, User> userMap = new HashMap<>();
        if (!targetUserIds.isEmpty()) {
            List<User> users = userService.listByIds(targetUserIds);
            if (users != null) {
                for (User user : users) {
                    userMap.put(user.getUserId(), user);
                }
            }
        }

        // 3. 逐条构建DTO
        List<ApplyFriendDTO> dtoList = new ArrayList<>(applyFriends.size());
        for (ApplyFriend applyFriend : applyFriends) {
            ApplyFriendDTO dto = new ApplyFriendDTO();
            dto.setMsg(applyFriend.getMessage());
            dto.setStatus(applyFriend.getStatus());
            dto.setTime(applyFriend.getUpdatedTime());

            Long targetUserId = applyFriend.getSenderId().equals(userId)
                    ? applyFriend.getReceiverId()
                    : applyFriend.getSenderId();
            User targetUser = userMap.get(targetUserId);
            if (targetUser != null) { // 防御性判断：对方账号被硬删除时整行字段留空
                dto.setUserId(String.valueOf(targetUser.getUserId()));
                dto.setNickname(targetUser.getNickname());
                dto.setAvatar(targetUser.getAvatar());
                dto.setIsReceiver(applyFriend.getSenderId().equals(userId) ? IS_RECEIVER_NO : IS_RECEIVER_YES);
            }
            dtoList.add(dto);
        }
        return dtoList;
    }

    /**
     * 查询未读好友申请数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    @Override
    public int getUnreadCount(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getReceiverId, userId)
                .eq(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode());

        return Math.toIntExact(applyFriendMapper.selectCount(queryWrapper));
    }

    /**
     * 修改好友申请状态
     * <p>
     * 事务边界必须在这个 public 入口上：下面的 handleXxx 是 protected 且被本类内部调用，
     * Spring 代理不会对它们生效，它们自身的 @Transactional 不起作用。
     *
     * @param receiverId 接收者用户ID（当前操作人）
     * @param senderIds  申请发送者用户ID列表
     * @param status     目标状态码
     * @return 通过申请时返回新建的会话信息，其他情况返回 null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModifyFriendApplicationResponse modifyApplicationStatus(Long receiverId, List<Long> senderIds, Integer status) {
        ThrowUtils.throwIf(receiverId == null, ErrorCode.PARAMS_ERROR, "接收者ID不能为空");
        ThrowUtils.throwIf(senderIds == null || senderIds.isEmpty(), ErrorCode.PARAMS_ERROR, "发送者ID列表不能为空");

        // 非法状态码在这里抛 IllegalArgumentException，由 Controller 单独捕获
        FriendApplicationStatusEnum statusEnum = FriendApplicationStatusEnum.fromCode(status);

        return switch (statusEnum) {
            case ACCEPTED -> handleAcceptApplication(receiverId, senderIds);
            case REJECTED -> handleRejectApplication(receiverId, senderIds);
            case READ -> {
                handleReadApplication(receiverId, senderIds);
                yield null;
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许修改为该状态值");
        };
    }

    /**
     * 处理通过好友申请
     *
     * @param receiverId 接收者用户ID
     * @param senderIds  发送者用户ID列表（只能有一个）
     * @return 新建的会话信息
     */
    protected ModifyFriendApplicationResponse handleAcceptApplication(Long receiverId, List<Long> senderIds) {
        ThrowUtils.throwIf(senderIds.size() != 1, ErrorCode.PARAMS_ERROR, "通过状态只能包含一个发送者");

        Long senderId = senderIds.get(0);

        ApplyFriend targetApply = findApplyFriendBySenderAndReceiver(senderId, receiverId);
        ThrowUtils.throwIf(targetApply == null, ErrorCode.NOT_FOUND_ERROR, "好友申请不存在");

        ModifyFriendApplicationResponse response = handleFriendRequest(targetApply, receiverId, true);
        ThrowUtils.throwIf(response == null, ErrorCode.OPERATION_ERROR, "处理好友申请失败");

        return response;
    }

    /**
     * 处理拒绝好友申请
     *
     * @param receiverId 接收者用户ID
     * @param senderIds  发送者用户ID列表（只能有一个）
     * @return null
     */
    protected ModifyFriendApplicationResponse handleRejectApplication(Long receiverId, List<Long> senderIds) {
        ThrowUtils.throwIf(senderIds.size() != 1, ErrorCode.PARAMS_ERROR, "拒绝状态只能包含一个发送者");

        Long senderId = senderIds.get(0);

        ApplyFriend targetApply = findApplyFriendBySenderAndReceiver(senderId, receiverId);
        ThrowUtils.throwIf(targetApply == null, ErrorCode.NOT_FOUND_ERROR, "好友申请不存在");

        handleFriendRequest(targetApply, receiverId, false);

        return null;
    }

    /**
     * 处理已读好友申请（可批量）
     *
     * @param receiverId 接收者用户ID
     * @param senderIds  发送者用户ID列表
     */
    protected void handleReadApplication(Long receiverId, List<Long> senderIds) {
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus, FriendApplicationStatusEnum.READ.getCode())
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getReceiverId, receiverId)
                .in(ApplyFriend::getSenderId, senderIds)
                .eq(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode());

        int updated = applyFriendMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(updated <= 0, ErrorCode.OPERATION_ERROR, "标记已读失败");
    }

    /**
     * 根据发送者和接收者查找好友申请
     *
     * @param senderId   发送者用户ID
     * @param receiverId 接收者用户ID
     * @return 好友申请记录，不存在时返回 null
     */
    private ApplyFriend findApplyFriendBySenderAndReceiver(Long senderId, Long receiverId) {
        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getSenderId, senderId)
                .eq(ApplyFriend::getReceiverId, receiverId);
        return this.getOne(queryWrapper);
    }

    /**
     * 处理好友申请（通过或拒绝）
     * <p>
     * 处理流程：
     * 1. 校验操作权限（只有接收者本人可以处理）
     * 2. 校验申请状态（只能处理未读或已读的申请）
     * 3. 更新申请状态
     * 4. 通过时建立好友关系并创建会话
     *
     * @param applyFriend 好友申请
     * @param receiverId  接收者用户ID（用于权限校验）
     * @param accept      true=通过，false=拒绝
     * @return 通过时返回会话信息，拒绝时返回 null
     */
    private ModifyFriendApplicationResponse handleFriendRequest(ApplyFriend applyFriend, Long receiverId, boolean accept) {
        // 1. 校验权限（只有接收者可以处理申请）
        ThrowUtils.throwIf(!applyFriend.getReceiverId().equals(receiverId),
                ErrorCode.NO_AUTH_ERROR, "无权处理该好友申请");

        // 2. 校验申请状态（只能处理未读或已读状态的申请）
        boolean isValidStatus = applyFriend.getStatus().equals(FriendApplicationStatusEnum.UNREAD.getCode())
                || applyFriend.getStatus().equals(FriendApplicationStatusEnum.READ.getCode());
        ThrowUtils.throwIf(!isValidStatus, ErrorCode.OPERATION_ERROR, "该好友申请已处理或已过期");

        // 3. 更新申请状态
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus,
                        accept ? FriendApplicationStatusEnum.ACCEPTED.getCode()
                                : FriendApplicationStatusEnum.REJECTED.getCode())
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getApplyFriendId, applyFriend.getApplyFriendId());
        boolean updated = this.update(updateWrapper);

        // 4. 通过时建立好友关系并创建会话
        if (accept && updated) {
            User receiver = userService.getById(receiverId);
            return friendService.addFriend(receiver, applyFriend.getSenderId());
        }

        // 5. 拒绝或更新失败时返回 null
        return null;
    }
}
