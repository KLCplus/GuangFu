package com.example.pvplatform.module.role.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.role.dto.CreateRoleRequest;
import com.example.pvplatform.module.role.dto.UpdateRoleRequest;
import com.example.pvplatform.module.role.vo.RoleVO;
import com.example.pvplatform.persistence.entity.SysRoleDO;
import com.example.pvplatform.persistence.entity.SysUserRoleDO;
import com.example.pvplatform.persistence.mapper.SysRoleMapper;
import com.example.pvplatform.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {
    private static final Set<String> BUILT_IN_ROLES = Set.of("ADMIN", "USER", "API_USER");

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public RoleService(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public List<RoleVO> listRoles() {
        List<SysRoleDO> roles = roleMapper.selectList(
            Wrappers.<SysRoleDO>lambdaQuery().orderByAsc(SysRoleDO::getRoleId));

        return roles.stream().map(role -> {
            long userCount = userRoleMapper.selectCount(
                Wrappers.<SysUserRoleDO>lambdaQuery()
                    .eq(SysUserRoleDO::getRoleId, role.getRoleId()));
            return toVO(role, userCount);
        }).toList();
    }

    @Transactional
    public RoleVO createRole(CreateRoleRequest req) {
        // validate roleCode format: uppercase, no spaces
        if (!req.roleCode().matches("^[A-Z][A-Z0-9_]*$")) {
            throw new BusinessException(400, "角色编码必须为大写字母开头，仅含大写字母、数字和下划线");
        }

        long count = roleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
            .eq(SysRoleDO::getRoleCode, req.roleCode()));
        if (count > 0) {
            throw new BusinessException(400, "角色编码已存在");
        }

        SysRoleDO role = new SysRoleDO();
        role.setRoleCode(req.roleCode());
        role.setRoleName(req.roleName());
        role.setDescription(req.description());
        role.setStatus(1);
        roleMapper.insert(role);

        return toVO(role, 0);
    }

    public RoleVO updateRole(Long roleId, UpdateRoleRequest req) {
        SysRoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        if (req.roleName() != null && !req.roleName().isBlank()) {
            role.setRoleName(req.roleName());
        }
        if (req.description() != null) {
            role.setDescription(req.description().isBlank() ? null : req.description());
        }
        roleMapper.updateById(role);

        long userCount = userRoleMapper.selectCount(
            Wrappers.<SysUserRoleDO>lambdaQuery()
                .eq(SysUserRoleDO::getRoleId, roleId));
        return toVO(role, userCount);
    }

    public void updateStatus(Long roleId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(400, "状态值只能为 0 或 1");
        }

        SysRoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        if (status == 0 && BUILT_IN_ROLES.contains(role.getRoleCode())) {
            throw new BusinessException(400, "不能禁用内置角色: " + role.getRoleCode());
        }

        role.setStatus(status);
        roleMapper.updateById(role);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        SysRoleDO role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        if (BUILT_IN_ROLES.contains(role.getRoleCode())) {
            throw new BusinessException(400, "不能删除内置角色: " + role.getRoleCode());
        }

        long userCount = userRoleMapper.selectCount(
            Wrappers.<SysUserRoleDO>lambdaQuery()
                .eq(SysUserRoleDO::getRoleId, roleId));
        if (userCount > 0) {
            throw new BusinessException(400, "该角色已分配给 " + userCount + " 个用户，不能删除");
        }

        // Remove role and its associations
        userRoleMapper.delete(Wrappers.<SysUserRoleDO>lambdaQuery()
            .eq(SysUserRoleDO::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    private RoleVO toVO(SysRoleDO role, long userCount) {
        return new RoleVO(
            role.getRoleId(),
            role.getRoleCode(),
            role.getRoleName(),
            role.getDescription(),
            role.getStatus(),
            userCount,
            role.getCreatedAt() != null
                ? role.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null
        );
    }
}
