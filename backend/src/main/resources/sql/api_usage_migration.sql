-- =========================================================
-- API 使用统计与 Token 字段迁移
-- 在 api_call_log 表中新增 Token 相关字段
-- 执行前请确认数据库已备份
-- =========================================================

-- 新增 Token 字段
ALTER TABLE api_call_log
    ADD COLUMN input_tokens BIGINT DEFAULT NULL COMMENT '输入Token数' AFTER response_summary,
    ADD COLUMN output_tokens BIGINT DEFAULT NULL COMMENT '输出Token数' AFTER input_tokens,
    ADD COLUMN total_tokens BIGINT DEFAULT NULL COMMENT '总Token数' AFTER output_tokens;

-- 为统计查询添加复合索引
ALTER TABLE api_call_log
    ADD KEY idx_call_user_time (user_id, request_time),
    ADD KEY idx_call_user_model (user_id, model_id),
    ADD KEY idx_call_user_key (user_id, api_key_id);
