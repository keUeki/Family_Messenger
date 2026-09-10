package com.shanyangcode.userservice.scheduler;

import java.util.Collections;
import java.util.List;

import com.shanyangcode.userservice.constants.KafkaTopicConstant;
import com.shanyangcode.userservice.model.dto.FriendRequestExpirationEvent;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that dispatches expired friend requests (a timing-wheel style job)
 *
 * Responsibilities:
 * - Runs once a second, scanning the Redis ZSET for friend requests that are due
 * - Publishes the expired friend request ids to Kafka
 * - FriendRequestExpirationExecutor consumes them and applies the expiry
 *
 * How it works (mirrors the timing wheel used by the red-packet module):
 * 1. A Redis ZSET holds the delayed tasks, scored by expiry timestamp
 * 2. The scheduled job advances the wheel every second, scanning for due tasks
 * 3. A Lua script atomically fetches and removes the due members
 * 4. The due tasks go to Kafka, where a separate consumer handles them
 *
 * Distributed coordination:
 * - ShedLock ensures only one instance runs the job in a cluster
 */
@Slf4j
@Component
public class FriendRequestExpirationDispatcher {

    private static final String ZSET_KEY = "friend-request-expire-zset";
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_TICK = 5;
    private static final long TIME_BUDGET_MS = 400;

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DefaultRedisScript<List> scanExpiredScript;

    public FriendRequestExpirationDispatcher(
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("scanExpiredFriendRequestsScript") DefaultRedisScript<List> scanExpiredScript) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.scanExpiredScript = scanExpiredScript;
    }

    /**
     * Scans for due tasks on a timer and dispatches them
     *
     * Execution policy:
     * - Frequency: once a second (fixedRate = 1000ms)
     * - Distributed lock: lockAtMostFor = 800ms, so two instances never run it at once
     * - Batching: at most 5 batches per run, at most 500 entries per batch
     * - Time budget: a single run stays under 400ms so the scheduler thread is not blocked
     *
     * Processing steps:
     * 1. Use the Lua script to atomically fetch the ids of the due friend requests
     * 2. Walk the id list and publish each one to Kafka
     * 3. A batch smaller than BATCH_SIZE means the queue is drained, so stop
     * 4. Stop as soon as the time budget or the batch limit is reached
     */
    @Scheduled(fixedRate = 1000)  // runs once a second
    @SchedulerLock(
            name = "FriendRequestExpirationDispatcher",
            lockAtMostFor = "800ms",   // hold the lock for at most 800ms
            lockAtLeastFor = "200ms"   // hold the lock for at least 200ms
    )
    public void dispatch() {
        long start = System.currentTimeMillis();
        int batches = 0;

        try {
            while (batches < MAX_BATCHES_PER_TICK && (System.currentTimeMillis() - start) < TIME_BUDGET_MS) {

                // 1. Run the Lua script to fetch the ids of the due friend requests
                List<String> expiredIds = redisTemplate.execute(
                        scanExpiredScript,
                        Collections.singletonList(ZSET_KEY),
                        "0",  // let the Lua script read the current time via the Redis TIME command
                        String.valueOf(BATCH_SIZE)
                );

                if (expiredIds == null || expiredIds.isEmpty()) {
                    // Nothing more is due; stop
                    break;
                }

                log.info("Found {} expired friend request(s)", expiredIds.size());

                // 2. Publish them to Kafka one by one
                for (String applyFriendIdStr : expiredIds) {
                    try {
                        Long applyFriendId = Long.parseLong(applyFriendIdStr);

                        FriendRequestExpirationEvent event = new FriendRequestExpirationEvent();
                        event.setApplyFriendId(applyFriendId);
                        event.setExpireTime(System.currentTimeMillis());

                        String eventJson = JSONUtil.toJsonStr(event);
                        kafkaTemplate.send(
                                KafkaTopicConstant.TOPIC_FRIEND_REQUEST_EXPIRATION,
                                String.valueOf(applyFriendId),  // use applyFriendId as the partition key
                                eventJson
                        );

                        log.debug("Friend-request expiry event dispatched, request id: {}", applyFriendId);

                    } catch (Exception e) {
                        log.error("Failed to dispatch the friend-request expiry event, request id: {}, error: {}",
                                applyFriendIdStr, e.getMessage(), e);
                    }
                }

                batches++;

                // 3. A short batch means the queue is drained; stop
                if (expiredIds.size() < BATCH_SIZE) {
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            if (batches > 0) {
                log.info("Friend-request expiry dispatch finished, batches: {}, elapsed: {}ms", batches, elapsed);
            }

        } catch (Exception e) {
            log.error("Friend-request expiry dispatch failed: {}", e.getMessage(), e);
        }
    }
}