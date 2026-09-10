package com.shanyangcode.userservice.consumer;

import com.shanyangcode.userservice.model.dto.FriendRequestCreationEvent;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.shanyangcode.userservice.constants.KafkaTopicConstant.GROUP_FRIEND_REQUEST_ENQUEUER;
import static com.shanyangcode.userservice.constants.KafkaTopicConstant.TOPIC_FRIEND_REQUEST_CREATION;

/**
 * Consumer for friend-request creation events (registers the delayed task)
 * <p>
 * Responsibilities:
 * - Consumes friend-request creation events
 * - Registers the friend request id in the Redis ZSET delay queue
 * - The ZSET score is the expiry timestamp, so the scheduled job can scan it cheaply
 * <p>
 * Data flow:
 * ApplyFriendService creates a friend request -> Kafka: friend-request-creation-topic
 * -> this consumer -> Redis ZSET: friend-request-expire-zset
 */
@Slf4j
@Component
public class FriendRequestExpirationEnqueuer {

    private static final String ZSET_KEY = "friend-request-expire-zset";
    private final StringRedisTemplate redisTemplate;

    public FriendRequestExpirationEnqueuer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Consumes a friend-request creation event and registers the delayed expiry task
     * <p>
     * Processing steps:
     * 1. Parse the Kafka message to obtain the friend request id and expiry time
     * 2. ZADD the friend request id into the ZSET
     * 3. Set the score to the expiry timestamp in milliseconds
     * 4. The scheduled job periodically scans the ZSET and pops the ids that are due
     *
     * @param message the Kafka message (a FriendRequestCreationEvent in JSON)
     */
    @KafkaListener(
            topics = TOPIC_FRIEND_REQUEST_CREATION,
            groupId = GROUP_FRIEND_REQUEST_ENQUEUER,
            concurrency = "1"  // single-threaded consumption to preserve ordering
    )
    public void enqueueExpirationTask(String message) {
        try {
            log.info("Friend-request creation event received: {}", message);

            // 1. Parse the event
            FriendRequestCreationEvent event = JSONUtil.toBean(message, FriendRequestCreationEvent.class);
            Long applyFriendId = event.getApplyFriendId();
            Long expireTime = event.getExpireTime();

            if (applyFriendId == null || expireTime == null) {
                log.error("Friend-request event is incomplete, applyFriendId: {}, expireTime: {}", applyFriendId, expireTime);
                return;
            }

            if (expireTime <= System.currentTimeMillis()) {
                log.warn("Friend request has already expired, skipping registration, request id: {}, expiry: {}", applyFriendId, expireTime);
                return;
            }

            // 2. Register it in the Redis ZSET
            // ZADD friend-request-expire-zset <expireTime> <applyFriendId>
            Boolean result = redisTemplate.opsForZSet().add(
                    ZSET_KEY,
                    String.valueOf(applyFriendId),
                    expireTime.doubleValue()
            );

            if (Boolean.TRUE.equals(result)) {
                log.info("Friend-request expiry task registered, request id: {}, expiry: {}", applyFriendId, expireTime);
            } else {
                log.info("Failed to register the friend-request expiry task, request id: {}, expiry: {}", applyFriendId, expireTime);
            }

        } catch (Exception e) {
            log.error("Failed to handle the friend-request creation event: {}, error: {}", message, e.getMessage(), e);
        }
    }
}