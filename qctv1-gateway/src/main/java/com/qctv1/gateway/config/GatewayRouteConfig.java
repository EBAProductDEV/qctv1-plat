package com.qctv1.gateway.config;

import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * 统一定义网关对外公开的 AI 路由。
 * 当前采用 Java DSL 显式声明，而不是开启 discovery locator 自动生成路由，
 * 这样可以精确控制路径映射、SSE 特殊处理以及普通接口的治理策略。
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator qctv1RouteLocator(RouteLocatorBuilder builder, Qctv1GatewayProperties properties) {
        return builder.routes()
                // 流式聊天路由必须放在普通聊天路由前面。
                // 因为 /api/ai/chat/stream 同时也满足 /api/ai/chat/**，
                // 如果顺序放反，SSE 请求会误走普通接口的过滤链。
                .route("ai-chat-stream", route -> route
                        .path("/api/ai/chat/stream")
                        .filters(filter -> sseFilters(filter)
                                .rewritePath("/api/ai/chat/(?<segment>.*)", "/chat/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                // 对外统一暴露 /api/ai/chat/**，进入网关后再翻译成 chat 服务现有的 /chat/**。
                .route("ai-chat-api", route -> route
                        .path("/api/ai/chat/**")
                        .filters(filter -> standardFilters(filter, properties, "chat-api")
                                .rewritePath("/api/ai/chat/?(?<segment>.*)", "/chat/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                // 图片能力目前仍在 chat 服务内，对外保持统一命名空间更方便前端记忆和调用。
                .route("ai-image-api", route -> route
                        .path("/api/ai/image/**")
                        .filters(filter -> standardFilters(filter, properties, "image-api")
                                .rewritePath("/api/ai/image/?(?<segment>.*)", "/image/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                // hello/demo 接口同样挂在统一 AI 前缀下，对下游仍保持 /hello/** 的原始控制器路径。
                .route("ai-hello-api", route -> route
                        .path("/api/ai/hello/**")
                        .filters(filter -> standardFilters(filter, properties, "hello-api")
                                .rewritePath("/api/ai/hello/?(?<segment>.*)", "/hello/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                // agent 聊天是流式接口，因此单独拆路由，只走 SSE 安全过滤链，
                // 避免被普通接口的重试和熔断策略干扰。
                .route("ai-agent-stream", route -> route
                        .path("/api/ai/agent/chat")
                        .filters(filter -> sseFilters(filter)
                                .rewritePath("/api/ai/agent/(?<segment>.*)", "/ai/agent/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getRagUri().toString()))
                // 对外 /api/ai/agent/** 统一映射到 rag 服务现有的 /ai/agent/** 控制器结构。
                .route("ai-agent-api", route -> route
                        .path("/api/ai/agent/**")
                        .filters(filter -> standardFilters(filter, properties, "agent-api")
                                .rewritePath("/api/ai/agent/?(?<segment>.*)", "/ai/agent/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getRagUri().toString()))
                // 代码检索和代码片段相关接口都落在 rag 服务中。
                .route("ai-code-api", route -> route
                        .path("/api/ai/code/**")
                        .filters(filter -> standardFilters(filter, properties, "code-api")
                                .rewritePath("/api/ai/code/?(?<segment>.*)", "/ai/code/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getRagUri().toString()))
                .build();
    }

    private GatewayFilterSpec standardFilters(
            GatewayFilterSpec filter,
            Qctv1GatewayProperties properties,
            String circuitBreakerName
    ) {
        // 网关和下游服务都可能补充 CORS 响应头，这里先做一次去重，
        // 避免浏览器收到重复值后出现跨域异常。
        filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );

        if (properties.getRoutes().isCircuitBreakerEnabled()) {
            // 熔断器按路由维度生效，避免某个下游接口持续异常时反复占用网关资源。
            filter.circuitBreaker(config -> config.setName(circuitBreakerName));
        }

        if (properties.getRoutes().isRetryEnabled()) {
            // 重试只对 GET 请求开放，并且只针对典型网关类状态码，
            // 这样可以降低误重放非幂等请求的风险。
            filter.retry(config -> config
                    .setRetries(properties.getRoutes().getRetryCount())
                    .setMethods(HttpMethod.GET)
                    .setStatuses(
                            HttpStatus.BAD_GATEWAY,
                            HttpStatus.SERVICE_UNAVAILABLE,
                            HttpStatus.GATEWAY_TIMEOUT
                    ));
        }

        return filter;
    }

    private GatewayFilterSpec sseFilters(GatewayFilterSpec filter) {
        // SSE 响应需要保持最小过滤链，尤其不能重试，
        // 否则可能造成流重复、顺序错乱或者连接被中断。
        return filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );
    }
}
