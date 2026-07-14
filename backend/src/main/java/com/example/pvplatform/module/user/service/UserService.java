package com.example.pvplatform.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.EmailService;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.auth.service.VerificationCodeService;
import com.example.pvplatform.module.user.dto.CancelAccountRequest;
import com.example.pvplatform.module.user.dto.ChangePasswordRequest;
import com.example.pvplatform.module.user.dto.ConfirmEmailChangeRequest;
import com.example.pvplatform.module.user.dto.SendEmailChangeCodeRequest;
import com.example.pvplatform.module.user.dto.UpdateProfileRequest;
import com.example.pvplatform.module.user.vo.UserProfileVO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService codeService;
    private final EmailService emailService;

    public UserService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder,
                       VerificationCodeService codeService, EmailService emailService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.codeService = codeService;
        this.emailService = emailService;
    }

    public UserProfileVO profile() {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        return toVO(user, roles);
    }

    public UserProfileVO updateProfile(UpdateProfileRequest req) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (req.nickname() != null && !req.nickname().isBlank()) {
            user.setNickname(req.nickname());
        }
        if (req.phone() != null) {
            String normalizedPhone = ContactPhoneNormalizer.normalize(req.phone());
            if (normalizedPhone != null) {
                Long count = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                    .eq(SysUserDO::getPhone, normalizedPhone)
                    .ne(SysUserDO::getUserId, userId));
                if (count > 0) {
                    throw new BusinessException(400, "手机号已被使用");
                }
            }
            user.setPhone(normalizedPhone);
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        if (req.gender() != null) {
            if (req.gender() < 0 || req.gender() > 2) {
                throw new BusinessException(400, "性别参数不合法");
            }
            user.setGender(req.gender());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        SysUserDO saved = userMapper.selectById(userId);
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        return toVO(saved, roles);
    }

    public void sendEmailChangeCode(SendEmailChangeCodeRequest req) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = requireUser(userId);
        verifyCurrentPassword(user, req.currentPassword());
        String newEmail = normalizeEmail(req.newEmail());
        validateNewEmail(userId, user.getEmail(), newEmail);

        String verificationKey = emailChangeKey(userId, newEmail);
        String code = codeService.generate(verificationKey);
        log.info("Email-change code stored: purpose=email-change, userId={}, email={}, keyDigest={}, cacheHit={}, codeLength={}",
            userId, maskEmail(newEmail), keyDigest(verificationKey),
            codeService.hasPendingCode(verificationKey), code.length());
        emailService.send(newEmail, "光伏平台 - 更换邮箱验证码",
            "您正在更换账户邮箱，验证码是: " + code + "\n有效期"
                + codeService.getExpireMinutes() + "分钟。如非本人操作，请忽略本邮件。");
    }

    @Transactional
    public UserProfileVO confirmEmailChange(ConfirmEmailChangeRequest req) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = requireUser(userId);
        verifyCurrentPassword(user, req.currentPassword());
        String newEmail = normalizeEmail(req.newEmail());
        validateNewEmail(userId, user.getEmail(), newEmail);
        String verificationKey = emailChangeKey(userId, newEmail);
        String inputCode = req.code() == null ? "" : req.code().trim();
        boolean cacheHit = codeService.hasPendingCode(verificationKey);
        log.info("Email-change code verify: purpose=email-change, userId={}, email={}, keyDigest={}, cacheHit={}, codeLength={}",
            userId, maskEmail(newEmail), keyDigest(verificationKey), cacheHit, inputCode.length());
        try {
            codeService.verify(verificationKey, inputCode);
            log.info("Email-change code verified: purpose=email-change, userId={}, keyDigest={}",
                userId, keyDigest(verificationKey));
        } catch (BusinessException exception) {
            log.warn("Email-change code rejected: purpose=email-change, userId={}, keyDigest={}, cacheHit={}, reason={}",
                userId, keyDigest(verificationKey), cacheHit, exception.getMessage());
            throw exception;
        }

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(userMapper.selectById(userId), roleMapper.selectRoleCodesByUserId(userId));
    }

    public void changePassword(ChangePasswordRequest req) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "旧密码错误");
        }

        if (req.oldPassword().equals(req.newPassword())) {
            throw new BusinessException(400, "新旧密码不能相同");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // Bump token version to invalidate all outstanding tokens
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(version + 1);
        userMapper.updateById(user);
    }

    public void updateAvatarUrl(String url) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setAvatarUrl(url);
        userMapper.updateById(user);
    }

    @Transactional
    public void cancelAccount(CancelAccountRequest req) {
        Long userId = SecurityUtils.requireCurrentUserId();
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(400, "密码错误");
        }

        // Bump token version and logically delete
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(version + 1);
        userMapper.updateById(user);
        userRoleMapper.deleteByUserId(userId);
        userMapper.deleteById(userId);
    }

    private UserProfileVO toVO(SysUserDO user, List<String> roles) {
        return new UserProfileVO(
            user.getUserId(),
            user.getUsername(),
            user.getNickname(),
            user.getEmail(),
            Boolean.TRUE.equals(user.getEmailVerified()),
            user.getPhone(),
            false,
            user.getAvatarUrl(),
            user.getGender(),
            user.getStatus(),
            roles,
            user.getCreatedAt() != null
                ? user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null
        );
    }

    private SysUserDO requireUser(Long userId) {
        SysUserDO user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(404, "用户不存在");
        return user;
    }

    private void verifyCurrentPassword(SysUserDO user, String currentPassword) {
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException(400, "当前账号未设置本地密码，暂不能通过密码换绑邮箱");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "当前密码错误");
        }
    }

    private void validateNewEmail(Long userId, String currentEmail, String newEmail) {
        if (currentEmail != null && currentEmail.equalsIgnoreCase(newEmail)) {
            throw new BusinessException(400, "新邮箱不能与当前邮箱相同");
        }
        Long count = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getEmail, newEmail)
            .ne(SysUserDO::getUserId, userId));
        if (count > 0) throw new BusinessException(400, "邮箱已被使用");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String emailChangeKey(Long userId, String email) {
        return "email-change:" + userId + ":" + email;
    }

    private String keyDigest(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception exception) {
            throw new IllegalStateException("Verification key digest failed", exception);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
