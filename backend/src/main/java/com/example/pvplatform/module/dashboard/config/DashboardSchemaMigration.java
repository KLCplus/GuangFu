package com.example.pvplatform.module.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DashboardSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DashboardSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DashboardSchemaMigration(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        if (isH2()) {
            createH2Table();
            return;
        }
        createMysqlTable();
        sanitizeSeedStationText();
        importExternalStations();
        addPvDataUniqueIndex();
    }

    private boolean isH2() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        }
    }

    private void createMysqlTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS station_runtime_config (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                station_id BIGINT NOT NULL,
                battery_capacity_kwh DECIMAL(12,3) DEFAULT NULL,
                nominal_voltage_v DECIMAL(10,2) NOT NULL DEFAULT 380.00,
                phase_type VARCHAR(16) NOT NULL DEFAULT 'THREE_PHASE',
                performance_ratio DECIMAL(5,3) NOT NULL DEFAULT 0.840,
                power_factor DECIMAL(5,3) NOT NULL DEFAULT 0.960,
                base_load_kw DECIMAL(12,3) NOT NULL DEFAULT 10.000,
                load_peak_factor DECIMAL(6,3) NOT NULL DEFAULT 1.500,
                station_type VARCHAR(32) NOT NULL DEFAULT 'COMMERCIAL',
                timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_station_runtime_config_station (station_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }


    private void sanitizeSeedStationText() {
        try {
            jdbcTemplate.update("""
                UPDATE power_station
                SET address = REPLACE(address, '本地联调用 mock 数据电站', '本地联调示范电站'),
                    description = REPLACE(description, 'mock', '联调')
                WHERE address LIKE '%mock%' OR description LIKE '%mock%'
                """);
        } catch (RuntimeException exception) {
            log.warn("Skip station seed text cleanup: {}", exception.getMessage());
        }
    }


    private void importExternalStations() {
        try {
            jdbcTemplate.execute("""
                INSERT IGNORE INTO power_station
                    (station_code, station_name, province, city, address, longitude, latitude,
                     capacity_kw, status, description)
                SELECT CONCAT('PVOUTPUT-', external_system_id),
                       COALESCE(NULLIF(system_name, ''), CONCAT('PVOutput ', external_system_id)),
                       'PVOutput',
                       COALESCE(NULLIF(postcode, ''), '公开电站'),
                       COALESCE(NULLIF(system_name, ''), 'PVOutput public station'),
                       longitude,
                       latitude,
                       GREATEST(COALESCE(system_size_w, 0) / 1000.0, 1),
                       CASE WHEN enabled = 1 THEN 'RUNNING' ELSE 'OFFLINE' END,
                       'PVOutput public station metadata'
                FROM external_pv_station
                WHERE enabled = 1
                  AND longitude IS NOT NULL
                  AND latitude IS NOT NULL
                  AND system_size_w IS NOT NULL
                ORDER BY id
                LIMIT 8
                """);
        } catch (RuntimeException exception) {
            log.warn("Skip external station import for dashboard: {}", exception.getMessage());
        }
    }

    private void createH2Table() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS station_runtime_config (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                station_id BIGINT NOT NULL UNIQUE,
                battery_capacity_kwh DECIMAL(12,3),
                nominal_voltage_v DECIMAL(10,2) DEFAULT 380.00,
                phase_type VARCHAR(16) DEFAULT 'THREE_PHASE',
                performance_ratio DECIMAL(5,3) DEFAULT 0.840,
                power_factor DECIMAL(5,3) DEFAULT 0.960,
                base_load_kw DECIMAL(12,3) DEFAULT 10.000,
                load_peak_factor DECIMAL(6,3) DEFAULT 1.500,
                station_type VARCHAR(32) DEFAULT 'COMMERCIAL',
                timezone VARCHAR(64) DEFAULT 'Asia/Shanghai',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }

    private void addPvDataUniqueIndex() {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'pv_data'
                  AND INDEX_NAME = 'uk_pv_data_station_collect_time'
                """, Integer.class);
            if (count != null && count > 0) return;
            jdbcTemplate.execute("ALTER TABLE pv_data ADD UNIQUE KEY uk_pv_data_station_collect_time (station_id, collect_time)");
        } catch (RuntimeException exception) {
            log.warn("Skip pv_data unique index migration: {}", exception.getMessage());
        }
    }
}
