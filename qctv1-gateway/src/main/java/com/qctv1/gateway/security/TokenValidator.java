package com.qctv1.gateway.security;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * token 校验抽象接口。
 * 这里保留响应式返回类型，是为了兼容未来对接远程鉴权、JWT 公钥校验等异步场景。
 */
public interface TokenValidator {

    Mono<TokenPrincipal> validate(String token, ServerWebExchange exchange);
}
