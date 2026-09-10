package com.shanyangcode.userservice.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shanyangcode.userservice.constants.FriendApplicationStatusEnum;
import com.shanyangcode.userservice.model.dto.FriendRequestExpirationEvent;
import com.shanyangcode.userservice.model.entity.ApplyFriend;
import com.shanyangcode.userservice.service.ApplyFriendService;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer for friend-request expiry events (performs the expiry)
 *
 * Responsibilities:
 * - Consumes friend-request expiry events
 * - Performs the expiry: sets the request's database status to EXPIRED
 * - Only touches requests that have not been acted on (status UNREAD or READ)
 *
 * Data flow:
 * FriendRequestExpirationDispatcher finds a due task -> Kafka: friend-request-expiration-topic
 * -> this consumer -> the database status becomes EXPIRED
 *
 * Notes:
 * - A request that was ACCEPTED or REJECTED must never be marked expired
 * - Idempotency: if the status is already EXPIRED the event is skipped
 */
@Slf4j
@Component
public class FriendRequestExpirationExecutor {

    private final ApplyFriendService applyFriendService;

    public FriendRequestExpirationExecutor(ApplyFriendService applyFriendService) {
        this.applyFriendService = applyFriendService;
    }

    /**
     * Consumes a friend-request expiry event and applies the expiry
     *
     * Processing steps:
     * 1. Parse the Kafka message to obtain the friend request id
     * 2. Load the friend request row from the database
     * 3. Check the current status; only UNREAD and READ are processed
     * 4. Set the status to EXPIRED
     * 5. Log the outcome
     *
     * @param message the Kafka message (a FriendRequestExpirationEvent in JSON)
     */
    @KafkaListener(
            topics = "friend-request-expiration-topic",
            groupId = "friend-request-executor-group",
            concurrency = "3"  // processed concurrently
    )
    public void executeExpiration(String message) {
        try {
            log.info("Friend-request expiry event received: {}", message);

            // 1. Parse the event
            FriendRequestExpirationEvent event = JSONUtil.toBean(message, FriendRequestExpirationEvent.class);
            Long applyFriendId = event.getApplyFriendId();

            // 2. Load the friend request row
            ApplyFriend applyFriend = applyFriendService.getById(applyFriendId);

            if (applyFriend == null) {
                log.warn("Friend request does not exist, request id: {}", applyFriendId);
                return;
            }

            // 3. Check the current status
            Integer currentStatus = applyFriend.getStatus();

            if (currentStatus.equals(FriendApplicationStatusEnum.EXPIRED.getCode())) {
                // Already expired; handled idempotently
                log.info("Friend request is already expired, skipping, request id: {}", applyFriendId);
                return;
            }

            if (currentStatus.equals(FriendApplicationStatusEnum.ACCEPTED.getCode())) {
                // Already accepted; must not be marked expired
                log.info("Friend request was accepted, so it must not be marked expired, request id: {}", applyFriendId);
                return;
            }

            if (currentStatus.equals(FriendApplicationStatusEnum.REJECTED.getCode())) {
                // Already rejected; must not be marked expired
                log.info("Friend request was rejected, so it must not be marked expired, request id: {}", applyFriendId);
                return;
            }

            // 4. Set the status to EXPIRED (only for requests still UNREAD or READ)
            LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.set(ApplyFriend::getStatus, FriendApplicationStatusEnum.EXPIRED.getCode())
                    .eq(ApplyFriend::getApplyFriendId, applyFriendId)
                    .in(ApplyFriend::getStatus,
                            FriendApplicationStatusEnum.UNREAD.getCode(),
                            FriendApplicationStatusEnum.READ.getCode()
                    );

            boolean updated = applyFriendService.update(updateWrapper);

            if (updated) {
                log.info("Friend request marked as expired, request id: {}", applyFriendId);
            } else {
                log.warn("Failed to update the friend request status (it may already have been handled), request id: {}", applyFriendId);
            }

        } catch (Exception e) {
            log.error("Failed to handle the friend-request expiry event: {}, error: {}", message, e.getMessage(), e);
        }
    }
}