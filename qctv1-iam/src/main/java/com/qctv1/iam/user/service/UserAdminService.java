package com.qctv1.iam.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qctv1.iam.auth.service.AuthService;
import com.qctv1.iam.common.BusinessException;
import com.qctv1.iam.common.PageResponse;
import com.qctv1.iam.user.dto.CreateUserRequest;
import com.qctv1.iam.user.dto.ResetPasswordRequest;
import com.qctv1.iam.user.dto.UpdateUserRequest;
import com.qctv1.iam.user.dto.UpdateUserStatusRequest;
import com.qctv1.iam.user.dto.UserPageQuery;
import com.qctv1.iam.user.entity.BaseUser;
import com.qctv1.iam.user.mapper.BaseUserMapper;
import com.qctv1.iam.user.vo.UserListItemVo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理服务。
 * 面向后台管理员使用，负责分页查询、创建、编辑、启停和重置密码。
 */
@Service
public class UserAdminService {

    private final BaseUserMapper baseUserMapper;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserAdminService(BaseUserMapper baseUserMapper, AuthService authService) {
        this.baseUserMapper = baseUserMapper;
        this.authService = authService;
    }

    public PageResponse<UserListItemVo> page(UserPageQuery query) {
        // 查询条件与前端搜索栏保持一致，所有分页都走数据库层而不是前端截取。
        Page<BaseUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<BaseUser> userPage = baseUserMapper.selectPage(page, new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getIsDeleted, "0")
                .like(StringUtils.hasText(query.getUserName()), BaseUser::getUserName, query.getUserName())
                .like(StringUtils.hasText(query.getTrueName()), BaseUser::getTrueName, query.getTrueName())
                .like(StringUtils.hasText(query.getMobile()), BaseUser::getMobile, query.getMobile())
                .eq(StringUtils.hasText(query.getRoleCode()), BaseUser::getRoleCode, query.getRoleCode())
                .eq(StringUtils.hasText(query.getStatus()), BaseUser::getStatus, query.getStatus())
                .orderByDesc(BaseUser::getCreateTime));
        List<UserListItemVo> records = userPage.getRecords().stream().map(this::toListItem).toList();
        return PageResponse.of(userPage.getCurrent(), userPage.getSize(), userPage.getTotal(), records);
    }

    @Transactional
    public void create(CreateUserRequest request, Long operatorId) {
        // 新增用户时写入创建人、时间以及密码哈希。
        ensureUnique(request.getUserName(), request.getMobile(), request.getEmail(), null);
        LocalDateTime now = LocalDateTime.now();
        BaseUser user = new BaseUser();
        fillUser(user, request.getUserName(), request.getTrueName(), request.getMobile(), request.getEmail(),
                request.getTrueNameEn(), request.getGender(), request.getBirth(), request.getTel(), request.getAddr(),
                request.getPostCode(), request.getIdentityType(), request.getIdentityNum(), request.getLogo(),
                request.getRemark(), request.getStatus(), request.getRoleCode());
        user.setIsDeleted("0");
        user.setCreateUserId(operatorId);
        user.setCreateTime(now);
        user.setUpdateUserId(operatorId);
        user.setUpdateTime(now);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordUpdateTime(now);
        baseUserMapper.insert(user);
    }

    @Transactional
    public void update(Long userId, UpdateUserRequest request, Long operatorId) {
        BaseUser user = getUser(userId);
        ensureUnique(request.getUserName(), request.getMobile(), request.getEmail(), userId);
        fillUser(user, request.getUserName(), request.getTrueName(), request.getMobile(), request.getEmail(),
                request.getTrueNameEn(), request.getGender(), request.getBirth(), request.getTel(), request.getAddr(),
                request.getPostCode(), request.getIdentityType(), request.getIdentityNum(), request.getLogo(),
                request.getRemark(), request.getStatus(), request.getRoleCode());
        user.setUpdateUserId(operatorId);
        user.setUpdateTime(LocalDateTime.now());
        baseUserMapper.updateById(user);
        if (!"1".equals(user.getStatus())) {
            // 用户被改成停用后，立即清除现有登录态。
            authService.logout(userId);
        }
    }

    @Transactional
    public void updateStatus(Long userId, UpdateUserStatusRequest request, Long operatorId) {
        BaseUser user = getUser(userId);
        user.setStatus("0".equals(request.getStatus()) ? "0" : "1");
        user.setUpdateUserId(operatorId);
        user.setUpdateTime(LocalDateTime.now());
        baseUserMapper.updateById(user);
        if (!"1".equals(user.getStatus())) {
            // 停用用户时要同步让其会话失效。
            authService.logout(userId);
        }
    }

    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request, Long operatorId) {
        BaseUser user = getUser(userId);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordUpdateTime(LocalDateTime.now());
        user.setUpdateUserId(operatorId);
        user.setUpdateTime(LocalDateTime.now());
        baseUserMapper.updateById(user);
        // 重置密码后强制退出，避免旧 token 继续可用。
        authService.logout(userId);
    }

    private BaseUser getUser(Long userId) {
        BaseUser user = baseUserMapper.selectById(userId);
        if (user == null || "1".equals(user.getIsDeleted())) {
            throw new BusinessException(404, "User not found");
        }
        return user;
    }

    private void ensureUnique(String userName, String mobile, String email, Long excludeId) {
        // 三个唯一字段分开校验，便于返回准确错误信息。
        ensureCount(userName, BaseUser::getUserName, "Username already exists", excludeId);
        ensureCount(mobile, BaseUser::getMobile, "Mobile already exists", excludeId);
        if (StringUtils.hasText(email)) {
            ensureCount(email, BaseUser::getEmail, "Email already exists", excludeId);
        }
    }

    private <T> void ensureCount(T value, SFunction<BaseUser, ?> column, String message, Long excludeId) {
        BaseUser existing = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>()
                .eq(column, value)
                .eq(BaseUser::getIsDeleted, "0")
                .ne(excludeId != null, BaseUser::getId, excludeId)
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException(400, message);
        }
    }

    private void fillUser(
            BaseUser user,
            String userName,
            String trueName,
            String mobile,
            String email,
            String trueNameEn,
            String gender,
            String birth,
            String tel,
            String addr,
            String postCode,
            String identityType,
            String identityNum,
            String logo,
            String remark,
            String status,
            String roleCode
    ) {
        // 统一的字段回填入口，避免 create/update 各自维护一套赋值逻辑。
        user.setUserName(userName);
        user.setTrueName(trueName);
        user.setMobile(mobile);
        user.setEmail(StringUtils.hasText(email) ? email.trim() : null);
        user.setTrueNameEn(StringUtils.hasText(trueNameEn) ? trueNameEn.trim() : "");
        user.setGender(StringUtils.hasText(gender) ? gender.trim() : "");
        user.setBirth(blankToNull(birth));
        user.setTel(blankToNull(tel));
        user.setAddr(blankToNull(addr));
        user.setPostCode(blankToNull(postCode));
        user.setIdentityType(StringUtils.hasText(identityType) ? identityType.trim() : "10");
        user.setIdentityNum(blankToNull(identityNum));
        user.setLogo(blankToNull(logo));
        user.setRemark(blankToNull(remark));
        user.setStatus("0".equals(status) ? "0" : "1");
        user.setRoleCode("ADMIN".equalsIgnoreCase(roleCode) ? "ADMIN" : "USER");
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private UserListItemVo toListItem(BaseUser user) {
        // 列表页只回传展示所需字段，不暴露敏感信息。
        UserListItemVo itemVo = new UserListItemVo();
        itemVo.setId(user.getId());
        itemVo.setUserName(user.getUserName());
        itemVo.setTrueName(user.getTrueName());
        itemVo.setMobile(user.getMobile());
        itemVo.setEmail(user.getEmail());
        itemVo.setRoleCode(user.getRoleCode());
        itemVo.setStatus(user.getStatus());
        itemVo.setLoginTime(user.getLoginTime());
        itemVo.setCreateTime(user.getCreateTime());
        return itemVo;
    }
}
