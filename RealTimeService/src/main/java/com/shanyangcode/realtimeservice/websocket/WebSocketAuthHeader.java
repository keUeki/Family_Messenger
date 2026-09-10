package com.shanyangcode.realtimeservice.websocket;


import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;


@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthHeader extends ChannelInboundHandlerAdapter {

    /**
     * The browser WebSocket API cannot set custom headers, so the token is also accepted as a query parameter.
     */
    private static final String TOKEN_QUERY_PARAM = "accessToken";

    private static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)  {
        if (msg instanceof FullHttpRequest request){
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
            String token = resolveToken(request, queryStringDecoder);
            if (StringUtils.isEmpty(token)) {
                log.warn("WebSocket handshake rejected: token missing, uri={}, remote={}",
                        queryStringDecoder.path(), ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            try {
                Claims claims = JwtUtil.parse(token);
                if (claims == null) {
                    log.warn("WebSocket handshake rejected: token is invalid or expired, remote={}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }

                String userId = claims.getSubject();
                if (userId == null || userId.isEmpty()) {
                    log.warn("WebSocket handshake rejected: token carries no user identifier, remote={}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }

                String storedToken = stringRedisTemplate.opsForValue().get(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
                if (StringUtils.isEmpty(storedToken) || !token.equals(storedToken)) {
                    log.warn("WebSocket handshake rejected: token does not match the one stored in Redis, userId={}, present in redis={}",
                            userId, !StringUtils.isEmpty(storedToken));
                    ctx.close();
                    return;
                }

                // Strip the query string so WebSocketServerProtocolHandler can match the handshake path
                request.setUri(queryStringDecoder.path());

                // 3. Bind the user to the channel
                ChannelManager.addUserChannel(userId, ctx.channel());
                ChannelManager.addChannelUser(userId, ctx.channel());
                log.info("WebSocket handshake authenticated, userId={}, remote={}", userId, ctx.channel().remoteAddress());
                ctx.fireChannelRead(msg);
            } catch (Exception e) {
                log.error("Unexpected error during WebSocket handshake authentication, remote={}", ctx.channel().remoteAddress(), e);
                ctx.close();
            }

        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Tries the Authorization header first, then the accessToken query parameter.
     */
    private String resolveToken(FullHttpRequest request, QueryStringDecoder queryStringDecoder) {
        String authHeader = request.headers().get("Authorization");
        if (StringUtils.isNotEmpty(authHeader)) {
            return authHeader.startsWith(BEARER_PREFIX)
                    ? authHeader.substring(BEARER_PREFIX.length()).trim()
                    : authHeader.trim();
        }

        Map<String, List<String>> parameters = queryStringDecoder.parameters();
        List<String> values = parameters.get(TOKEN_QUERY_PARAM);
        if (values != null && !values.isEmpty()) {
            return values.get(0).trim();
        }
        return null;
    }
}