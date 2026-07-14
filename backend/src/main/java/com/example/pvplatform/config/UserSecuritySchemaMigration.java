package com.example.pvplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds the explicit email verification state without touching existing email values. */
@Component
@ConditionalOnProperty(prefix = "database.migration.user-security", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class UserSecuritySchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UserSecuritySchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public UserSecuritySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!exists("TABLES", "TABLE_NAME", "sys_user")) {
            log.warn("Skip user security migration because sys_user does not exist; initialize the database first");
            return;
        }
        if (!exists("COLUMNS", "COLUMN_NAME", "email_verified")) {
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 "
                + "COMMENT '邮箱是否已完成验证码验证：1是，0否' AFTER email");
            log.info("Added sys_user.email_verified");
        }
    }

    private boolean exists(String metadataTable, String field, String value) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema." + metadataTable
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND " + field + " = ?",
            Integer.class, value);
        return count != null && count > 0;
    }
}
