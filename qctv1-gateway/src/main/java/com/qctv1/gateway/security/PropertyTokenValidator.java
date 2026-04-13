package com.qctv1.gateway.security;

import com.qctv1.gateway.config.Qctv1GatewayProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 基于配置文件的占位 token 校验实现。
 * 当前只用于把鉴权链路跑通，生产环境应替换成真正的认证中心或 JWT 校验逻辑。
 */
@Component
public class PropertyTokenValidator implements TokenValidator {

    private final Qctv1GatewayProperties properties;

    public PropertyTokenValidator(Qctv1GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Boolean> validate(String token, ServerWebExchange exchange) {
        // 这里只是开发阶段的占位实现，不能长期依赖配置白名单做认证。
        return Mono.just(properties.getAuth().getMockValidTokens().contains(token));
    }
}
