package com.example.pvplatform.module.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.user.dto.UpdateUserRolesRequest;
import com.example.pvplatform.module.user.vo.UserListItemVO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import com.example.pvplatform.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public AdminUserService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                            SysUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public PageResult<UserListItemVO> listUsers(int pageNum, int pageSize,
                                                 String keyword, Integer status, String role) {
        Page<SysUserDO> page = new Page<>(pageNum, pageSize);
        IPage<SysUserDO> result = userMapper.selectUserPage(page, keyword, status, role);

        List<Long> userIds = result.getRecords().stream()
            .map(SysUserDO::getUserId).toList();

        // batch query roles for all users
        Map<Long, List<String>> rolesMap = batchQueryRoles(userIds);

        List<UserListItemVO> records = result.getRecords().stream()
            .map(u -> toListItemVO(u, rolesMap.getOrDefault(u.getUserId(), List.of())))
            .toList();

        return new PageResult<>(result.getTotal(), pageNum, pageSize, records);
    }

    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        Long currentUserId = SecurityUtils.requireCurrentUserId();
        if (currentUserId.equals(userId)) {
            throw new BusinessException(400, "管理员不能禁用自己");
        }

        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // if disabling the last active admin, prevent it
        if (status == 0) {
            List<String> targetRoles = roleMapper.selectRoleCodesByUserId(userId);
            if (targetRoles.contains("ADMIN")) {
                long activeAdminCount = countActiveAdmins();
                if (activeAdminCount <= 1) {
                    throw new BusinessException(400, "不能禁用最后一个有效管理员");
                }
            }
        }

        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Transactional
    public void updateUserRoles(Long userId, UpdateUserRolesRequest req) {
        Long currentUserId = SecurityUtils.requireCurrentUserId();

        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // validate all role codes exist and are enabled
        List<String> uniqueRoles = req.roles().stream().distinct().toList();
        if (uniqueRoles.isEmpty()) {
            throw new BusinessException(400, "至少保留一个角色");
        }

        List<SysRoleDO> roles = roleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
            .in(SysRoleDO::getRoleCode, uniqueRoles)
            .eq(SysRoleDO::getStatus, 1));
        if (roles.size() != uniqueRoles.size()) {
            throw new BusinessException(400, "存在无效的角色编码");
        }

        // admin self-protection: cannot remove own last ADMIN role
        if (currentUserId.equals(userId)) {
            boolean willKeepAdmin = uniqueRoles.contains("ADMIN");
            if (!willKeepAdmin) {
                throw new BusinessException(400, "管理员不能移除自己的ADMIN角色");
            }
        }

        // delete old, insert new in transaction
        userRoleMapper.deleteByUserId(userId);

        for (SysRoleDO role : roles) {
            SysUserRoleDO relation = new SysUserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(role.getRoleId());
            userRoleMapper.insert(relation);
        }
    }

    private Map<Long, List<String>> batchQueryRoles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRoleDO> relations = userRoleMapper.selectList(
            Wrappers.<SysUserRoleDO>lambdaQuery().in(SysUserRoleDO::getUserId, userIds));
        if (relations.isEmpty()) {
            return userIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
        }
        List<Long> roleIds = relations.stream().map(SysUserRoleDO::getRoleId).distinct().toList();
        List<SysRoleDO> roles = roleMapper.selectList(
            Wrappers.<SysRoleDO>lambdaQuery()
                .in(SysRoleDO::getRoleId, roleIds)
                .eq(SysRoleDO::getStatus, 1));
        Map<Long, String> roleIdToCode = roles.stream()
            .collect(Collectors.toMap(SysRoleDO::getRoleId, SysRoleDO::getRoleCode));

        return relations.stream()
            .collect(Collectors.groupingBy(SysUserRoleDO::getUserId,
                Collectors.mapping(r -> roleIdToCode.getOrDefault(r.getRoleId(), ""),
                    Collectors.filtering(s -> !s.isEmpty(), Collectors.toList()))));
    }

    @Transactional
    public void deleteUser(Long userId) {
        Long currentUserId = SecurityUtils.requireCurrentUserId();
        if (currentUserId.equals(userId)) {
            throw new BusinessException(400, "管理员不能删除自己");
        }

        SysUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // prevent deleting the last active admin
        List<String> targetRoles = roleMapper.selectRoleCodesByUserId(userId);
        if (targetRoles.contains("ADMIN")) {
            long activeAdminCount = countActiveAdmins();
            if (activeAdminCount <= 1) {
                throw new BusinessException(400, "不能删除最后一个管理员");
            }
        }

        // Bump token version to invalidate outstanding tokens
        int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(version + 1);
        userMapper.updateById(user);

        // Clear user-role relations and logically delete
        userRoleMapper.deleteByUserId(userId);
        userMapper.deleteById(userId);
    }

    private long countActiveAdmins() {
        SysRoleDO adminRole = roleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, "ADMIN")
            .eq(SysRoleDO::getStatus, 1)
            .last("LIMIT 1"));
        if (adminRole == null) {
            return 0;
        }
        return userRoleMapper.selectCount(Wrappers.<SysUserRoleDO>lambdaQuery()
            .eq(SysUserRoleDO::getRoleId, adminRole.getRoleId()));
    }

    private UserListItemVO toListItemVO(SysUserDO user, List<String> roles) {
        return new UserListItemVO(
            user.getUserId(),
            user.getUsername(),
            user.getNickname(),
            user.getEmail(),
            maskPhone(user.getPhone()),
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
