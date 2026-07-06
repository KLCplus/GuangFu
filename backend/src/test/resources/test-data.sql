-- Seed roles only; users are created programmatically in tests
-- Use MERGE to be idempotent across multiple context initializations
MERGE INTO sys_role (role_code, role_name, status) KEY (role_code) VALUES ('ADMIN', '管理员', 1);
MERGE INTO sys_role (role_code, role_name, status) KEY (role_code) VALUES ('USER', '普通用户', 1);
MERGE INTO sys_role (role_code, role_name, status) KEY (role_code) VALUES ('API_USER', '开放API用户', 1);
