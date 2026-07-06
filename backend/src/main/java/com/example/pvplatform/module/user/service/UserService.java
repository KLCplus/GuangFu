package com.example.pvplatform.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.user.dto.CancelAccountRequest;
import com.example.pvplatform.module.user.dto.ChangePasswordRequest;
import com.example.pvplatform.module.user.dto.UpdateProfileRequest;
import com.example.pvplatform.module.user.vo.UserProfileVO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
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
        if (req.email() != null) {
            if (!req.email().isBlank()) {
                Long count = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                    .eq(SysUserDO::getEmail, req.email())
                    .ne(SysUserDO::getUserId, userId));
                if (count > 0) {
                    throw new BusinessException(400, "邮箱已被使用");
                }
            }
            user.setEmail(req.email().isBlank() ? null : req.email());
        }
        if (req.phone() != null) {
            if (!req.phone().isBlank()) {
                Long count = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                    .eq(SysUserDO::getPhone, req.phone())
                    .ne(SysUserDO::getUserId, userId));
                if (count > 0) {
                    throw new BusinessException(400, "手机号已被使用");
                }
            }
            user.setPhone(req.phone().isBlank() ? null : req.phone());
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

        userMapper.updateById(user);
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        return toVO(user, roles);
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
            maskPhone(user.getPhone()),
            user.getAvatarUrl(),
            user.getGender(),
            user.getStatus(),
            roles,
            user.getCreatedAt() != null
                ? user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
