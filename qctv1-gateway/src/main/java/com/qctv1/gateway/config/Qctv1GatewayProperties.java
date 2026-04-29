package com.qctv1.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "qctv1.gateway")
public class Qctv1GatewayProperties {

    private final Routes routes = new Routes();

    private final Auth auth = new Auth();

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

        private URI chatUri = URI.create("lb://qctv1-ai-chat");

        private URI ragUri = URI.create("lb://qctv1-ai-rag");

        private URI dramaUri = URI.create("lb://qctv1-ai-drama");

        private URI iamUri = URI.create("lb://qctv1-iam");

        private int connectTimeoutMs = 10_000;

        private int responseTimeoutMs = 180_000;

        private boolean retryEnabled = true;

        private int retryCount = 2;

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

        public URI getDramaUri() {
            return dramaUri;
        }

        public void setDramaUri(URI dramaUri) {
            this.dramaUri = dramaUri;
        }

        public URI getIamUri() {
            return iamUri;
        }

        public void setIamUri(URI iamUri) {
            this.iamUri = iamUri;
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

        private boolean enabled = true;

        private List<String> whitelist = new ArrayList<>(List.of(
                "/actuator/**",
                "/api/iam/auth/login",
                "/api/iam/auth/register",
                "/api/iam/auth/refresh",
                "/api/ai/chat/stream",
                "/api/ai/agent/chat",
                "/api/ai/drama/assets/**"
        ));

        private String jwtSecret = "Qctv1JwtSecretKeyForDevOnlyPleaseChange1234567890";

        private String issuer = "qctv1-iam";

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

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Cors {

        private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://localhost:*",
                "https://127.0.0.1:*"
        ));

        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

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

