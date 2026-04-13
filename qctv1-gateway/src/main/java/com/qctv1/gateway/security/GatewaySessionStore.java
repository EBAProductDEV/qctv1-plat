package com.qctv1.gateway.security;

import reactor.core.publisher.Mono;

/**
 * 网关读取会话状态的抽象接口。
 * 单独抽接口的好处是：测试里可以直接 mock，后续也能切换成别的会话存储实现。
 */
public interface GatewaySessionStore {

    Mono<String> getAccessSessionId(Long userId);
}
