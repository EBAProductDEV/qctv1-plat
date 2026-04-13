package com.qctv1.gateway.security;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关默认 token 校验器。
 * 校验流程分成两步：
 * 1. 用 JWT 校验 token 的签名、issuer 与 claims。
 * 2. 用 Redis 校验该 token 所属 session 是否还是当前有效会话。
 */
@Component
public class JwtRedisTokenValidator implements TokenValidator {

    private final GatewayJwtTokenService gatewayJwtTokenService;

    private final GatewaySessionStore gatewaySessionStore;

    public JwtRedisTokenValidator(
            GatewayJwtTokenService gatewayJwtTokenService,
            GatewaySessionStore gatewaySessionStore
    ) {
        this.gatewayJwtTokenService = gatewayJwtTokenService;
        this.gatewaySessionStore = gatewaySessionStore;
    }

    @Override
    public Mono<TokenPrincipal> validate(String token, ServerWebExchange exchange) {
        TokenPrincipal principal = gatewayJwtTokenService.parseAccessToken(token);
        if (principal == null) {
            return Mono.empty();
        }
        // Redis 中保存的是当前账号最近一次 access sessionId；
        // 只有二者一致，才说明这个 token 仍然属于当前有效登录态。
        return gatewaySessionStore.getAccessSessionId(principal.userId())
                .filter(principal.sessionId()::equals)
                .map(sessionId -> principal);
    }
}
