# news 模块实现现状

## 模块职责

`news` 负责新闻/公告内容管理和站内通知读取状态管理。

## 已实现

- 前台新闻分页列表、按类型筛选、新闻详情。
- 管理端新闻分页列表、按状态/类型筛选、创建、更新、发布、下线、删除。
- 新闻状态包括草稿、已发布、下线；前台只展示已发布内容。
- 发布新闻时会写发布时间和发布人。
- 发布后可根据目标角色创建站内通知。
- 站内通知支持分页列表、按已读状态筛选、未读数、标记单条已读、全部已读。
- 普通用户通知查询只作用于当前登录用户。

## 未实现或限制

- 新闻封面文件上传未在本模块实现，只保存 `coverUrl`。
- 富文本内容没有服务端清洗或 XSS 处理。
- 发布流程没有审核、定时发布、撤回原因和版本历史。
- 通知创建目前依赖新闻发布流程或服务内部调用，没有独立后台通知发送接口。
- 目标角色通知依赖用户角色查询，缺少复杂人群筛选。

## 主要接口

- `GET /api/news`
- `GET /api/news/{newsId}`
- `GET /api/admin/news`
- `POST /api/admin/news`
- `PUT /api/admin/news/{newsId}`
- `PUT /api/admin/news/{newsId}/publish`
- `PUT /api/admin/news/{newsId}/offline`
- `DELETE /api/admin/news/{newsId}`
- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{notificationId}/read`
- `PUT /api/notifications/read-all`

## 相关表

- `news`
- `user_notification`
- 通知目标角色依赖 `sys_user`、`sys_user_role`、`sys_role`。

## 测试情况

- 已有 `PhaseFourServiceTest` 间接覆盖部分新闻和通知服务。
- 建议补充新闻发布通知生成、不同目标角色过滤、已读幂等、前台不可见草稿/下线新闻、富文本安全测试。
