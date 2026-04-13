package com.qctv1.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * 记录网关访问日志。
 * 这里重点关注请求方法、访问路径、响应状态和整条链路耗时。
 */
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 在请求进入下游链路前先记下开始时间，便于在响应返回时计算总耗时。
        Instant start = Instant.now();
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // 请求结束后统一落日志，这时可以拿到最终状态码和完整耗时。
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    String requestId = exchange.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER);
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 0
                            : exchange.getResponse().getStatusCode().value();
                    log.info(
                            "gateway requestId={} method={} path={} status={} elapsedMs={}",
                            requestId,
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getRawPath(),
                            status,
                            elapsed
                    );
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
