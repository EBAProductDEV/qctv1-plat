package com.qctv1.gateway.security;

public record TokenPrincipal(Long userId, String userName, String roleCode, String sessionId) {
}
