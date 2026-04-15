package com.qctv1.iam.user.controller;

import com.qctv1.iam.api.common.PageResult;
import com.qctv1.iam.api.header.IamUserHeaders;
import com.qctv1.iam.api.user.IamUserApi;
import com.qctv1.iam.api.user.dto.UserListItemDto;
import com.qctv1.iam.api.user.dto.UserPageQuery;
import com.qctv1.iam.api.user.dto.UserProfileDto;
import com.qctv1.iam.auth.service.AuthService;
import com.qctv1.iam.auth.vo.UserProfileVo;
import com.qctv1.iam.common.PageResponse;
import com.qctv1.iam.common.security.CurrentUser;
import com.qctv1.iam.common.security.RequestUserContext;
import com.qctv1.iam.user.service.UserAdminService;
import com.qctv1.iam.user.vo.UserListItemVo;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/users")
public class InternalUserController implements IamUserApi {

    private final AuthService authService;

    private final UserAdminService userAdminService;

    public InternalUserController(AuthService authService, UserAdminService userAdminService) {
        this.authService = authService;
        this.userAdminService = userAdminService;
    }

    @Override
    @GetMapping("/current")
    public UserProfileDto getCurrentProfile(
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = IamUserHeaders.USER_NAME, required = false) String userName,
            @RequestHeader(value = IamUserHeaders.USER_ROLE, required = false) String roleCode
    ) {
        CurrentUser currentUser = RequestUserContext.requireCurrentUser(userId, userName, roleCode);
        return toProfileDto(authService.profile(currentUser.userId()));
    }

    @Override
    @GetMapping("/{id}")
    public UserProfileDto getUserById(
            @PathVariable("id") Long id,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = IamUserHeaders.USER_NAME, required = false) String userName,
            @RequestHeader(value = IamUserHeaders.USER_ROLE, required = false) String roleCode
    ) {
        RequestUserContext.requireAdmin(userId, userName, roleCode);
        return toProfileDto(authService.toProfile(authService.getEnabledUserById(id)));
    }

    @Override
    @PostMapping("/page")
    public PageResult<UserListItemDto> pageUsers(
            @Valid @RequestBody UserPageQuery query,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userId,
            @RequestHeader(value = IamUserHeaders.USER_NAME, required = false) String userName,
            @RequestHeader(value = IamUserHeaders.USER_ROLE, required = false) String roleCode
    ) {
        RequestUserContext.requireAdmin(userId, userName, roleCode);

        com.qctv1.iam.user.dto.UserPageQuery localQuery = new com.qctv1.iam.user.dto.UserPageQuery();
        localQuery.setPageNum(query.getPageNum());
        localQuery.setPageSize(query.getPageSize());
        localQuery.setUserName(query.getUserName());
        localQuery.setTrueName(query.getTrueName());
        localQuery.setMobile(query.getMobile());
        localQuery.setRoleCode(query.getRoleCode());
        localQuery.setStatus(query.getStatus());

        PageResponse<UserListItemVo> pageResponse = userAdminService.page(localQuery);
        List<UserListItemDto> items = pageResponse.getList().stream()
                .map(this::toListItemDto)
                .toList();
        return PageResult.of(pageResponse.getPageNum(), pageResponse.getPageSize(), pageResponse.getTotal(), items);
    }

    private UserProfileDto toProfileDto(UserProfileVo profileVo) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(profileVo.getId());
        dto.setUserName(profileVo.getUserName());
        dto.setTrueName(profileVo.getTrueName());
        dto.setName(profileVo.getName());
        dto.setMobile(profileVo.getMobile());
        dto.setEmail(profileVo.getEmail());
        dto.setRoleCode(profileVo.getRoleCode());
        dto.setStatus(profileVo.getStatus());
        dto.setLogo(profileVo.getLogo());
        dto.setGender(profileVo.getGender());
        dto.setBirth(profileVo.getBirth());
        dto.setTel(profileVo.getTel());
        dto.setAddr(profileVo.getAddr());
        dto.setPostCode(profileVo.getPostCode());
        dto.setIdentityType(profileVo.getIdentityType());
        dto.setIdentityNum(profileVo.getIdentityNum());
        dto.setRemark(profileVo.getRemark());
        dto.setCreateTime(profileVo.getCreateTime());
        dto.setUpdateTime(profileVo.getUpdateTime());
        dto.setLoginTime(profileVo.getLoginTime());
        dto.setRoles(profileVo.getRoles());
        return dto;
    }

    private UserListItemDto toListItemDto(UserListItemVo vo) {
        UserListItemDto dto = new UserListItemDto();
        dto.setId(vo.getId());
        dto.setUserName(vo.getUserName());
        dto.setTrueName(vo.getTrueName());
        dto.setMobile(vo.getMobile());
        dto.setEmail(vo.getEmail());
        dto.setRoleCode(vo.getRoleCode());
        dto.setStatus(vo.getStatus());
        dto.setLoginTime(vo.getLoginTime());
        dto.setCreateTime(vo.getCreateTime());
        return dto;
    }
}
