package com.qctv1.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.gateway.filter.RequestIdGlobalFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 兜底异常处理器。
 * 作用是在网关边界把未捕获异常统一转换成 JSON，避免前端拿到不一致的错误结构。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayJsonExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayJsonExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayJsonExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 如果上游已经把异常转换成带状态码的异常，这里优先沿用那个状态码。
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }

        // 异常响应结构尽量与鉴权失败保持一致，前端只需要按一种格式解析网关错误。
        String requestId = response.getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("requestId", requestId);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException jsonProcessingException) {
            // 就算异常处理本身再出错，也尽量返回一个合法 JSON。
            payload = "{\"status\":500,\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
        }

        log.error("Gateway request failed, path={}, requestId={}", exchange.getRequest().getPath(), requestId, ex);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }
}
