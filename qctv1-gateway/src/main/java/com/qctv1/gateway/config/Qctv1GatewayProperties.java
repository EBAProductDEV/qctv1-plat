package com.qctv1.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关自定义配置。
 * 这里把路由目标、鉴权开关和 CORS 规则集中收口，方便本地模式和 nacos 模式统一复用。
 */
@ConfigurationProperties(prefix = "qctv1.gateway")
public class Qctv1GatewayProperties {

    // routes 描述“请求要转发到哪里”以及“普通接口启用哪些治理能力”。
    private final Routes routes = new Routes();

    // auth 单独拆出来，便于本地开发时关闭，后续接入真实认证时再打开。
    private final Auth auth = new Auth();

    // cors 会被自定义预检过滤器使用，因为浏览器预检请求往往先于正常路由匹配到达。
    private final Cors cors = new Cors();

    public Routes getRoutes() {
        return routes;
    }

    public Auth getAuth() {
        return auth;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Routes {

        // nacos 模式下这里是逻辑服务名，由负载均衡器去解析真实实例；
        // local 模式下会被 application-local.yml 覆盖成固定 HTTP 地址。
        private URI chatUri = URI.create("lb://qctv1-ai-chat");

        // rag 服务的目标地址规则与 chat 服务相同。
        private URI ragUri = URI.create("lb://qctv1-ai-rag");

        // 下游连接建立超时时间。
        private int connectTimeoutMs = 10_000;

        // 普通下游响应的读取超时时间。
        private int responseTimeoutMs = 180_000;

        // 重试开关保留为可配置，因为普通 GET 接口适合重试，而流式接口不适合。
        private boolean retryEnabled = true;

        private int retryCount = 2;

        // 熔断器保留为路由级能力，后续如果需要可以对不同接口分别调优。
        private boolean circuitBreakerEnabled = true;

        public URI getChatUri() {
            return chatUri;
        }

        public void setChatUri(URI chatUri) {
            this.chatUri = chatUri;
        }

        public URI getRagUri() {
            return ragUri;
        }

        public void setRagUri(URI ragUri) {
            this.ragUri = ragUri;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getResponseTimeoutMs() {
            return responseTimeoutMs;
        }

        public void setResponseTimeoutMs(int responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
        }

        public boolean isRetryEnabled() {
            return retryEnabled;
        }

        public void setRetryEnabled(boolean retryEnabled) {
            this.retryEnabled = retryEnabled;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public boolean isCircuitBreakerEnabled() {
            return circuitBreakerEnabled;
        }

        public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
            this.circuitBreakerEnabled = circuitBreakerEnabled;
        }
    }

    public static class Auth {

        // 默认关闭认证，优先保证前后端本地链路先打通，再逐步接入真实 JWT / OAuth2。
        private boolean enabled = false;

        // 流式接口和 actuator 默认放行，便于本地开发和健康检查。
        private List<String> whitelist = new ArrayList<>(List.of(
                "/actuator/**",
                "/api/ai/chat/stream",
                "/api/ai/agent/chat"
        ));

        // 临时 mock token 列表，只供占位鉴权实现使用。
        private List<String> mockValidTokens = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }

        public List<String> getMockValidTokens() {
            return mockValidTokens;
        }

        public void setMockValidTokens(List<String> mockValidTokens) {
            this.mockValidTokens = mockValidTokens;
        }
    }

    public static class Cors {

        // 默认允许本地浏览器来源，方便 qctv1-web 在常见开发端口下直接访问网关。
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://localhost:*",
                "https://127.0.0.1:*"
        ));

        // 这些方法已经覆盖当前前端请求场景以及浏览器预检请求。
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        // 暴露请求 ID，便于前端、网关、下游服务三端串联日志。
        private List<String> exposedHeaders = new ArrayList<>(List.of("X-Request-Id"));

        private boolean allowCredentials = true;

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }
}
