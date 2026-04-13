package com.qctv1.iam.common.security;

public record CurrentUser(Long userId, String userName, String roleCode) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(roleCode);
    }
}
