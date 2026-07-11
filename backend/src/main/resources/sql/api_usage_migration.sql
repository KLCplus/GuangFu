-- API 使用统计增量迁移（可对已有数据库重复执行）
-- 应用启动时 ApiUsageSchemaMigration 会执行同样的字段/索引检查；
-- 本脚本保留给部署或 DBA 手工迁移，绝不删除或重建业务表。

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_api_usage_schema$$
CREATE PROCEDURE migrate_api_usage_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND COLUMN_NAME = 'input_tokens'
    ) THEN
        ALTER TABLE api_call_log ADD COLUMN input_tokens BIGINT DEFAULT NULL COMMENT '输入Token数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND COLUMN_NAME = 'output_tokens'
    ) THEN
        ALTER TABLE api_call_log ADD COLUMN output_tokens BIGINT DEFAULT NULL COMMENT '输出Token数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND COLUMN_NAME = 'total_tokens'
    ) THEN
        ALTER TABLE api_call_log ADD COLUMN total_tokens BIGINT DEFAULT NULL COMMENT '总Token数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND INDEX_NAME = 'idx_call_user_time'
    ) THEN
        ALTER TABLE api_call_log ADD INDEX idx_call_user_time (user_id, request_time);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND INDEX_NAME = 'idx_call_user_model'
    ) THEN
        ALTER TABLE api_call_log ADD INDEX idx_call_user_model (user_id, model_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND INDEX_NAME = 'idx_call_user_key'
    ) THEN
        ALTER TABLE api_call_log ADD INDEX idx_call_user_key (user_id, api_key_id);
    END IF;
END$$

CALL migrate_api_usage_schema()$$
DROP PROCEDURE migrate_api_usage_schema$$

DELIMITER ;
