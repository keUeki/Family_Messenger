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
     * 浏览器的 WebSocket API 无法自定义请求头，因此同时支持从查询参数中读取 token
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
                log.warn("WebSocket 握手被拒绝：缺少 token，uri={}, remote={}",
                        queryStringDecoder.path(), ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            try {
                Claims claims = JwtUtil.parse(token);
                if (claims == null) {
                    log.warn("WebSocket 握手被拒绝：token 无效或已过期，remote={}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }

                String userId = claims.getSubject();
                if (userId == null || userId.isEmpty()) {
                    log.warn("WebSocket 握手被拒绝：token 中不包含用户标识，remote={}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }

                String storedToken = stringRedisTemplate.opsForValue().get(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
                if (StringUtils.isEmpty(storedToken) || !token.equals(storedToken)) {
                    log.warn("WebSocket 握手被拒绝：token 与 Redis 中保存的不一致，userId={}, redis中存在={}",
                            userId, !StringUtils.isEmpty(storedToken));
                    ctx.close();
                    return;
                }

                // 去掉查询参数，保证 WebSocketServerProtocolHandler 能够匹配到握手路径
                request.setUri(queryStringDecoder.path());

                // 3. 绑定用户与 channel
                ChannelManager.addUserChannel(userId, ctx.channel());
                ChannelManager.addChannelUser(userId, ctx.channel());
                log.info("WebSocket 握手鉴权通过，userId={}, remote={}", userId, ctx.channel().remoteAddress());
                ctx.fireChannelRead(msg);
            } catch (Exception e) {
                log.error("WebSocket 握手鉴权发生异常，remote={}", ctx.channel().remoteAddress(), e);
                ctx.close();
            }

        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * 依次尝试从 Authorization 请求头和 accessToken 查询参数中解析 token
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