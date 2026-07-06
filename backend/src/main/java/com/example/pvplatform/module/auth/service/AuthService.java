package com.example.pvplatform.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.auth.dto.LoginRequest;
import com.example.pvplatform.module.auth.dto.RegisterRequest;
import com.example.pvplatform.module.auth.vo.LoginVO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        Long count = userMapper.selectCount(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getUsername, request.username()));
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        SysUserDO user = new SysUserDO();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setStatus(1);
        userMapper.insert(user);

        SysRoleDO role = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, "USER").last("LIMIT 1"));
        if (role != null) {
            SysUserRoleDO relation = new SysUserRoleDO();
            relation.setUserId(user.getUserId());
            relation.setRoleId(role.getRoleId());
            userRoleMapper.insert(relation);
        }
        return Map.of("userId", user.getUserId(), "username", user.getUsername());
    }

    public LoginVO login(LoginRequest request) {
        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getUsername, request.username()).last("LIMIT 1"));
        if (user == null || user.getStatus() == 0 || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
        String role = roles.isEmpty() ? "USER" : roles.get(0);
        return new LoginVO(
            "mock-jwt-token-" + user.getUserId(),
            Map.of("userId", user.getUserId(), "username", user.getUsername(), "role", role)
        );
    }
}
