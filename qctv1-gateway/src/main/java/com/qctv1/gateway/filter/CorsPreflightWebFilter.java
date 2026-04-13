package com.qctv1.gateway.filter;

import com.qctv1.gateway.config.Qctv1GatewayProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 专门处理浏览器跨域预检请求。
 * 预检请求往往发生在路由匹配之前，因此这里放在 WebFilter 层而不是具体路由过滤器里。
 */
@Component
public class CorsPreflightWebFilter implements WebFilter, Ordered {

    private final Qctv1GatewayProperties properties;

    public CorsPreflightWebFilter(Qctv1GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String origin = request.getHeaders().getOrigin();
        if (origin == null) {
            return chain.filter(exchange);
        }

        // 浏览器的 CORS 预检请求往往先于正常路由处理到达，
        // 所以这里在 WebFilter 中统一提前处理。
        String matchedOrigin = matchOrigin(origin);
        if (matchedOrigin == null) {
            if (CorsUtils.isPreFlightRequest(request)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, matchedOrigin);
        response.getHeaders().set(HttpHeaders.VARY, "Origin,Access-Control-Request-Method,Access-Control-Request-Headers");
        if (properties.getCors().isAllowCredentials()) {
            response.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, properties.getCors().getExposedHeaders());

        if (CorsUtils.isPreFlightRequest(request)) {
            // 预检请求在这里直接结束，不再继续进入正常网关路由链。
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, properties.getCors().getAllowedMethods());
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, resolveAllowedHeaders(request));
            response.setStatusCode(HttpStatus.OK);
            return response.setComplete();
        }

        return chain.filter(exchange);
    }

    private String matchOrigin(String origin) {
        // 同时支持精确来源和“主机固定、端口任意”的本地开发模式。
        for (String pattern : properties.getCors().getAllowedOriginPatterns()) {
            if (pattern.endsWith(":*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (origin.startsWith(prefix)) {
                    return origin;
                }
            } else if (pattern.equals(origin)) {
                return origin;
            }
        }
        return null;
    }

    private List<String> resolveAllowedHeaders(ServerHttpRequest request) {
        String requestedHeaders = request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        // 如果配置是 *，就允许浏览器声明的请求头；否则返回显式配置的请求头列表。
        if (requestedHeaders == null || properties.getCors().getAllowedHeaders().contains("*")) {
            return List.of("*");
        }
        return properties.getCors().getAllowedHeaders();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
