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
 * 网关路由配置。
 *
 * 前端只访问统一入口，例如 /api/ai/drama/series。
 * 网关在这里根据 Path 谓词命中对应路由，然后通过 RewritePath 把公网路径改成下游服务真实路径。
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator qctv1RouteLocator(RouteLocatorBuilder builder, Qctv1GatewayProperties properties) {
        return builder.routes()
                .route("iam-api", route -> route
                        .path("/api/iam/**")
                        .filters(filter -> passthroughFilters(filter)
                                .rewritePath("/api/iam/?(?<segment>.*)", "/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getIamUri().toString()))
                .route("ai-chat-stream", route -> route
                        .path("/api/ai/chat/stream")
                        .filters(filter -> sseFilters(filter)
                                .rewritePath("/api/ai/chat/(?<segment>.*)", "/chat/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                .route("ai-chat-api", route -> route
                        .path("/api/ai/chat/**")
                        .filters(filter -> standardFilters(filter, properties, "chat-api")
                                .rewritePath("/api/ai/chat/?(?<segment>.*)", "/chat/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                .route("ai-image-api", route -> route
                        .path("/api/ai/image/**")
                        .filters(filter -> standardFilters(filter, properties, "image-api")
                                .rewritePath("/api/ai/image/?(?<segment>.*)", "/image/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                .route("ai-hello-api", route -> route
                        .path("/api/ai/hello/**")
                        .filters(filter -> standardFilters(filter, properties, "hello-api")
                                .rewritePath("/api/ai/hello/?(?<segment>.*)", "/hello/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getChatUri().toString()))
                .route("ai-drama-task-ws", route -> route
                        .path("/api/ai/drama/ws/tasks")
                        .filters(filter -> passthroughFilters(filter)
                                .rewritePath("/api/ai/drama/?(?<segment>.*)", "/drama/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .uri(toWebSocketUri(properties.getRoutes().getDramaUri().toString())))
                .route("ai-drama-api", route -> route
                        .path("/api/ai/drama/**")
                        .filters(filter -> standardFilters(filter, properties, "drama-api")
                                .rewritePath("/api/ai/drama/?(?<segment>.*)", "/drama/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getDramaUri().toString()))
                .route("ai-agent-stream", route -> route
                        .path("/api/ai/agent/chat")
                        .filters(filter -> sseFilters(filter)
                                .rewritePath("/api/ai/agent/(?<segment>.*)", "/ai/agent/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getRagUri().toString()))
                .route("ai-agent-api", route -> route
                        .path("/api/ai/agent/**")
                        .filters(filter -> standardFilters(filter, properties, "agent-api")
                                .rewritePath("/api/ai/agent/?(?<segment>.*)", "/ai/agent/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getRagUri().toString()))
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
        // 去重响应头，避免网关和下游服务都写 CORS 响应头时浏览器判定跨域失败。
        filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );

        // 普通 API 允许熔断，防止下游长时间异常时拖垮网关线程和连接池。
        if (properties.getRoutes().isCircuitBreakerEnabled()) {
            filter.circuitBreaker(config -> config.setName(circuitBreakerName));
        }

        // 只对 GET 做重试，避免 POST/PUT 这类有副作用的请求被重复提交。
        if (properties.getRoutes().isRetryEnabled()) {
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
        // SSE 流式接口不能叠加重试、响应体改写等策略，否则容易打断流式输出。
        return filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );
    }

    private GatewayFilterSpec passthroughFilters(GatewayFilterSpec filter) {
        // 直通路由只做必要响应头去重，不附加熔断和重试。
        return filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );
    }

    private String toWebSocketUri(String uri) {
        if (uri.startsWith("lb://")) {
            return "lb:ws://" + uri.substring("lb://".length());
        }
        if (uri.startsWith("http://")) {
            return "ws://" + uri.substring("http://".length());
        }
        if (uri.startsWith("https://")) {
            return "wss://" + uri.substring("https://".length());
        }
        return uri;
    }
}
