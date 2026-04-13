package com.qctv1.iam.user.controller;

import com.qctv1.iam.common.ApiResponse;
import com.qctv1.iam.common.PageResponse;
import com.qctv1.iam.common.security.CurrentUser;
import com.qctv1.iam.common.security.RequestUserContext;
import com.qctv1.iam.user.dto.CreateUserRequest;
import com.qctv1.iam.user.dto.ResetPasswordRequest;
import com.qctv1.iam.user.dto.UpdateUserRequest;
import com.qctv1.iam.user.dto.UpdateUserStatusRequest;
import com.qctv1.iam.user.dto.UserPageQuery;
import com.qctv1.iam.user.service.UserAdminService;
import com.qctv1.iam.user.vo.UserListItemVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 * 所有接口都要求管理员身份，控制器本身不做业务，只负责权限入口和参数接收。
 */
@Validated
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserListItemVo>> page(@Valid UserPageQuery query, HttpServletRequest request) {
        // 分页查询是用户管理页的主入口，因此先校验管理员权限再执行业务查询。
        RequestUserContext.requireAdmin(request);
        return ApiResponse.success(userAdminService.page(query));
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CreateUserRequest request, HttpServletRequest httpServletRequest) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(httpServletRequest);
        RequestUserContext.requireAdmin(httpServletRequest);
        userAdminService.create(request, currentUser.userId());
        return ApiResponse.success("Create user success", null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpServletRequest
    ) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(httpServletRequest);
        RequestUserContext.requireAdmin(httpServletRequest);
        userAdminService.update(id, request, currentUser.userId());
        return ApiResponse.success("Update user success", null);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpServletRequest
    ) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(httpServletRequest);
        RequestUserContext.requireAdmin(httpServletRequest);
        userAdminService.updateStatus(id, request, currentUser.userId());
        return ApiResponse.success("Update status success", null);
    }

    @PutMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable("id") Long id,
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(httpServletRequest);
        RequestUserContext.requireAdmin(httpServletRequest);
        userAdminService.resetPassword(id, request, currentUser.userId());
        return ApiResponse.success("Reset password success", null);
    }
}
