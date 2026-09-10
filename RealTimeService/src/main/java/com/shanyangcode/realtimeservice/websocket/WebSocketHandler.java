package com.shanyangcode.realtimeservice.websocket;

import cn.hutool.json.JSONUtil;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.model.dto.MessageRequest;
import com.shanyangcode.realtimeservice.constants.WebSocketConstant;
import com.shanyangcode.realtimeservice.utils.SnowflakeDynamicUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Date;


@Slf4j
@AllArgsConstructor
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) {
        Channel channel = channelHandlerContext.channel();
        String msg = textWebSocketFrame.text();

        log.debug("Received from {}: {}", channel.id(), msg); // info -> debug, to keep the log from flooding

        try {
            if (WebSocketConstant.HEARTBEAT_PING.equals(msg)) {
                // Heartbeat response
                if (channel.isActive()) {
                    log.debug("Received heartbeat ping from {}", channel.id());
                    channel.writeAndFlush(new TextWebSocketFrame(WebSocketConstant.HEARTBEAT_PONG));
                }
            } else {
                // Business message
                if (channel.isActive()) {
                    sendMessageKafka(msg, channel);
                } else {
                    log.warn("Channel {} inactive, skip message: {}", channel.id(), msg);
                }
            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", channel.id(), msg, e);
            // Todo
//            if (channel.isActive()) {
//                sendSystemErrorToClient(channel, msg, e);
//            }
            // Clean up the connection even when the send fails, to avoid zombie connections
            clearChannel(channel);
            channelHandlerContext.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception caught in channel pipeline", cause);
        clearChannel(ctx.channel());
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        System.out.println("channel active");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // clearChannel(ctx.channel());
        super.channelInactive(ctx);
        System.out.println("channel inActive");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        System.out.println("handler added");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            String userId = ChannelManager.getUserIdByChannel(ctx.channel());
            if (userId != null) {
                log.info("handlerRemoved: cleaning up user {}", userId);
                clearChannel(ctx.channel());
            }
        } finally {
            super.handlerRemoved(ctx);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        // Handle the heartbeat
        if (evt instanceof IdleStateEvent event) {
            switch (event.state()) {
                case READER_IDLE:
                    log.error("Read idle timeout");
                    clearChannel(ctx.channel());
                    ctx.close();
                    break;
                case WRITER_IDLE:
                    log.error("Write idle timeout");
                case ALL_IDLE:
                    log.error("Read/write idle timeout");
            }
        }
    }


    public void clearChannel(Channel channel) {
        if (channel == null) {
            return; // idempotency guard
        }
        System.out.println("clearChannel: " + channel.id());

        String userId = ChannelManager.getUserIdByChannel(channel);
        try {


            if (userId != null) {
                ChannelManager.removeUserChannel(userId);
                saveOfflineTime(userId);
            }
            ChannelManager.removeChannelUser(channel);
        } catch (Exception e) {
            log.error("clearChannel failed for channel: {}, userId: {}", channel.id(), userId, e);
        } finally {
            if (channel.isActive()) {
                channel.close();
            }
        }
    }




    /**
     * Records the user's offline timestamp in Redis
     * <p>
     * Key: user:{userId}:offline
     * Value: the timestamp
     */
    private void saveOfflineTime(String userId) {
        String key = CommonConstant.OFFLINE_KEY_REDIS + userId;
        String timestamp = String.valueOf(System.currentTimeMillis());
        stringRedisTemplate.opsForValue().set(key, timestamp);
        log.debug("Recorded the user's offline time: userId={}, timestamp={}", userId, timestamp);
    }

    public void sendMessageKafka(String message, Channel channel) {
        // Convert to the message body
        MessageRequest messageRequest = JSONUtil.toBean(message, MessageRequest.class);
        messageRequest.setMessageId(SnowflakeDynamicUtil.nextId());
        messageRequest.setCreatedTime(new Date());


        // todo verify authorisation


        // Persist the message exactly once to avoid duplicate consumption
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

        // Push the message downstream
        kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_PUSH, messageRequest.getSessionId().toString(), JSONUtil.toJsonStr(messageRequest)).whenComplete((success, failure) -> {
            if (failure != null) {
                // Producer failed to publish
                System.err.println("Producer failed to publish: " + failure.getMessage());
                // Log, alert, compensate, and so on
            } else {
                // Producer published successfully
                System.out.println("Producer published the push message successfully, offset: " + success.getRecordMetadata().offset());
            }
        });
    }

}