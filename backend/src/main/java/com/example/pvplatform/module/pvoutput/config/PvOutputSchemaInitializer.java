package com.example.pvplatform.module.pvoutput.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@Order(0)
public class PvOutputSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public PvOutputSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        if (isH2()) {
            createH2Tables();
            return;
        }
        createMysqlTables();
    }

    private boolean isH2() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        }
    }

    private void createMysqlTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS external_pv_station (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                source VARCHAR(32) NOT NULL DEFAULT 'PVOUTPUT',
                external_system_id BIGINT NOT NULL,
                system_name VARCHAR(255) DEFAULT NULL,
                system_size_w INT DEFAULT NULL,
                postcode VARCHAR(64) DEFAULT NULL,
                orientation VARCHAR(32) DEFAULT NULL,
                outputs INT DEFAULT NULL,
                last_output_text VARCHAR(64) DEFAULT NULL,
                panel VARCHAR(255) DEFAULT NULL,
                inverter VARCHAR(255) DEFAULT NULL,
                distance_km DECIMAL(10,2) DEFAULT NULL,
                latitude DECIMAL(10,6) DEFAULT NULL,
                longitude DECIMAL(10,6) DEFAULT NULL,
                enabled TINYINT NOT NULL DEFAULT 1,
                last_sync_time DATETIME DEFAULT NULL,
                last_sync_status VARCHAR(32) DEFAULT NULL,
                last_sync_error VARCHAR(512) DEFAULT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_external_system_id (external_system_id),
                KEY idx_external_station_enabled (enabled)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS external_pv_station_status (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                external_system_id BIGINT NOT NULL,
                sample_time DATETIME NOT NULL,
                energy_generation_wh INT DEFAULT NULL,
                power_generation_w INT DEFAULT NULL,
                energy_consumption_wh INT DEFAULT NULL,
                power_consumption_w INT DEFAULT NULL,
                normalised_output DECIMAL(10,4) DEFAULT NULL,
                temperature_c DECIMAL(8,2) DEFAULT NULL,
                voltage_v DECIMAL(8,2) DEFAULT NULL,
                raw_payload TEXT DEFAULT NULL,
                fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_external_station_sample_time (external_system_id, sample_time),
                KEY idx_external_system_id (external_system_id),
                KEY idx_sample_time (sample_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }

    private void createH2Tables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS external_pv_station (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                source VARCHAR(32) DEFAULT 'PVOUTPUT',
                external_system_id BIGINT NOT NULL UNIQUE,
                system_name VARCHAR(255),
                system_size_w INT,
                postcode VARCHAR(64),
                orientation VARCHAR(32),
                outputs INT,
                last_output_text VARCHAR(64),
                panel VARCHAR(255),
                inverter VARCHAR(255),
                distance_km DECIMAL(10,2),
                latitude DECIMAL(10,6),
                longitude DECIMAL(10,6),
                enabled BOOLEAN DEFAULT TRUE,
                last_sync_time TIMESTAMP,
                last_sync_status VARCHAR(32),
                last_sync_error VARCHAR(512),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS external_pv_station_status (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                external_system_id BIGINT NOT NULL,
                sample_time TIMESTAMP NOT NULL,
                energy_generation_wh INT,
                power_generation_w INT,
                energy_consumption_wh INT,
                power_consumption_w INT,
                normalised_output DECIMAL(10,4),
                temperature_c DECIMAL(8,2),
                voltage_v DECIMAL(8,2),
                raw_payload TEXT,
                fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(external_system_id, sample_time)
            )
            """);
    }
}
