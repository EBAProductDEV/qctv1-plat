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
 * 这里统一定义前端访问路径与下游服务真实路径的映射关系。
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator qctv1RouteLocator(RouteLocatorBuilder builder, Qctv1GatewayProperties properties) {
        return builder.routes()
                .route("iam-api", route -> route
                        .path("/api/iam/**")
                        // IAM 认证接口不走熔断/重试，避免登录、注册被 1 秒超时或错误重试干扰。
                        .filters(filter -> passthroughFilters(filter)
                                .rewritePath("/api/iam/?(?<segment>.*)", "/${segment}"))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, properties.getRoutes().getConnectTimeoutMs())
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, properties.getRoutes().getResponseTimeoutMs())
                        .uri(properties.getRoutes().getIamUri().toString()))
                .route("ai-chat-stream", route -> route
                        .path("/api/ai/chat/stream")
                        // SSE 流接口只做最轻量的头处理，避免重试/熔断打断流式输出。
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
        // 去重响应头，避免 CORS 头在网关和下游重复写入时产生冲突。
        filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );

        // 普通 AI 接口允许走熔断，防止下游异常时拖垮网关。
        if (properties.getRoutes().isCircuitBreakerEnabled()) {
            filter.circuitBreaker(config -> config.setName(circuitBreakerName));
        }

        // 这里只给 GET 请求做重试，避免对 POST/PUT 这类有副作用的请求重复提交。
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
        return filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );
    }

    private GatewayFilterSpec passthroughFilters(GatewayFilterSpec filter) {
        // 直通场景只保留必要的响应头去重，不叠加任何保护性策略。
        return filter.dedupeResponseHeader(
                "Access-Control-Allow-Credentials Access-Control-Allow-Origin",
                DedupeResponseHeaderGatewayFilterFactory.Strategy.RETAIN_UNIQUE.name()
        );
    }
}
