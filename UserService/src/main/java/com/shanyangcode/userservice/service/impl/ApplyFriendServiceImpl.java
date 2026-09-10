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
 * Friend request service implementation
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
     * Perspective flag used in the request list: I am the sender / I am the receiver
     */
    private static final int IS_RECEIVER_NO = 0;
    private static final int IS_RECEIVER_YES = 1;

    /**
     * How long a friend request stays valid (24 hours)
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
     * Sends a friend request
     * <p>
     * Processing steps:
     * 1. Check that both the sender and the receiver exist and are not the same user
     * 2. Check whether they are already friends
     * 3. Check for an existing pending request
     * 4a. None: insert a new request row and asynchronously emit the Kafka events (notification + expiry)
     * 4b. One exists and was accepted: report that they are already friends
     * 4c. One exists in another state (read / rejected / expired): reuse the row, reset the status to UNREAD, update the message, and asynchronously emit the Kafka events (notification + expiry)
     * 5. Return the applyFriendId
     *
     * @param senderId   the sender's user id
     * @param receiverId the receiver's user id
     * @param message    the request message
     * @return the friend request id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendFriendRequest(Long senderId, Long receiverId, String message) {
        // 1. Check that both users exist
        User sender = userService.getById(senderId);
        ThrowUtils.throwIf(sender == null, ErrorCode.NOT_FOUND_ERROR, "The sender does not exist");

        User receiver = userService.getById(receiverId);
        ThrowUtils.throwIf(receiver == null, ErrorCode.NOT_FOUND_ERROR, "The receiver does not exist");

        // Reject a request addressed to oneself
        ThrowUtils.throwIf(senderId.equals(receiverId), ErrorCode.OPERATION_ERROR, "You cannot add yourself");

        // 2. Check whether they are already friends, or blocked
        boolean isFriend = friendService.lambdaQuery()
                .eq(Friend::getUserId, senderId)
                .eq(Friend::getFriendId, receiverId)
                .exists();
        ThrowUtils.throwIf(isFriend, ErrorCode.OPERATION_ERROR, "You are already friends; there is no need to add them again");


        // 3. Check for an existing pending request
        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getSenderId, senderId)
                .eq(ApplyFriend::getReceiverId, receiverId);
        ApplyFriend existingApplyFriend = this.getOne(queryWrapper);

        Long applyFriendId = null;
        if (existingApplyFriend == null) {
            // 4a. None: insert a new request row and asynchronously emit the Kafka events (notification + expiry)
            applyFriendId = handleNewFriendApplication(senderId, receiverId, message, sender);
        } else if (existingApplyFriend.getStatus().equals(FriendApplicationStatusEnum.ACCEPTED.getCode())) {
            // 4b. One exists and was accepted: report that they are already friends
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "You are already friends; there is no need to add them again");
        } else {
            // 4c. One exists in another state (read / rejected / expired): reuse the row, reset the status to UNREAD, update the message, and asynchronously emit the Kafka events (notification + expiry)
            applyFriendId = handleExistingFriendApplication(existingApplyFriend, message, sender);
        }

        // 5. Return the applyFriendId
        return applyFriendId;
    }

    /**
     * Handles a brand-new friend request
     *
     * @param senderId   the sender's id
     * @param receiverId the receiver's id
     * @param message    the request message
     * @param sender     the sender's user record
     * @return the friend request id
     */
    private Long handleNewFriendApplication(Long senderId, Long receiverId, String message, User sender) {
        // 1. Create the friend request row
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
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "Failed to create the friend request");

        // 2. Publish the Kafka notification (asynchronously)
        sendFriendApplicationNotification(receiverId, sender, message);

        // 3. Publish the expiry-registration event (asynchronously)
        registerExpirationTask(applyFriendId);

        return applyFriendId;
    }


    /**
     * Handles an existing friend request row
     *
     * @param existingApplyFriend the existing friend request
     * @param message             the request message
     * @param sender              the sender's user record
     * @return the friend request id
     */
    private Long handleExistingFriendApplication(ApplyFriend existingApplyFriend, String message, User sender) {
        // 1. Update the friend request row
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode())
                .set(ApplyFriend::getMessage, message)
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getApplyFriendId, existingApplyFriend.getApplyFriendId());

        boolean updated = this.update(updateWrapper);
        ThrowUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR, "Failed to update the friend request");

        // 2. Publish the Kafka notification (asynchronously)
        sendFriendApplicationNotification(existingApplyFriend.getReceiverId(), sender, message);

        // 3. Re-register the expiry task (asynchronously)
        registerExpirationTask(existingApplyFriend.getApplyFriendId());

        return existingApplyFriend.getApplyFriendId();
    }


    /**
     * Publishes the friend request notification over Kafka
     *
     * @param receiverId the receiver's id
     * @param sender     the sender's user record
     * @param message    the message attached to the request
     */
    private void sendFriendApplicationNotification(Long receiverId, User sender, String message) {
        try {
            FriendApplicationNotificationDTO notification = new FriendApplicationNotificationDTO();
            notification.setApplyUserName(sender.getNickname());
            notification.setApplyUserId(sender.getUserId());
            notification.setApplyFriendAvatar(sender.getAvatar());
            notification.setMessage(message);

            notificationService.pushNewApply(receiverId, notification);
            log.info("Friend request notification published, receiver id: {}, sender id: {}", receiverId, sender.getUserId());
        } catch (Exception e) {
            log.warn("Failed to publish the friend request notification, receiver id: {}, sender id: {}, cause: {}",
                    receiverId, sender.getUserId(), e.getMessage());
        }
    }

    /**
     * Registers the friend request expiry task over Kafka
     *
     * @param applyFriendId the friend request id
     */
    private void registerExpirationTask(Long applyFriendId) {
        try {
            // Compute the expiry (now + 24 hours)
            long createTime = System.currentTimeMillis();
            long expireTime = createTime + (FRIEND_REQUEST_EXPIRATION_HOURS * 60 * 60 * 1000);

            // Build the expiry event
            FriendRequestCreationEvent event = new FriendRequestCreationEvent();
            event.setApplyFriendId(applyFriendId);
            event.setCreateTime(createTime);
            event.setExpireTime(expireTime);

            // Publish it to the Kafka topic
            kafkaTemplate.send(
                    KafkaTopicConstant.TOPIC_FRIEND_REQUEST_CREATION,
                    String.valueOf(applyFriendId),
                    JSONUtil.toJsonStr(event)
            );

            log.info("Friend request expiry task registered, request id: {}, expiry: {}", applyFriendId, expireTime);
        } catch (Exception e) {
            log.error("Failed to register the friend request expiry task, request id: {}, cause: {}", applyFriendId, e.getMessage());
        }
    }


    /**
     * Returns a page of the friend requests involving this user, including the other party's details
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @return a page of request DTOs
     */
    @Override
    public IPage<ApplyFriendDTO> getReceivedRequestsWithUserInfo(Long userId, PageRequest pageRequest) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "User id must not be empty");

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();

        // 1. Query a page of friend request entities
        IPage<ApplyFriend> applyFriendPage = getReceivedRequests(userId, pageNum, pageSize);

        // 2. Map them to DTOs, batch-filling the other party's details
        List<ApplyFriendDTO> dtoList = mapApplyFriendsToDTO(applyFriendPage.getRecords(), userId);

        // 3. Build the DTO page, reusing the entity page's total
        Page<ApplyFriendDTO> dtoPage = new Page<>(pageNum, pageSize, applyFriendPage.getTotal());
        dtoPage.setRecords(dtoList);

        return dtoPage;
    }

    /**
     * Returns a page of the friend request entities involving this user
     * <p>
     * Note: the predicate is senderId = userId OR receiverId = userId, so it covers both the
     * requests I sent and the ones I received. That is deliberate: it is exactly what makes
     * the isReceiver field on the DTO meaningful.
     * By contrast, getUnreadCount counts only the requests I received.
     *
     * @param userId   the user id
     * @param pageNum  the page number
     * @param pageSize the page size
     * @return a page of request entities
     */
    private IPage<ApplyFriend> getReceivedRequests(Long userId, int pageNum, int pageSize) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "User id must not be empty");

        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq(ApplyFriend::getSenderId, userId)
                        .or()
                        .eq(ApplyFriend::getReceiverId, userId))
                .orderByDesc(ApplyFriend::getUpdatedTime);

        // The pagination plugin (MybatisPlusConfig in the Common module) adds the LIMIT and computes total
        Page<ApplyFriend> page = new Page<>(pageNum, pageSize);
        return applyFriendMapper.selectPage(page, queryWrapper);
    }

    /**
     * Maps friend request rows onto DTOs
     * <p>
     * Handles the sender/receiver perspective switch: the user details on the DTO always describe the other party.
     *
     * @param applyFriends the friend request rows
     * @param userId       the current user's id
     * @return the DTO list
     */
    private List<ApplyFriendDTO> mapApplyFriendsToDTO(List<ApplyFriend> applyFriends, Long userId) {
        if (applyFriends == null || applyFriends.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Collect every other party's user id up front, so the loop does not issue N+1 getById calls
        Set<Long> targetUserIds = new HashSet<>();
        for (ApplyFriend applyFriend : applyFriends) {
            Long targetId = applyFriend.getSenderId().equals(userId)
                    ? applyFriend.getReceiverId()
                    : applyFriend.getSenderId();
            if (targetId != null) {
                targetUserIds.add(targetId);
            }
        }

        // 2. Fetch all the user records in a single query
        Map<Long, User> userMap = new HashMap<>();
        if (!targetUserIds.isEmpty()) {
            List<User> users = userService.listByIds(targetUserIds);
            if (users != null) {
                for (User user : users) {
                    userMap.put(user.getUserId(), user);
                }
            }
        }

        // 3. Build the DTOs one by one
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
            if (targetUser != null) { // Defensive: if the other account was hard-deleted, leave the row's fields empty
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
     * Returns the number of unread friend requests
     *
     * @param userId the user id
     * @return the unread count
     */
    @Override
    public int getUnreadCount(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "User id must not be empty");

        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getReceiverId, userId)
                .eq(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode());

        return Math.toIntExact(applyFriendMapper.selectCount(queryWrapper));
    }

    /**
     * Updates the status of one or more friend requests
     * <p>
     * The transaction boundary has to sit on this public entry point: the handleXxx methods below are
     * protected and called from within this class, so the Spring proxy never sees them and their own @Transactional has no effect.
     *
     * @param receiverId the receiver's user id (the current actor)
     * @param senderIds  the user ids of the request senders
     * @param status     the target status code
     * @return the newly created session when a request is accepted, otherwise null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModifyFriendApplicationResponse modifyApplicationStatus(Long receiverId, List<Long> senderIds, Integer status) {
        ThrowUtils.throwIf(receiverId == null, ErrorCode.PARAMS_ERROR, "Receiver id must not be empty");
        ThrowUtils.throwIf(senderIds == null || senderIds.isEmpty(), ErrorCode.PARAMS_ERROR, "Sender id list must not be empty");

        // An invalid status code throws IllegalArgumentException here, which the controller catches separately
        FriendApplicationStatusEnum statusEnum = FriendApplicationStatusEnum.fromCode(status);

        return switch (statusEnum) {
            case ACCEPTED -> handleAcceptApplication(receiverId, senderIds);
            case REJECTED -> handleRejectApplication(receiverId, senderIds);
            case READ -> {
                handleReadApplication(receiverId, senderIds);
                yield null;
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "That status value is not allowed");
        };
    }

    /**
     * Accepts a friend request
     *
     * @param receiverId the receiver's user id
     * @param senderIds  the sender user ids (exactly one)
     * @return the newly created session
     */
    protected ModifyFriendApplicationResponse handleAcceptApplication(Long receiverId, List<Long> senderIds) {
        ThrowUtils.throwIf(senderIds.size() != 1, ErrorCode.PARAMS_ERROR, "Accepting a request takes exactly one sender");

        Long senderId = senderIds.get(0);

        ApplyFriend targetApply = findApplyFriendBySenderAndReceiver(senderId, receiverId);
        ThrowUtils.throwIf(targetApply == null, ErrorCode.NOT_FOUND_ERROR, "The friend request does not exist");

        ModifyFriendApplicationResponse response = handleFriendRequest(targetApply, receiverId, true);
        ThrowUtils.throwIf(response == null, ErrorCode.OPERATION_ERROR, "Failed to process the friend request");

        return response;
    }

    /**
     * Rejects a friend request
     *
     * @param receiverId the receiver's user id
     * @param senderIds  the sender user ids (exactly one)
     * @return null
     */
    protected ModifyFriendApplicationResponse handleRejectApplication(Long receiverId, List<Long> senderIds) {
        ThrowUtils.throwIf(senderIds.size() != 1, ErrorCode.PARAMS_ERROR, "Rejecting a request takes exactly one sender");

        Long senderId = senderIds.get(0);

        ApplyFriend targetApply = findApplyFriendBySenderAndReceiver(senderId, receiverId);
        ThrowUtils.throwIf(targetApply == null, ErrorCode.NOT_FOUND_ERROR, "The friend request does not exist");

        handleFriendRequest(targetApply, receiverId, false);

        return null;
    }

    /**
     * Marks friend requests as read (accepts several at once)
     *
     * @param receiverId the receiver's user id
     * @param senderIds  the sender user ids
     */
    protected void handleReadApplication(Long receiverId, List<Long> senderIds) {
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus, FriendApplicationStatusEnum.READ.getCode())
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getReceiverId, receiverId)
                .in(ApplyFriend::getSenderId, senderIds)
                .eq(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode());

        int updated = applyFriendMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(updated <= 0, ErrorCode.OPERATION_ERROR, "Failed to mark the requests as read");
    }

    /**
     * Looks up a friend request by sender and receiver
     *
     * @param senderId   the sender's user id
     * @param receiverId the receiver's user id
     * @return the friend request row, or null when there is none
     */
    private ApplyFriend findApplyFriendBySenderAndReceiver(Long senderId, Long receiverId) {
        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getSenderId, senderId)
                .eq(ApplyFriend::getReceiverId, receiverId);
        return this.getOne(queryWrapper);
    }

    /**
     * Processes a friend request, accepting or rejecting it
     * <p>
     * Processing steps:
     * 1. Check the permission (only the receiver may act on it)
     * 2. Check the status (only unread or read requests can be acted on)
     * 3. Update the request status
     * 4. On acceptance, create the friendship and the session
     *
     * @param applyFriend the friend request
     * @param receiverId  the receiver's user id (used for the permission check)
     * @param accept      true to accept, false to reject
     * @return the session when accepting, null when rejecting
     */
    private ModifyFriendApplicationResponse handleFriendRequest(ApplyFriend applyFriend, Long receiverId, boolean accept) {
        // 1. Check the permission (only the receiver may act on the request)
        ThrowUtils.throwIf(!applyFriend.getReceiverId().equals(receiverId),
                ErrorCode.NO_AUTH_ERROR, "You are not allowed to act on this friend request");

        // 2. Check the status (only unread or read requests can be acted on)
        boolean isValidStatus = applyFriend.getStatus().equals(FriendApplicationStatusEnum.UNREAD.getCode())
                || applyFriend.getStatus().equals(FriendApplicationStatusEnum.READ.getCode());
        ThrowUtils.throwIf(!isValidStatus, ErrorCode.OPERATION_ERROR, "That friend request has already been handled or has expired");

        // 3. Update the request status
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus,
                        accept ? FriendApplicationStatusEnum.ACCEPTED.getCode()
                                : FriendApplicationStatusEnum.REJECTED.getCode())
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getApplyFriendId, applyFriend.getApplyFriendId());
        boolean updated = this.update(updateWrapper);

        // 4. On acceptance, create the friendship and the session
        if (accept && updated) {
            User receiver = userService.getById(receiverId);
            return friendService.addFriend(receiver, applyFriend.getSenderId());
        }

        // 5. Return null when rejecting, or when the update failed
        return null;
    }
}
