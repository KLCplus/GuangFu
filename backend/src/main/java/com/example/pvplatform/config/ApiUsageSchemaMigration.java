package com.example.pvplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps existing installations compatible with the API usage statistics code.
 * init.sql is intentionally destructive and must not be run on application startup,
 * so this runner applies only additive, idempotent changes to api_call_log.
 */
@Component
@ConditionalOnProperty(prefix = "database.migration.api-usage", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class ApiUsageSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ApiUsageSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public ApiUsageSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!exists("TABLES", "TABLE_NAME", "api_call_log")) {
            log.warn("Skip API usage migration because api_call_log does not exist; initialize the database first");
            return;
        }
        addColumnIfMissing("input_tokens", "BIGINT DEFAULT NULL COMMENT '输入Token数'");
        addColumnIfMissing("output_tokens", "BIGINT DEFAULT NULL COMMENT '输出Token数'");
        addColumnIfMissing("total_tokens", "BIGINT DEFAULT NULL COMMENT '总Token数'");
        addIndexIfMissing("idx_call_user_time", "user_id, request_time");
        addIndexIfMissing("idx_call_user_model", "user_id, model_id");
        addIndexIfMissing("idx_call_user_key", "user_id, api_key_id");
    }

    private void addColumnIfMissing(String name, String definition) {
        if (exists("COLUMNS", "COLUMN_NAME", name)) return;
        jdbcTemplate.execute("ALTER TABLE api_call_log ADD COLUMN " + name + " " + definition);
        log.info("Added api_call_log.{}", name);
    }

    private void addIndexIfMissing(String name, String columns) {
        if (exists("STATISTICS", "INDEX_NAME", name)) return;
        jdbcTemplate.execute("ALTER TABLE api_call_log ADD INDEX " + name + " (" + columns + ")");
        log.info("Added api_call_log index {}", name);
    }

    private boolean exists(String metadataTable, String field, String value) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema." + metadataTable
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND " + field + " = ?",
            Integer.class, value);
        return count != null && count > 0;
    }
}
