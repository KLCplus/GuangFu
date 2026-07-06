package com.example.pvplatform.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.user.entity.User;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public UserService(SysUserMapper userMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    public User profile() {
        // JWT 接入前临时返回第一位有效用户，之后应从 SecurityContext 获取 userId。
        SysUserDO user = userMapper.selectOne(Wrappers.<SysUserDO>lambdaQuery()
            .eq(SysUserDO::getStatus, 1)
            .orderByAsc(SysUserDO::getUserId)
            .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(404, "数据库中暂无用户，请先注册");
        }
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getUserId());
        return new User(user.getUserId(), user.getUsername(), user.getEmail(),
            roles.isEmpty() ? "USER" : roles.get(0), user.getStatus() == 1 ? "ENABLE" : "DISABLE");
    }
}
