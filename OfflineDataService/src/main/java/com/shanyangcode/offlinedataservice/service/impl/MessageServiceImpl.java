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
import com.shanyangcode.offlinedataservice.client.UserServiceClient;
import com.shanyangcode.offlinedataservice.mapper.MessageMapper;
import com.shanyangcode.offlinedataservice.model.dto.HistoryMessageRequest;
import com.shanyangcode.offlinedataservice.model.dto.OfflineMessageRequest;
import com.shanyangcode.offlinedataservice.model.entity.Message;
import com.shanyangcode.offlinedataservice.service.MessageService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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


    // ==================== 离线消息查询 ====================

    @Override
    public Map<Long, List<MessageResponse>> getOfflineMessages(OfflineMessageRequest request) {
        Long userId = request.getUserId();
        Long offlineTime = request.getOfflineTime();

        if (userId == null || offlineTime == null) {
            return Collections.emptyMap();
        }

        // 1. 获取用户的所有会话
        List<Long> sessionIds = userServiceClient.getSessionIdsByUserId(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 遍历每个会话，获取离线后的消息
        Map<Long, List<MessageResponse>> result = new HashMap<>();
        long hotBoundary = System.currentTimeMillis() - CommonConstant.SEVEN_DAYS_MILLIS;

        for (Long sessionId : sessionIds) {
            List<MessageResponse> messages = getMessagesAfter(sessionId, offlineTime, hotBoundary);
            if (!messages.isEmpty()) {
                result.put(sessionId, messages);
            }
        }

        log.info("用户 {} 离线消息查询完成，共 {} 个会话有新消息", userId, result.size());
        return result;
    }

    /**
     * 获取指定时间之后的消息（离线消息）
     */
    private List<MessageResponse> getMessagesAfter(Long sessionId, long afterTime, long hotBoundary) {
        List<MessageResponse> result = new ArrayList<>();

        // 1. 查 Redis（热数据）
        if (afterTime >= hotBoundary) {
            // 离线时间在热数据范围内，直接查 Redis
            List<MessageResponse> redisMessages = getMessagesFromRedisAfter(sessionId, afterTime);
            result.addAll(redisMessages);
        } else {
            // 离线时间在冷数据范围，需要同时查 Redis 和 MySQL
            // 先查 Redis 全部热数据
            List<MessageResponse> redisMessages = getMessagesFromRedisAfter(sessionId, hotBoundary);
            result.addAll(redisMessages);

            // 再查 MySQL 冷数据
            List<MessageResponse> mysqlMessages = getMessagesFromMySQLAfter(sessionId, afterTime, hotBoundary);
            result.addAll(mysqlMessages);
        }

        // 按时间正序（旧消息在前）
        result.sort(Comparator.comparing(MessageResponse::getCreatedTime));
        return result;
    }

    /**
     * 从 Redis 获取指定时间之后的消息
     */
    private List<MessageResponse> getMessagesFromRedisAfter(Long sessionId, long afterTime) {
        String key = CommonConstant.SESSION_KEY_REDIS + sessionId;
        // (afterTime, +inf] 开区间，不包含 afterTime 这一刻的消息
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


    // ==================== 数据转换 ====================

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
            // MySQL 的 Date 转时间戳字符串，保持和 Redis 数据格式一致
            response.setCreatedTime(String.valueOf(msg.getCreatedTime().getTime()));

            MessageBody body = new MessageBody();
            body.setContent(msg.getContent());
            body.setReplyId(msg.getReplyId());
            response.setBody(body);

            responses.add(response);
        }
        return responses;
    }

    /**
     * 从 MySQL 获取冷数据区间的离线消息
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
}