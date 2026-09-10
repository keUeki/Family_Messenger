package com.shanyangcode.userservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock distributed scheduler-lock configuration
 * <p>
 * Responsibilities:
 * - Activates @SchedulerLock so a scheduled task runs on only one instance in a cluster
 * - Uses Redis as the lock store, reusing the existing Redis connection
 * <p>
 * Note: @EnableScheduling is already declared on the UserServiceApplication main class.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ShedLockConfig {

    /** Prefix for the Redis lock keys, kept per service. */
    private static final String LOCK_KEY_PREFIX = "UserService";

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, LOCK_KEY_PREFIX);
    }
}