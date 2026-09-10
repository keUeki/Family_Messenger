package com.shanyangcode.gateway.filter;



import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.utils.JwtUtil;

import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Allowlisted paths: endpoints that do not require authentication
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/api/user/login/code",
            "/api/user/register",
            "/api/user/sendCaptcha",
            "/api/user/login/password",
            "/api/user/refresh"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // CORS preflight requests carry no auth header; let the CORS filter handle them
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // Allowlisted paths pass straight through
        if (EXCLUDE_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // Read the Access-Token and Refresh-Token
        String accessToken = request.getHeaders().getFirst("Access-Token");
        String refreshToken = request.getHeaders().getFirst("Refresh-Token");

        try {
            // 1. Try to authenticate with the Access-Token
            if (accessToken != null && !accessToken.isEmpty()) {
                Claims acClaims = JwtUtil.parse(accessToken);
                if (acClaims != null) {
                    String userId = acClaims.getSubject();
                    String redisAccessToken = stringRedisTemplate.opsForValue()
                            .get(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
                    if (accessToken.equals(redisAccessToken)) {
                        // Token is valid; let the request through
                        return chain.filter(exchange);
                    }
                }
            }

            // 2. If the Access-Token is invalid, use the Refresh-Token to decide whether a refresh is needed
            if (refreshToken != null && !refreshToken.isEmpty()) {
                Claims rfClaims = JwtUtil.parse(refreshToken);
                if (rfClaims != null) {
                    String userId = rfClaims.getSubject();
                    String redisRefreshToken = stringRedisTemplate.opsForValue()
                            .get(CommonConstant.REFRESH_TOKEN_PREFIX + userId);
                    if (refreshToken.equals(redisRefreshToken)) {
                        // Refresh-Token is valid but the Access-Token has expired -> ask the client to refresh
                        return buildErrorResponse(exchange, ErrorCode.TOKEN_EXPIRED);
                    }
                }
            }

            // 3. Neither is valid -> not logged in
            return buildErrorResponse(exchange, ErrorCode.NOT_LOGIN_ERROR);

        } catch (Exception e) {
            log.error("Unexpected error while validating the JWT", e);
            return buildErrorResponse(exchange, ErrorCode.SYSTEM_ERROR, "Could not confirm the login state");
        }
    }

    /**
     * Builds a generic error response
     */
    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, ErrorCode errorCode) {
        return buildErrorResponse(exchange, errorCode, errorCode.getMessage());
    }

    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        String jsonResponse = String.format("{\"code\":%d,\"message\":\"%s\"}", errorCode.getCode(), message);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // or map the errorCode onto an HttpStatus
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // fairly high precedence
    }
}