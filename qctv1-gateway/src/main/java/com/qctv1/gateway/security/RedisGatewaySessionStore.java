package com.qctv1.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 网关侧 Redis 会话存储实现。
 * 这里只读取 access sessionId，用于校验当前 accessToken 是否仍然属于有效会话。
 */
@Component
public class RedisGatewaySessionStore implements GatewaySessionStore {

    private static final String ACCESS_KEY_PREFIX = "qctv1:iam:access:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisGatewaySessionStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<String> getAccessSessionId(Long userId) {
        // 键格式要与 IAM 写入 Redis 时保持一致，否则网关会误判 token 失效。
        return redisTemplate.opsForValue().get(ACCESS_KEY_PREFIX + userId);
    }
}
