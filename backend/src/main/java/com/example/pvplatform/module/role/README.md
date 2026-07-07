# role 模块实现现状

## 模块职责

`role` 负责后台角色管理，为用户授权模块提供角色数据基础。

## 已实现

- 管理端角色列表、创建、更新、启停、删除。
- 角色编码唯一性校验。
- 删除角色前检查是否仍有用户绑定，避免误删已使用角色。
- 使用 `sys_role` 和 `sys_user_role` 落库。
- 接口统一挂在 `/api/admin` 下，受 `ADMIN` 权限保护。

## 未实现或限制

- 当前角色只是一组角色编码，没有细粒度菜单、按钮、资源权限表。
- 不能删除已绑定用户的角色，但没有提供角色解绑/迁移辅助流程。
- 没有角色分页，当前直接返回全部角色。
- 缺少专门的单元测试或集成测试。

## 主要接口

- `GET /api/admin/roles`
- `POST /api/admin/roles`
- `PUT /api/admin/roles/{roleId}`
- `PUT /api/admin/roles/{roleId}/status`
- `DELETE /api/admin/roles/{roleId}`

## 相关表

- `sys_role`
- `sys_user_role`

## 测试情况

- 当前未看到 `role` 模块专门测试。
- 建议补充创建重复角色、禁用角色、删除已绑定角色、非管理员访问 403 的测试。
