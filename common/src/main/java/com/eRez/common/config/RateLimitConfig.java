package com.eRez.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RateLimitConfig {

    private static final String SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "    redis.call('EXPIRE', KEYS[1], 60) " +
            "end " +
            "return count";

    @Bean
    public RedisScript<Long> rateLimitScript() {
        return new DefaultRedisScript<>(SCRIPT, Long.class);
    }
}
