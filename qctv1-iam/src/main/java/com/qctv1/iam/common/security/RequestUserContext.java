package com.qctv1.iam.common.security;

import com.qctv1.iam.common.BusinessException;
import com.qctv1.iam.common.header.IamHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class RequestUserContext {

    private RequestUserContext() {
    }

    public static CurrentUser requireCurrentUser(HttpServletRequest request) {
        String userId = request.getHeader(IamHeaders.USER_ID);
        String userName = request.getHeader(IamHeaders.USER_NAME);
        String roleCode = request.getHeader(IamHeaders.USER_ROLE);
        return requireCurrentUser(userId, userName, roleCode);
    }

    public static CurrentUser requireCurrentUser(String userId, String userName, String roleCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(userName) || !StringUtils.hasText(roleCode)) {
            throw new BusinessException(401, "Login required");
        }
        return new CurrentUser(Long.parseLong(userId), userName, roleCode);
    }

    public static void requireAdmin(HttpServletRequest request) {
        String userId = request.getHeader(IamHeaders.USER_ID);
        String userName = request.getHeader(IamHeaders.USER_NAME);
        String roleCode = request.getHeader(IamHeaders.USER_ROLE);
        requireAdmin(userId, userName, roleCode);
    }

    public static void requireAdmin(String userId, String userName, String roleCode) {
        CurrentUser currentUser = requireCurrentUser(userId, userName, roleCode);
        if (!currentUser.isAdmin()) {
            throw new BusinessException(403, "Admin permission required");
        }
    }
}
