package com.example.pvplatform.module.role.controller;

import com.example.pvplatform.common.Result;
import com.example.pvplatform.module.role.dto.CreateRoleRequest;
import com.example.pvplatform.module.role.dto.UpdateRoleRequest;
import com.example.pvplatform.module.role.dto.UpdateRoleStatusRequest;
import com.example.pvplatform.module.role.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminRoleController {
    private final RoleService roleService;

    public AdminRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    public Result<?> listRoles() {
        return Result.success(roleService.listRoles());
    }

    @PostMapping("/roles")
    public Result<?> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(roleService.createRole(request));
    }

    @PutMapping("/roles/{roleId}")
    public Result<?> updateRole(@PathVariable Long roleId,
                                @Valid @RequestBody UpdateRoleRequest request) {
        return Result.success(roleService.updateRole(roleId, request));
    }

    @PutMapping("/roles/{roleId}/status")
    public Result<?> updateRoleStatus(@PathVariable Long roleId,
                                      @Valid @RequestBody UpdateRoleStatusRequest request) {
        roleService.updateStatus(roleId, request.status());
        return Result.success();
    }

    @DeleteMapping("/roles/{roleId}")
    public Result<?> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return Result.success();
    }
}
