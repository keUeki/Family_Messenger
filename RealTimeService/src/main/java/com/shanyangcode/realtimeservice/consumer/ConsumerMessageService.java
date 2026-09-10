package com.shanyangcode.realtimeservice.consumer;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.model.dto.ChatRequest;
import com.shanyangcode.common.model.dto.MessageBody;
import com.shanyangcode.common.model.dto.MessageRequest;
import com.shanyangcode.common.model.vo.MessageResponse;
import com.shanyangcode.common.utils.FormatDateUtil;
import com.shanyangcode.realtimeservice.client.AiServiceClient;
import com.shanyangcode.realtimeservice.client.UserServiceClient;
import com.shanyangcode.realtimeservice.utils.SnowflakeDynamicUtil;
import com.shanyangcode.realtimeservice.websocket.ChannelManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class ConsumerMessageService {

    @Resource
    private UserServiceClient userServiceClient;

    @Resource
    private AiServiceClient aiServiceClient;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = CommonConstant.KAFKA_MESSAGE_TOPIC_PUSH, groupId = "infinite-chat-push-group-0")
    public void consume(String message) {
        System.out.println("Message received: " + message);
        MessageRequest messageRequest = JSONUtil.toBean(message, MessageRequest.class);
        System.out.println("Message received: " + messageRequest);
        if (messageRequest.getSessionType() == null) {
            log.error("Message has no sessionType, cannot be routed, dropping it: {}", message);
            return;
        }
        if (messageRequest.getSessionType() == SessionTypeConstant.SIGNAL_TYPE) {
            signalMessage(messageRequest);
        } else if (messageRequest.getSessionType() == SessionTypeConstant.GROUP_TYPE) {
            groupMessage(messageRequest);
        } else if (messageRequest.getSessionType() == SessionTypeConstant.ROBOT_TYPE) {
            aiSignalMessage(messageRequest);
        }
    }

    public void aiSignalMessage(MessageRequest messageRequest) {
        MessageResponse messageResponse = createMessageResponse(messageRequest);
        messageResponse.setMessageId(SnowflakeDynamicUtil.nextId());
        // Fetch the AI reply
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setPrompt(messageRequest.getBody().getContent());
        chatRequest.setSessionId(messageRequest.getSessionId());
        chatRequest.setUserId(messageRequest.getSenderId());
        String chat = aiServiceClient.chat(chatRequest);


        MessageBody messageBody = messageResponse.getBody();
        messageBody.setContent(chat);
        messageResponse.setBody(messageBody);
        messageResponse.setSenderId(CommonConstant.AI_ID);

        pushMessageToUser(messageResponse, chatRequest.getUserId());
        BeanUtil.copyProperties(messageResponse,messageRequest);

        kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_STORE, JSONUtil.toJsonStr(messageRequest)).whenComplete((success, failure) -> {
            if (failure != null) {
                // Producer failed to publish
                System.err.println("Producer failed to publish: " + failure.getMessage());
                // Log, alert, compensate, and so on
            } else {
                // Producer published successfully
                System.out.println("Producer published successfully, offset: " + success.getRecordMetadata().offset());
            }
        });
    }

    public void signalMessage(MessageRequest messageRequest) {
        MessageResponse messageResponse = createMessageResponse(messageRequest);
        pushMessageToUser(messageResponse, messageRequest.getSenderId());
        pushMessageToUser(messageResponse, messageRequest.getReceiverId());

    }

    public void groupMessage(MessageRequest messageRequest) {
        List<Long> receiveUserIds = userServiceClient.getUserIdBySessionId(messageRequest.getSessionId());
        MessageResponse messageResponse = createMessageResponse(messageRequest);
        for (Long receiveUserId : receiveUserIds) {
            pushMessageToUser(messageResponse, receiveUserId);
        }
    }

    public MessageResponse createMessageResponse(MessageRequest messageRequest) {
        MessageResponse messageResponse = new MessageResponse();
        BeanUtil.copyProperties(messageRequest, messageResponse);
        messageResponse.setCreatedTime(FormatDateUtil.formatDate(messageRequest.getCreatedTime()));
        return messageResponse;

    }

    public void pushMessageToUser(MessageResponse messageResponse, Long receiverId) {
        if (receiverId == null) {
            log.warn("Receiver is empty, skipping push: {}", messageResponse);
            return;
        }
        Channel channel = ChannelManager.getChannelByUserId(receiverId.toString());
        if (channel != null && channel.isActive()) {
            TextWebSocketFrame frame = new TextWebSocketFrame(JSONUtil.toJsonStr(messageResponse));
            channel.writeAndFlush(frame).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("Message delivered: {}", messageResponse);
                } else {
                    log.info("Message delivery failed: {}", future.cause() != null ? future.cause().getMessage() : "unknown error");
                }
            });
        } else {
            log.info("Channel is missing or already closed, receiver: {}", receiverId);
        }
    }

}