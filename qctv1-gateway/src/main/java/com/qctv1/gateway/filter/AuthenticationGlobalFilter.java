package com.qctv1.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.gateway.config.Qctv1GatewayProperties;
import com.qctv1.gateway.security.TokenValidator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权全局过滤器。
 * 当前版本只搭骨架：支持开关、白名单和统一错误返回，真正的 token 校验逻辑留给 TokenValidator 扩展。
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private final Qctv1GatewayProperties properties;

    private final TokenValidator tokenValidator;

    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthenticationGlobalFilter(
            Qctv1GatewayProperties properties,
            TokenValidator tokenValidator,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 整个鉴权骨架都可以通过配置关闭。
        // 这样在真实身份体系还没接入前，网关也能先承担路由转发职责。
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        // 白名单优先于 token 解析，这样流式接口和健康检查在开启鉴权后仍可按需放行。
        if (properties.getAuth().getWhitelist().stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authorization)) {
            return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }

        String token = normalizeToken(authorization);
        if (!StringUtils.hasText(token)) {
            return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, "Authorization header is empty");
        }

        // token 校验通过接口抽象出去，当前先用属性配置做占位，
        // 后面可以无缝替换成 JWT、OAuth2 或远程鉴权实现。
        return tokenValidator.validate(token, exchange)
                .flatMap(valid -> valid
                        ? chain.filter(exchange)
                        : writeJsonError(exchange, HttpStatus.FORBIDDEN, "Token validation failed"));
    }

    private String normalizeToken(String authorization) {
        // 同时兼容原始 token 和标准 Bearer 写法，减少前后端联调阻力。
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 在网关边界统一错误响应结构，前端不需要区分具体是哪个鉴权分支失败。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        body.put("requestId", response.getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER));

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            // 即使序列化异常，也退回到最小可用 JSON，避免前端拿到无法解析的响应体。
            payload = "{\"status\":500,\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
