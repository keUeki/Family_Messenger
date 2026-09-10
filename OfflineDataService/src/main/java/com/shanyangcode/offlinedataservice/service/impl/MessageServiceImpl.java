package com.shanyangcode.offlinedataservice.service.impl;

import cn.hutool.core.bean.BeanUtil;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.dto.MessageBody;
import com.shanyangcode.common.model.dto.MessageRequest;
import com.shanyangcode.common.model.vo.MessageResponse;
import com.shanyangcode.common.utils.FormatDateUtil;
import com.shanyangcode.offlinedataservice.client.AiServiceClient;
import com.shanyangcode.offlinedataservice.client.UserServiceClient;
import com.shanyangcode.offlinedataservice.mapper.MessageMapper;
import com.shanyangcode.offlinedataservice.model.dto.HistoryMessageRequest;
import com.shanyangcode.offlinedataservice.model.dto.OfflineMessageRequest;
import com.shanyangcode.offlinedataservice.model.dto.SessionSummaryRequest;
import com.shanyangcode.offlinedataservice.model.entity.Message;
import com.shanyangcode.offlinedataservice.service.MessageService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
@Slf4j
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Override
    public void saveMessageToMySQL(MessageRequest messageRequest) {
        Message message = new Message();
        BeanUtil.copyProperties(messageRequest, message);
        message.setContent(messageRequest.getBody().getContent());
        message.setReplyId(messageRequest.getBody().getReplyId());
        ThrowUtils.throwIf(!this.save(message), ErrorCode.SYSTEM_ERROR);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private UserServiceClient userServiceClient;


    // ==================== Offline message queries ====================

    @Override
    public Map<Long, List<MessageResponse>> getOfflineMessages(OfflineMessageRequest request) {
        Long userId = request.getUserId();
        Long offlineTime = request.getOfflineTime();

        if (userId == null || offlineTime == null) {
            return Collections.emptyMap();
        }

        // 1. Load every session the user belongs to
        List<Long> sessionIds = userServiceClient.getSessionIdsByUserId(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. For each session, collect the messages sent since the user went offline
        Map<Long, List<MessageResponse>> result = new HashMap<>();
        long hotBoundary = System.currentTimeMillis() - CommonConstant.SEVEN_DAYS_MILLIS;

        for (Long sessionId : sessionIds) {
            List<MessageResponse> messages = getMessagesAfter(sessionId, offlineTime, hotBoundary);
            if (!messages.isEmpty()) {
                result.put(sessionId, messages);
            }
        }

        log.info("Offline message lookup finished for user {}; {} session(s) have new messages", userId, result.size());
        return result;
    }

    /**
     * Returns the messages sent after the given time (the offline backlog)
     */
    private List<MessageResponse> getMessagesAfter(Long sessionId, long afterTime, long hotBoundary) {
        List<MessageResponse> result = new ArrayList<>();

        // 1. Query Redis (hot data)
        if (afterTime >= hotBoundary) {
            // The offline point falls inside the hot window, so Redis alone is enough
            List<MessageResponse> redisMessages = getMessagesFromRedisAfter(sessionId, afterTime);
            result.addAll(redisMessages);
        } else {
            // The offline point falls into cold storage, so query both Redis and MySQL
            // First read all of the hot data from Redis
            List<MessageResponse> redisMessages = getMessagesFromRedisAfter(sessionId, hotBoundary);
            result.addAll(redisMessages);

            // Then read the cold data from MySQL
            List<MessageResponse> mysqlMessages = getMessagesFromMySQLAfter(sessionId, afterTime, hotBoundary);
            result.addAll(mysqlMessages);
        }

        // Sort ascending by time (oldest first)
        result.sort(Comparator.comparing(MessageResponse::getCreatedTime));
        return result;
    }

    /**
     * Reads the messages sent after the given time from Redis
     */
    private List<MessageResponse> getMessagesFromRedisAfter(Long sessionId, long afterTime) {
        String key = CommonConstant.SESSION_KEY_REDIS + sessionId;
        // (afterTime, +inf] — exclusive, so a message sent exactly at afterTime is skipped
        Set<String> messageJsonSet = stringRedisTemplate.opsForZSet()
                .rangeByScore(key, afterTime + 1, Double.MAX_VALUE);

        if (messageJsonSet == null || messageJsonSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<MessageResponse> messages = new ArrayList<>();
        for (String json : messageJsonSet) {
            messages.add(JSON.parseObject(json, MessageResponse.class));
        }
        return messages;
    }


    // ==================== Conversions ====================

    private List<MessageResponse> convertToResponses(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<MessageResponse> responses = new ArrayList<>();
        for (Message msg : messages) {
            MessageResponse response = new MessageResponse();
            response.setMessageId(msg.getMessageId());
            response.setSessionId(msg.getSessionId());
            response.setSenderId(msg.getSenderId());
            response.setType(msg.getType());
            response.setSessionType(msg.getSessionType());
            // Convert the MySQL Date to a timestamp string so it matches the Redis format
            response.setCreatedTime(String.valueOf(msg.getCreatedTime().getTime()));

            MessageBody body = new MessageBody();
            body.setContent(msg.getContent());
            body.setReplyId(msg.getReplyId());
            response.setBody(body);

            responses.add(response);
        }
        return responses;
    }

    @Override
    public List<MessageResponse> getHistoryMessages(HistoryMessageRequest request) {
        Long sessionId = request.getSessionId();
        Long beforeTime = request.getBeforeTime();
        int limit = request.getLimit() != null ? request.getLimit() : CommonConstant.DEFAULT_LIMIT;

        if (sessionId == null || beforeTime == null) {
            return Collections.emptyList();
        }

        long hotBoundary = System.currentTimeMillis() - CommonConstant.SEVEN_DAYS_MILLIS;

        List<MessageResponse> result = new ArrayList<>();

        // 1. If beforeTime falls inside the hot window, start with Redis
        if (beforeTime > hotBoundary) {
            List<MessageResponse> redisMessages = getMessagesFromRedisBefore(sessionId, beforeTime, limit);
            result.addAll(redisMessages);
        }

        // 2. If Redis did not return enough rows, fall back to MySQL
        if (result.size() < limit) {
            int remaining = limit - result.size();
            // beforeTime for the MySQL query: the oldest Redis message's time, or the original beforeTime
            long mysqlBeforeTime = beforeTime > hotBoundary ? hotBoundary : beforeTime;

            List<MessageResponse> mysqlMessages = getMessagesFromMySQLBefore(sessionId, mysqlBeforeTime, remaining);
            result.addAll(mysqlMessages);
        }

        // Sort descending by time (newest first, which is what scrolling back expects)
        result.sort(Comparator.comparing(MessageResponse::getCreatedTime).reversed());
        return result;
    }

    /**
     * Reads the messages sent before the given time from Redis
     */
    private List<MessageResponse> getMessagesFromRedisBefore(Long sessionId, long beforeTime, int limit) {
        String key = CommonConstant.SESSION_KEY_REDIS + sessionId;
        // [0, beforeTime) — inclusive lower bound, exclusive upper bound
        Set<String> messageJsonSet = stringRedisTemplate.opsForZSet()
                .reverseRangeByScore(key, 0, beforeTime - 1, 0, limit);

        if (messageJsonSet == null || messageJsonSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<MessageResponse> messages = new ArrayList<>();
        for (String json : messageJsonSet) {
            messages.add(JSON.parseObject(json, MessageResponse.class));
        }
        return messages;
    }

    /**
     * Reads the messages sent before the given time from MySQL
     */
    private List<MessageResponse> getMessagesFromMySQLBefore(Long sessionId, long beforeTime, int limit) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId)
                .lt("created_time", new Date(beforeTime))
                .orderByDesc("created_time")
                .last("LIMIT " + limit);

        List<Message> messages = messageMapper.selectList(queryWrapper);
        return convertToResponses(messages);
    }

    /**
     * Reads the offline backlog that falls into the cold-storage window from MySQL
     */
    private List<MessageResponse> getMessagesFromMySQLAfter(Long sessionId, long afterTime, long beforeTime) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId)
                .gt("created_time", new Date(afterTime))
                .lt("created_time", new Date(beforeTime))
                .orderByAsc("created_time");

        List<Message> messages = messageMapper.selectList(queryWrapper);
        return convertToResponses(messages);
    }

    @Override
    public String getSummary(SessionSummaryRequest sessionSummaryRequest) {
        return   historyChatLog(sessionSummaryRequest.getSessionId(), sessionSummaryRequest.getHours());

    }


    @Resource
    private AiServiceClient aiServiceClient;

    public String historyChatLog(Long sessionId, Integer hours) {
        Map<Long, String> userNickName = userServiceClient.getUserNickName(sessionId);

        // Current time in the Asia/Shanghai zone
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        LocalDateTime nowShanghai = LocalDateTime.now(shanghai);
        LocalDateTime threshold = nowShanghai.minusHours(hours);
        // Format as a yyyy-MM-dd HH:mm:ss string
        String timeThresholdStr = threshold.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId).ge("created_time", timeThresholdStr);

        List<Message> messages = this.list(queryWrapper);
        StringBuilder chatLog = new StringBuilder();

        for (Message message : messages) {
            Long senderId = message.getSenderId();
            String senderName = userNickName.get(senderId);
            String timeStr = FormatDateUtil.formatDate(message.getCreatedTime());
            chatLog.append("[").append(senderName).append("] ").append(timeStr).append(": ").append(message.getContent()).append("\n");
        }
        String result = "No messages";

        try {
            result = aiServiceClient.chatSummary(chatLog.toString().trim());
        } catch (Exception e) {
            throw new RuntimeException("The session summary call failed");
        }

        return result;
    }

}