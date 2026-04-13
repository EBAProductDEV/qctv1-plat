package com.qctv1.iam.auth.controller;

import com.qctv1.iam.auth.dto.LoginRequest;
import com.qctv1.iam.auth.dto.RefreshTokenRequest;
import com.qctv1.iam.auth.dto.RegisterRequest;
import com.qctv1.iam.auth.service.AuthService;
import com.qctv1.iam.auth.vo.AuthTokenVo;
import com.qctv1.iam.auth.vo.MenuListVo;
import com.qctv1.iam.auth.vo.UserProfileVo;
import com.qctv1.iam.common.ApiResponse;
import com.qctv1.iam.common.security.CurrentUser;
import com.qctv1.iam.common.security.RequestUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证对外接口。
 * 这里只做请求接入和参数校验，具体业务都下沉到 AuthService。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("Register success", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenVo> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        // 登录时把客户端 IP 一并传下去，便于记录最近登录信息。
        return ApiResponse.success("Login success", authService.login(request, httpServletRequest.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenVo> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("Refresh success", authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(request);
        authService.logout(currentUser.userId());
        return ApiResponse.success("Logout success", null);
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileVo> profile(HttpServletRequest request) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(request);
        return ApiResponse.success(authService.profile(currentUser.userId()));
    }

    @GetMapping("/menus")
    public ApiResponse<MenuListVo> menus(HttpServletRequest request) {
        // 当前菜单按角色返回，前端据此构建动态路由和侧边栏。
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(request);
        return ApiResponse.success(authService.menus(currentUser.roleCode()));
    }
}
