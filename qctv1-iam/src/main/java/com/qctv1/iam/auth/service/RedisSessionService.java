package com.qctv1.iam.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 会话服务。
 * JWT 负责“证明 token 是谁发的”，Redis 负责“证明这次登录现在仍然有效”。
 */
@Service
public class RedisSessionService {

    private static final String ACCESS_KEY_PREFIX = "qctv1:iam:access:";
    private static final String REFRESH_KEY_PREFIX = "qctv1:iam:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RedisSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveSession(Long userId, String sessionId, Duration accessTtl, Duration refreshTtl) {
        // access 与 refresh 分开存储，便于分别控制生命周期。
        redisTemplate.opsForValue().set(accessKey(userId), sessionId, accessTtl);
        redisTemplate.opsForValue().set(refreshKey(userId), sessionId, refreshTtl);
    }

    public String getAccessSessionId(Long userId) {
        return redisTemplate.opsForValue().get(accessKey(userId));
    }

    public String getRefreshSessionId(Long userId) {
        return redisTemplate.opsForValue().get(refreshKey(userId));
    }

    public void clearSession(Long userId) {
        // 登出或账号被禁用时直接删除会话键，旧 token 就会在网关/IAM 侧失效。
        redisTemplate.delete(accessKey(userId));
        redisTemplate.delete(refreshKey(userId));
    }

    private String accessKey(Long userId) {
        return ACCESS_KEY_PREFIX + userId;
    }

    private String refreshKey(Long userId) {
        return REFRESH_KEY_PREFIX + userId;
    }
}
