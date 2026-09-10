package com.shanyangcode.userservice.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis configuration
 *
 * Responsibilities:
 * - Configures the Redis template
 * - Configures the Lua script beans
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate Bean
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Lua script that scans for expired friend requests
     *
     * Atomically pops the ids of expired friend requests from the Redis ZSET and removes them.
     *
     * Parameters:
     * - KEYS[1]: the ZSET key (friend-request-expire-zset)
     * - ARGV[1]: the current timestamp in milliseconds; when <= 0 the Redis TIME command is used instead
     * - ARGV[2]: the maximum number of entries to fetch
     *
     * Returns: the list of expired friend request ids
     */
    @Bean
    public DefaultRedisScript<List> scanExpiredFriendRequestsScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();

        String luaScript =
                "local zsetKey = KEYS[1] " +
                        "local nowArg  = ARGV[1] " +
                        "local maxCount = tonumber(ARGV[2]) " +
                        "if not maxCount or maxCount <= 0 then maxCount = 500 end " +
                        "local nowMs " +
                        "if (not nowArg) or nowArg == '' or tonumber(nowArg) <= 0 then " +
                        "  local t = redis.call('TIME'); " +
                        "  nowMs = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000) " +
                        "else nowMs = tonumber(nowArg) end " +
                        "local expired = redis.call('ZRANGEBYSCORE', zsetKey, '-inf', nowMs, 'LIMIT', 0, maxCount) " +
                        "if #expired == 0 then return {} end " +
                        "for i=1,#expired do redis.call('ZREM', zsetKey, expired[i]) end " +
                        "return expired;";

        script.setScriptText(luaScript);
        script.setResultType(List.class);
        return script;
    }
}