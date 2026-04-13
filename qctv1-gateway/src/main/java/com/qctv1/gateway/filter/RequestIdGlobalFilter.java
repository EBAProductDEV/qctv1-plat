package com.qctv1.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 为每个请求补齐并透传 X-Request-Id。
 * 这样前端、网关和下游服务就能用同一个请求号串起整条链路。
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 如果调用方已经带了请求 ID，就直接复用；
        // 否则由网关生成一个新的请求 ID，避免链路日志无法关联。
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        // 把请求 ID 写回下游请求头，确保下游服务也能拿到同一个值。
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        // 同时把请求 ID 放进响应头，前端排障时可以直接复制这个值去查日志。
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getAttributes().put(REQUEST_ID_HEADER, requestId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
