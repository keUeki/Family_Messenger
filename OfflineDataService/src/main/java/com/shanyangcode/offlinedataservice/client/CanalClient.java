package com.shanyangcode.offlinedataservice.client;


import java.text.SimpleDateFormat;
import java.util.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;


import com.alibaba.otter.canal.protocol.Message;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.model.dto.MessageBody;
import com.shanyangcode.common.model.vo.MessageResponse;

import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
@SuppressWarnings("BusyWait")
public class CanalClient implements CommandLineRunner {

    @Resource
    private CanalConnector canalConnector;


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final int MAX_RETRY_TIMES = 5; // Maximum number of retries after an error

    private static final long INITIAL_RETRY_DELAY = 1000; // Initial retry delay: 1 second

    private static final long MAX_RETRY_DELAY = 60000; // Maximum retry delay: 60 seconds

    private static final long HEARTBEAT_INTERVAL = 30000; // Send a heartbeat every 30 seconds

    private static final long IDLE_CHECK_INTERVAL = 5000; // Check the idle state every 5 seconds
    // Tables to watch (all lower-case; MySQL schema/table case sensitivity differs across platforms)
    private static final Set<String> MONITOR_TABLES = Set.of("infinitechat.message");

    @Override
    public void run(String... args) {
        new Thread(this::process).start();
    }


    private void process() {
        log.info("====== Canal consumer thread started ======");

        int batchSize = 1000;
        int retryTimes = 0;
        long retryDelay = INITIAL_RETRY_DELAY;
        long lastActiveTime = System.currentTimeMillis();

        while (true) {
            try {
                // Check the connection state
                if (!canalConnector.checkValid()) {
                    reconnectCanal();
                    retryTimes = 0;
                    retryDelay = INITIAL_RETRY_DELAY;
                    lastActiveTime = System.currentTimeMillis();
                }

                // Check whether a heartbeat is due
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastActiveTime > HEARTBEAT_INTERVAL) {
                    sendHeartbeat();
                    lastActiveTime = currentTime;
                    continue;
                }

                Message message = canalConnector.getWithoutAck(batchSize);
                long batchId = message.getId();
                int size = message.getEntries().size();

                if (batchId == -1 || size == 0) {
                    // Sleep briefly when there is no data, to avoid spinning the CPU
                    Thread.sleep(IDLE_CHECK_INTERVAL);
                    continue;
                }

                lastActiveTime = System.currentTimeMillis(); // Update the last-active timestamp

                try {
                    handleMessage(message.getEntries());
                    canalConnector.ack(batchId);
                    retryTimes = 0;
                    retryDelay = INITIAL_RETRY_DELAY;
                } catch (Exception e) {
                    log.error("Error while processing the message payload, rolling back", e);
                    safeRollback(batchId);
                    throw e;
                }

            } catch (Exception e) {
                log.error("Error while processing the Canal message", e);

                if (retryTimes++ >= MAX_RETRY_TIMES) {
                    log.error("Reached the maximum of {} retries, waiting before trying again", MAX_RETRY_TIMES);
                    retryTimes = 0;
                    try {
                        Thread.sleep(MAX_RETRY_DELAY);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                long sleepTime = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
                log.warn("Reconnecting in {} second(s), attempt {}...", sleepTime / 1000, retryTimes);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                retryDelay = sleepTime;
            }
        }
    }


    /**
     * Sends a heartbeat to keep the connection alive
     */
    private void sendHeartbeat() {
        try {
            // Send an empty ack as the heartbeat
            canalConnector.ack(-1);
            log.debug("Sent a heartbeat to keep the connection alive");
        } catch (Exception e) {
            log.error("Failed to send the heartbeat", e);
            try {
                if (canalConnector.checkValid()) {
                    canalConnector.disconnect();
                }
            } catch (Exception ex) {
                log.error("Error while disconnecting", ex);
            }
        }
    }

    private void reconnectCanal() {
        try {
            canalConnector.disconnect();
            canalConnector.connect();
            canalConnector.subscribe();
            log.info("Reconnected to the Canal server");
        } catch (Exception e) {
            log.error("Failed to connect to the Canal server", e);
            throw e;
        }
    }

    private void safeRollback(long batchId) {
        try {
            canalConnector.rollback(batchId);
        } catch (Exception ex) {
            log.error("Error while rolling back the Canal message", ex);
            try {
                if (canalConnector.checkValid()) {
                    canalConnector.disconnect();
                }
            } catch (Exception e) {
                log.error("Error while disconnecting", e);
            }
        }
    }


    private void handleMessage(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            CanalEntry.RowChange rowChange;
            try {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse the binlog event", e);
            }

            String schemaName = entry.getHeader().getSchemaName();
            String tableName = entry.getHeader().getTableName();
            String fullTableName = schemaName + "." + tableName;

            log.info("====== Change received: fullTableName={} ======", fullTableName);

            // The schema name in the binlog is InfiniteChat, so the comparison against the lower-case
            // configured table names must be case-insensitive; otherwise every change is dropped, the Redis hot cache stays empty and history queries return nothing.
            if (!MONITOR_TABLES.contains(fullTableName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            System.out.println("Table name: " + tableName);
            CanalEntry.EventType eventType = rowChange.getEventType();

            log.info("======> binlog[{}:{}], name[{},{}], eventType: {}", entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(), schemaName, tableName, eventType);


            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                if (rowChange.getEventType() == CanalEntry.EventType.INSERT) {
                    handleInsert(rowData.getAfterColumnsList(), tableName);

                }
            }
        }
    }


    // Inside the Canal handling method
    private void handleInsert(List<CanalEntry.Column> columns, String tableName) {
        Map<String, String> map = new HashMap<>();
        for (CanalEntry.Column column : columns) {
            map.put(column.getName(), column.getValue());
        }
        log.info("Table name: {}, row: {}", tableName, map);

        // 1. Build the complete message object
        MessageResponse messageResponse = buildMessageFromMap(map);
        log.info("Message body: {}", messageResponse);
        storeMessageToRedis(messageResponse);

    }

    private MessageResponse buildMessageFromMap(Map<String, String> map) {
        MessageResponse messageResponse = new MessageResponse();
        messageResponse.setSessionId(Long.valueOf(map.get("session_id")));
        messageResponse.setSenderId(Long.valueOf(map.get("sender_id")));
        messageResponse.setMessageId(Long.valueOf(map.get("message_id")));
        Integer type = Integer.valueOf(map.get("type"));
        messageResponse.setType(type);
        messageResponse.setSessionType(Integer.valueOf(map.get("session_type")));
        messageResponse.setCreatedTime(map.get("created_time"));

        // Parse the content according to the message type
        MessageBody body = new MessageBody();
        body.setContent(map.get("content"));
        if (StringUtils.isEmpty(map.get("reply_id"))) {
            body.setReplyId(null);
        } else {
            body.setReplyId(Long.valueOf(map.get("reply_id")));
        }
        messageResponse.setBody(body);
        return messageResponse;
    }



    private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf;
    });


    private void storeMessageToRedis(MessageResponse messageResponse) {
        String key = CommonConstant.SESSION_KEY_REDIS + messageResponse.getSessionId();
        String messageJson = JSON.toJSONString(messageResponse);

        try {
            double score = DATE_FORMATTER.get().parse(messageResponse.getCreatedTime()).getTime();
            long cutoff = System.currentTimeMillis() - CommonConstant.SEVEN_DAYS_MILLIS;

            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("NullableProblems")
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    StringRedisTemplate template = (StringRedisTemplate) operations;
                    // Write the message
                    template.opsForZSet().add(key, messageJson, score);
                    // Purge data older than 7 days
                    template.opsForZSet().removeRangeByScore(key, 0, cutoff);
                    return null;
                }
            });

            log.debug("Message stored in Redis, sessionId={}, messageId={}",
                    messageResponse.getSessionId(), messageResponse.getMessageId());

        } catch (Exception e) {
            log.error("Failed to store the message in Redis, sessionId={}, messageId={}",
                    messageResponse.getSessionId(), messageResponse.getMessageId(), e);
        }
    }

}