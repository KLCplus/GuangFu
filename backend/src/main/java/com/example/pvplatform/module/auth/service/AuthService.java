package com.example.pvplatform.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.EmailService;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.module.auth.vo.RefreshVO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.JwtTokenService;
import com.example.pvplatform.security.SecurityUtils;
import com.example.pvplatform.security.TokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final HttpServletRequest request;
    private final LoginAttemptService attemptService;
    private final LoginLogService logService;
    private final VerificationCodeService codeService;
    private final EmailService emailService;

    public AuthService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, HttpServletRequest request,
                       LoginAttemptService attemptService, LoginLogService logService,
                       VerificationCodeService codeService, EmailService emailService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.request = request;
        this.attemptService = attemptService;
        this.logService = logService;
        this.codeService = codeService;
        this.emailService = emailService;
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        // check username uniqueness
        Long count = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getUsername, req.username()));
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        // check email uniqueness if provided
        if (req.email() != null && !req.email().isBlank()) {
            Long emailCount = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getEmail, req.email()));
            if (emailCount > 0) {
                throw new BusinessException(400, "邮箱已被使用");
            }
        }

        // USER role must exist
        SysRoleDO role = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, "USER")
            .eq(SysRoleDO::getStatus, 1)
            .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException(500, "系统角色配置异常，请联系管理员");
        }

        SysUserDO user = new SysUserDO();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.username());
        if (req.email() != null && !req.email().isBlank()) {
            user.setEmail(req.email());
        }
        user.setStatus(1);
        user.setTokenVersion(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "用户名或邮箱已存在");
        }

        SysUserRoleDO relation = new SysUserRoleDO();
        relation.setUserId(user.getUserId());
        relation.setRoleId(role.getRoleId());
        userRoleMapper.insert(relation);

        logService.record(user.getUserId(), user.getUsername(), "REGISTER",
            getClientIp(), getUserAgent(), "SUCCESS", null);

        return Map.of("userId", user.getUserId(), "username", user.getUsername());
    }

    public LoginVO login(LoginRequest loginReq) {
        String ip = getClientIp();
        String ua = getUserAgent();

        // Check login attempt lock
        attemptService.checkNotLocked(loginReq.username());

        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getUsername, loginReq.username())
            .last("LIMIT 1"));

        if (user == null || !passwordEncoder.matches(loginReq.password(), user.getPasswordHash())) {
            attemptService.recordFailure(loginReq.username());
            logService.record(null, loginReq.username(), "LOGIN", ip, ua,
                "FAIL", "用户名或密码错误");
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            // Don't count disabled as credential failure
            logService.record(user.getUserId(), user.getUsername(), "LOGIN", ip, ua,
                "FAIL", "账号已禁用");
            throw new BusinessException(401, "用户名或密码错误");
        }

        // Clear attempts on success
        attemptService.recordSuccess(loginReq.username());

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;

        String accessToken = jwtTokenService.generateAccessToken(
            user.getUserId(), user.getUsername(), roles, version);
        String refreshToken = jwtTokenService.generateRefreshToken(user.getUserId(), version);

        // update last login
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userMapper.updateById(user);

        logService.record(user.getUserId(), user.getUsername(), "LOGIN", ip, ua,
            "SUCCESS", null);

        return LoginVO.of(accessToken, jwtTokenService.getAccessExpirationSeconds(),
            refreshToken, jwtTokenService.getRefreshExpirationSeconds(),
            user.getUserId(), user.getUsername(), user.getNickname(), roles);
    }

    public RefreshVO refresh(String refreshTokenValue) {
        TokenClaims claims = jwtTokenService.parseToken(refreshTokenValue, "refresh");

        SysUserDO user = userMapper.selectById(claims.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        // check token version
        int userVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        int claimVersion = claims.tokenVersion() != null ? claims.tokenVersion() : 0;
        if (claimVersion != userVersion) {
            throw new BusinessException(401, "登录状态已变更，请重新登录");
        }

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());

        // Issue new access + rotated refresh tokens
        String newAccess = jwtTokenService.generateAccessToken(
            user.getUserId(), user.getUsername(), roles, userVersion);
        String newRefresh = jwtTokenService.generateRefreshToken(user.getUserId(), userVersion);

        logService.record(user.getUserId(), user.getUsername(), "REFRESH",
            getClientIp(), getUserAgent(), "SUCCESS", null);

        return new RefreshVO(newAccess, jwtTokenService.getAccessExpirationSeconds(),
            newRefresh, jwtTokenService.getRefreshExpirationSeconds());
    }

    public void logout() {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(version + 1);
        userMapper.updateById(user);

        logService.record(user.getUserId(), user.getUsername(), "LOGOUT",
            getClientIp(), getUserAgent(), "SUCCESS", null);
    }

    // ---- email verification code ----

    public void sendCode(String email) {
        String code = codeService.generate(email);
        emailService.send(email, "光伏平台 - 验证码",
            "您的验证码是: " + code + "\n有效期" + codeService.getExpireMinutes() + "分钟。");
    }

    @Transactional
    public LoginVO emailCodeLogin(String email, String code) {
        codeService.verify(email, code);
        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getEmail, email).eq(SysUserDO::getStatus, 1).last("LIMIT 1"));
        if (user == null) throw new BusinessException(401, "该邮箱未注册");
        return issueTokens(user, "EMAIL_CODE_LOGIN");
    }

    @Transactional
    public LoginVO emailCodeRegister(String username, String email, String code) {
        codeService.verify(email, code);
        if (userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery().eq(SysUserDO::getUsername, username)) > 0)
            throw new BusinessException(400, "用户名已存在");
        if (userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery().eq(SysUserDO::getEmail, email)) > 0)
            throw new BusinessException(400, "邮箱已被注册");

        SysRoleDO role = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, "USER").eq(SysRoleDO::getStatus, 1).last("LIMIT 1"));
        if (role == null) throw new BusinessException(500, "系统角色配置异常");

        SysUserDO user = new SysUserDO();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(username);
        user.setEmail(email);
        user.setStatus(1);
        user.setTokenVersion(0);
        userMapper.insert(user);

        SysUserRoleDO ur = new SysUserRoleDO();
        ur.setUserId(user.getUserId()); ur.setRoleId(role.getRoleId());
        userRoleMapper.insert(ur);

        logService.record(user.getUserId(), user.getUsername(), "REGISTER",
            getClientIp(), getUserAgent(), "SUCCESS", "via email code");
        return issueTokens(user, "EMAIL_CODE_LOGIN");
    }

    private LoginVO issueTokens(SysUserDO user, String loginType) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        String access = jwtTokenService.generateAccessToken(
            user.getUserId(), user.getUsername(), roles, version);
        String refresh = jwtTokenService.generateRefreshToken(user.getUserId(), version);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(getClientIp());
        userMapper.updateById(user);
        logService.record(user.getUserId(), user.getUsername(), loginType,
            getClientIp(), getUserAgent(), "SUCCESS", null);
        return LoginVO.of(access, jwtTokenService.getAccessExpirationSeconds(),
            refresh, jwtTokenService.getRefreshExpirationSeconds(),
            user.getUserId(), user.getUsername(), user.getNickname(), roles);
    }

    public void forgotPassword(String email) {
        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getEmail, email)
            .eq(SysUserDO::getStatus, 1)
            .last("LIMIT 1"));

        if (user == null) {
            // Return silently to prevent email enumeration
            return;
        }

        String code = codeService.generate(email);
        emailService.send(email, "光伏平台 - 密码重置验证码",
            "您的验证码是: " + code + "\n有效期" + codeService.getExpireMinutes() + "分钟，请勿泄露给他人。");
        logService.record(user.getUserId(), user.getUsername(), "PWD_RESET",
            getClientIp(), getUserAgent(), "SUCCESS", "验证码已发送");
    }

    public void resetPassword(String email, String code, String newPassword) {
        codeService.verify(email, code);

        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getEmail, email)
            .eq(SysUserDO::getStatus, 1)
            .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(400, "用户不存在或已被禁用");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Bump token version to invalidate all outstanding tokens
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(version + 1);
        userMapper.updateById(user);

        logService.record(user.getUserId(), user.getUsername(), "PWD_RESET",
            getClientIp(), getUserAgent(), "SUCCESS", "密码重置成功");
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String getUserAgent() {
        return request.getHeader("User-Agent");
    }
}
