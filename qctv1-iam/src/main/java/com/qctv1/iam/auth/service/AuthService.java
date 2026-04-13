package com.qctv1.iam.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qctv1.iam.auth.dto.LoginRequest;
import com.qctv1.iam.auth.dto.RefreshTokenRequest;
import com.qctv1.iam.auth.dto.RegisterRequest;
import com.qctv1.iam.auth.vo.AuthTokenVo;
import com.qctv1.iam.auth.vo.MenuListVo;
import com.qctv1.iam.auth.vo.RouteItemVo;
import com.qctv1.iam.auth.vo.RouteMetaVo;
import com.qctv1.iam.auth.vo.UserProfileVo;
import com.qctv1.iam.common.BusinessException;
import com.qctv1.iam.user.entity.BaseUser;
import com.qctv1.iam.user.mapper.BaseUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证域核心服务。
 * 负责注册、登录、刷新 token、登出、个人资料以及动态菜单返回。
 */
@Service
public class AuthService {

    private final BaseUserMapper baseUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenService jwtTokenService;
    private final RedisSessionService redisSessionService;

    public AuthService(
            BaseUserMapper baseUserMapper,
            JwtTokenService jwtTokenService,
            RedisSessionService redisSessionService
    ) {
        this.baseUserMapper = baseUserMapper;
        this.jwtTokenService = jwtTokenService;
        this.redisSessionService = redisSessionService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        // 公开注册先校验两次密码是否一致，再校验用户名/手机号/邮箱唯一性。
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(400, "Passwords do not match");
        }
        ensureUserNameUnique(request.getUserName(), null);
        ensureMobileUnique(request.getMobile(), null);
        ensureEmailUnique(request.getEmail(), null);
        // 注册用户默认就是启用状态的普通用户，不需要额外激活流程。
        LocalDateTime now = LocalDateTime.now();
        BaseUser user = new BaseUser();
        user.setUserName(request.getUserName());
        user.setTrueName(request.getTrueName());
        user.setTrueNameEn("");
        user.setMobile(request.getMobile());
        user.setEmail(blankToNull(request.getEmail()));
        user.setGender("");
        user.setIdentityType("10");
        user.setStatus("1");
        user.setIsDeleted("0");
        user.setCreateUserId(0L);
        user.setCreateTime(now);
        user.setUpdateUserId(0L);
        user.setUpdateTime(now);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoleCode("USER");
        user.setPasswordUpdateTime(now);
        baseUserMapper.insert(user);
    }

    @Transactional
    public AuthTokenVo login(LoginRequest request, String loginIp) {
        // 当前登录方式固定为 user_name + password，因此这里只按用户名查用户。
        BaseUser user = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getUserName, request.getUserName())
                .eq(BaseUser::getIsDeleted, "0")
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "Invalid username or password");
        }
        if (!"1".equals(user.getStatus())) {
            throw new BusinessException(403, "User is disabled");
        }
        // 重新生成 token 对并写入新的 sessionId，天然实现“新登录覆盖旧登录”。
        JwtTokenService.TokenPair tokenPair = jwtTokenService.issueTokens(user.getId(), user.getUserName(), user.getRoleCode());
        saveSession(user.getId(), tokenPair);

        // 登录成功后更新最近登录信息，供个人中心和用户管理页展示。
        user.setLoginIp(loginIp);
        user.setLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        baseUserMapper.updateById(user);
        return buildTokenVo(user, tokenPair);
    }

    @Transactional
    public AuthTokenVo refresh(RefreshTokenRequest request) {
        // refresh 时先解析 JWT，再校验 token 类型，避免把 accessToken 错当成 refreshToken。
        JwtTokenService.TokenClaims claims = jwtTokenService.parse(request.getRefreshToken());
        if (!JwtTokenService.TOKEN_TYPE_REFRESH.equals(claims.tokenType())) {
            throw new BusinessException(401, "Invalid refresh token type");
        }
        // Redis 中保存的是当前账号最近一次有效登录的 refresh sessionId。
        String currentSessionId = redisSessionService.getRefreshSessionId(claims.userId());
        if (!claims.sessionId().equals(currentSessionId)) {
            throw new BusinessException(401, "Refresh token has expired");
        }
        // 刷新成功后会整体换新 token，而不是继续沿用旧 accessToken。
        BaseUser user = getEnabledUserById(claims.userId());
        JwtTokenService.TokenPair tokenPair = jwtTokenService.issueTokens(user.getId(), user.getUserName(), user.getRoleCode());
        saveSession(user.getId(), tokenPair);
        return buildTokenVo(user, tokenPair);
    }

    public void logout(Long userId) {
        // 清空 Redis 会话后，即使旧 token 还没过期，也会因为 sessionId 不匹配而失效。
        redisSessionService.clearSession(userId);
    }

    public UserProfileVo profile(Long userId) {
        return toProfile(getEnabledUserById(userId));
    }

    public MenuListVo menus(String roleCode) {
        // 本期菜单先按角色在后端固定返回，不走数据库配置，后续可再演进为菜单表。
        MenuListVo menuListVo = new MenuListVo();
        List<RouteItemVo> menus = new ArrayList<>();
        menus.add(buildListMenu());
        menus.add(buildFormMenu());
        menus.add(buildDetailMenu());
        menus.add(buildFrameMenu());
        if ("ADMIN".equalsIgnoreCase(roleCode)) {
            menus.add(buildSystemMenu());
        }
        menuListVo.setList(menus);
        return menuListVo;
    }

    public UserProfileVo toProfile(BaseUser user) {
        UserProfileVo profileVo = new UserProfileVo();
        profileVo.setId(user.getId());
        profileVo.setUserName(user.getUserName());
        profileVo.setTrueName(user.getTrueName());
        profileVo.setName(StringUtils.hasText(user.getTrueName()) ? user.getTrueName() : user.getUserName());
        profileVo.setMobile(user.getMobile());
        profileVo.setEmail(user.getEmail());
        profileVo.setRoleCode(user.getRoleCode());
        profileVo.setStatus(user.getStatus());
        profileVo.setLogo(user.getLogo());
        profileVo.setGender(user.getGender());
        profileVo.setBirth(user.getBirth());
        profileVo.setTel(user.getTel());
        profileVo.setAddr(user.getAddr());
        profileVo.setPostCode(user.getPostCode());
        profileVo.setIdentityType(user.getIdentityType());
        profileVo.setIdentityNum(user.getIdentityNum());
        profileVo.setRemark(user.getRemark());
        profileVo.setCreateTime(user.getCreateTime());
        profileVo.setUpdateTime(user.getUpdateTime());
        profileVo.setLoginTime(user.getLoginTime());
        profileVo.setRoles(List.of(user.getRoleCode()));
        return profileVo;
    }

    public BaseUser getEnabledUserById(Long userId) {
        // 获取“当前可用用户”时同时校验：存在、未删除、已启用。
        BaseUser user = baseUserMapper.selectById(userId);
        if (user == null || "1".equals(user.getIsDeleted())) {
            throw new BusinessException(404, "User not found");
        }
        if (!"1".equals(user.getStatus())) {
            throw new BusinessException(403, "User is disabled");
        }
        return user;
    }

    private AuthTokenVo buildTokenVo(BaseUser user, JwtTokenService.TokenPair tokenPair) {
        // 登录和刷新统一返回同一种结构，前端只维护一套登录态逻辑即可。
        AuthTokenVo tokenVo = new AuthTokenVo();
        tokenVo.setAccessToken(tokenPair.accessToken());
        tokenVo.setRefreshToken(tokenPair.refreshToken());
        tokenVo.setExpiresIn(tokenPair.accessExpireAtMillis());
        tokenVo.setUserInfo(toProfile(user));
        return tokenVo;
    }

    private void saveSession(Long userId, JwtTokenService.TokenPair tokenPair) {
        // access 与 refresh 在 Redis 中使用不同 TTL，符合短期访问、长期续期的设计。
        redisSessionService.saveSession(
                userId,
                tokenPair.sessionId(),
                Duration.ofMinutes(30),
                Duration.ofDays(7)
        );
    }

    private void ensureUserNameUnique(String userName, Long excludeId) {
        // excludeId 用于编辑用户场景，避免更新自己时被误判为重复。
        BaseUser user = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getUserName, userName)
                .eq(BaseUser::getIsDeleted, "0")
                .ne(excludeId != null, BaseUser::getId, excludeId)
                .last("limit 1"));
        if (user != null) {
            throw new BusinessException(400, "Username already exists");
        }
    }

    private void ensureMobileUnique(String mobile, Long excludeId) {
        BaseUser user = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getMobile, mobile)
                .eq(BaseUser::getIsDeleted, "0")
                .ne(excludeId != null, BaseUser::getId, excludeId)
                .last("limit 1"));
        if (user != null) {
            throw new BusinessException(400, "Mobile already exists");
        }
    }

    private void ensureEmailUnique(String email, Long excludeId) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        BaseUser user = baseUserMapper.selectOne(new LambdaQueryWrapper<BaseUser>()
                .eq(BaseUser::getEmail, email)
                .eq(BaseUser::getIsDeleted, "0")
                .ne(excludeId != null, BaseUser::getId, excludeId)
                .last("limit 1"));
        if (user != null) {
            throw new BusinessException(400, "Email already exists");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private RouteItemVo buildListMenu() {
        return route("/list", "list", "LAYOUT", "/list/base", meta("List", "List", "view-list"), List.of(
                route("base", "ListBase", "/list/base/index", null, meta("Base List", "Base List", null), null),
                route("card", "ListCard", "/list/card/index", null, meta("Card List", "Card List", null), null),
                route("filter", "ListFilter", "/list/filter/index", null, meta("Filter List", "Filter List", null), null),
                route("tree", "ListTree", "/list/tree/index", null, meta("Tree List", "Tree List", null), null)
        ));
    }

    private RouteItemVo buildFormMenu() {
        return route("/form", "form", "LAYOUT", "/form/base", meta("Form", "Form", "edit-1"), List.of(
                route("base", "FormBase", "/form/base/index", null, meta("Base Form", "Base Form", null), null),
                route("step", "FormStep", "/form/step/index", null, meta("Step Form", "Step Form", null), null)
        ));
    }

    private RouteItemVo buildDetailMenu() {
        return route("/detail", "detail", "LAYOUT", "/detail/base", meta("Detail", "Detail", "layers"), List.of(
                route("base", "DetailBase", "/detail/base/index", null, meta("Base Detail", "Base Detail", null), null),
                route("advanced", "DetailAdvanced", "/detail/advanced/index", null, meta("Card Detail", "Card Detail", null), null),
                route("deploy", "DetailDeploy", "/detail/deploy/index", null, meta("Data Detail", "Data Detail", null), null),
                route("secondary", "DetailSecondary", "/detail/secondary/index", null, meta("Secondary Detail", "Secondary Detail", null), null)
        ));
    }

    private RouteItemVo buildFrameMenu() {
        RouteItemVo doc = route("doc", "Doc", "IFRAME", null, meta("Docs (IFrame)", "Docs (IFrame)", null), null);
        doc.getMeta().setFrameSrc("https://tdesign.tencent.com/starter/docs/vue-next/get-started");
        RouteItemVo tdesign = route("TDesign", "TDesign", "IFRAME", null, meta("TDesign (IFrame)", "TDesign (IFrame)", null), null);
        tdesign.getMeta().setFrameSrc("https://tdesign.tencent.com/vue-next/getting-started");
        RouteItemVo tdesignLink = route("TDesign2", "TDesign2", "IFRAME", null, meta("TDesign (Link)", "TDesign (Link)", null), null);
        tdesignLink.getMeta().setFrameSrc("https://tdesign.tencent.com/vue-next/getting-started");
        tdesignLink.getMeta().setFrameBlank(true);
        return route("/frame", "Frame", "LAYOUT", "/frame/doc", meta("External", "External", "internet"), List.of(doc, tdesign, tdesignLink));
    }

    private RouteItemVo buildSystemMenu() {
        return route("/system", "system", "LAYOUT", "/system/user", meta("系统管理", "System", "setting"), List.of(
                route("user", "SystemUser", "/system/user/index", null, meta("用户管理", "User Management", null), null)
        ));
    }

    private RouteItemVo route(
            String path,
            String name,
            String component,
            String redirect,
            RouteMetaVo meta,
            List<RouteItemVo> children
    ) {
        RouteItemVo itemVo = new RouteItemVo();
        itemVo.setPath(path);
        itemVo.setName(name);
        itemVo.setComponent(component);
        itemVo.setRedirect(redirect);
        itemVo.setMeta(meta);
        itemVo.setChildren(children);
        return itemVo;
    }

    private RouteMetaVo meta(String zhCn, String enUs, String icon) {
        // 前端动态菜单要求 title 是多语言对象，这里统一封装，减少重复代码。
        RouteMetaVo metaVo = new RouteMetaVo();
        Map<String, String> title = new LinkedHashMap<>();
        title.put("zh_CN", zhCn);
        title.put("en_US", enUs);
        metaVo.setTitle(title);
        metaVo.setIcon(icon);
        return metaVo;
    }
}
