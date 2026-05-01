package com.qctv1.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.gateway.config.Qctv1GatewayProperties;
import com.qctv1.gateway.security.GatewayHeaders;
import com.qctv1.gateway.security.TokenPrincipal;
import com.qctv1.gateway.security.TokenValidator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
 * 网关统一认证过滤器。
 * 负责三件事：
 * 1. 放行白名单接口。
 * 2. 校验 Authorization 中的 JWT 是否有效。
 * 3. 把当前登录用户信息透传给下游服务。
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
        // 如果关掉鉴权开关，网关只做普通转发。
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        // 白名单接口不需要携带 token，例如登录、注册、刷新 token。
        if (properties.getAuth().getWhitelist().stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange.getRequest());
        if (!StringUtils.hasText(token)) {
            return writeJsonError(exchange, HttpStatus.UNAUTHORIZED, "Missing authorization token");
        }

        // validate 通过后把用户标识写入请求头，后端服务不再自行解析 JWT。
        return tokenValidator.validate(token, exchange)
                .flatMap(principal -> chain.filter(mutateExchange(exchange, principal)))
                .switchIfEmpty(writeJsonError(exchange, HttpStatus.FORBIDDEN, "Token validation failed"));
    }

    private ServerWebExchange mutateExchange(ServerWebExchange exchange, TokenPrincipal principal) {
        // 透传的请求头是 IAM 与其他下游服务共享的“当前用户上下文”。
        return exchange.mutate()
                .request(builder -> builder
                        .header(GatewayHeaders.USER_ID, String.valueOf(principal.userId()))
                        .header(GatewayHeaders.USER_NAME, principal.userName())
                        .header(GatewayHeaders.USER_ROLE, principal.roleCode()))
                .build();
    }

    private String normalizeToken(String authorization) {
        // 兼容标准 Bearer token 和直接传裸 token 两种格式。
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authorization)) {
            return normalizeToken(authorization);
        }
        // 浏览器原生 WebSocket 无法自定义 Authorization 请求头。
        // 任务中心这类 WebSocket 握手会把 token 放在 query 参数里，由网关统一校验后再转发。
        if (isWebSocketRequest(request)) {
            return request.getQueryParams().getFirst("token");
        }
        return null;
    }

    private boolean isWebSocketRequest(ServerHttpRequest request) {
        String upgrade = request.getHeaders().getUpgrade();
        String connection = request.getHeaders().getFirst("Connection");
        return "websocket".equalsIgnoreCase(upgrade)
                || (connection != null && connection.toLowerCase().contains("upgrade"));
    }

    private Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String message) {
        // 鉴权失败统一返回 JSON，前端可以稳定地按 message 展示错误原因。
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

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
            payload = "{\"status\":500,\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
