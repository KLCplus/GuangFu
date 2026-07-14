package com.example.pvplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive migration for installations created before OSS-backed files existed. */
@Component
@ConditionalOnProperty(prefix = "database.migration.oss-news", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OssNewsSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OssNewsSchemaMigration.class);
    private final JdbcTemplate jdbc;
    public OssNewsSchemaMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void run(ApplicationArguments args) {
        if (!table("file_resource") || !table("sys_user") || !table("news")) { log.warn("Skip OSS/news migration: initialize base schema first"); return; }
        column("file_resource", "object_key", "VARCHAR(500) DEFAULT NULL");
        column("file_resource", "content_type", "VARCHAR(100) DEFAULT NULL");
        column("file_resource", "file_status", "VARCHAR(20) NOT NULL DEFAULT 'BOUND'");
        column("file_resource", "biz_id", "BIGINT DEFAULT NULL");
        column("file_resource", "updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        index("file_resource", "uk_file_object_key", "object_key");
        index("file_resource", "idx_file_biz", "business_type, biz_id");
        column("sys_user", "avatar_file_id", "BIGINT DEFAULT NULL");
        index("sys_user", "idx_user_avatar_file", "avatar_file_id");
        column("news", "cover_file_id", "BIGINT DEFAULT NULL");
        column("news", "category", "VARCHAR(32) DEFAULT NULL");
        column("news", "content_type", "VARCHAR(32) DEFAULT NULL");
        column("news", "source_type", "VARCHAR(32) DEFAULT NULL");
        column("news", "source_name", "VARCHAR(128) DEFAULT NULL");
        column("news", "source_url", "VARCHAR(1000) DEFAULT NULL");
        column("news", "external_id", "VARCHAR(255) DEFAULT NULL");
        column("news", "source_published_at", "DATETIME DEFAULT NULL");
        column("news", "fetched_at", "DATETIME DEFAULT NULL");
        column("news", "external_content", "TINYINT NOT NULL DEFAULT 0");
        column("news", "warning_level", "VARCHAR(64) DEFAULT NULL");
        column("news", "warning_region", "VARCHAR(255) DEFAULT NULL");
        column("news", "warning_agency", "VARCHAR(255) DEFAULT NULL");
        column("news", "effective_at", "DATETIME DEFAULT NULL");
        column("news", "expires_at", "DATETIME DEFAULT NULL");
        index("news", "idx_news_status_publish", "status, published_at");
        index("news", "idx_news_category_publish", "category, status, published_at");
        unique("news", "uk_news_external", "source_type, external_id");
        jdbc.update("UPDATE news SET category = CASE WHEN news_type = 'INDUSTRY_NEWS' THEN 'INDUSTRY' ELSE 'PLATFORM' END WHERE category IS NULL");
        jdbc.update("UPDATE news SET content_type = CASE WHEN news_type = 'NOTICE' THEN 'PLATFORM_NOTICE' ELSE 'PLATFORM_NEWS' END, source_type = 'PLATFORM', source_name = '光伏智云平台' WHERE source_type IS NULL");
    }
    private void column(String table, String column, String definition) { if (!exists("COLUMNS", "COLUMN_NAME", table, column)) { jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition); log.info("Added {}.{}", table, column); } }
    private void index(String table, String name, String columns) { if (!exists("STATISTICS", "INDEX_NAME", table, name)) jdbc.execute("ALTER TABLE " + table + " ADD INDEX " + name + " (" + columns + ")"); }
    private void unique(String table, String name, String columns) { if (!exists("STATISTICS", "INDEX_NAME", table, name)) jdbc.execute("ALTER TABLE " + table + " ADD UNIQUE INDEX " + name + " (" + columns + ")"); }
    private boolean table(String name) { return exists("TABLES", "TABLE_NAME", name, name); }
    private boolean exists(String meta, String field, String table, String value) { Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema." + meta + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND " + field + " = ?", Integer.class, table, value); return n != null && n > 0; }
}
