package com.example.pvplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "database.migration.open-wallet", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class OpenWalletSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OpenWalletSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public OpenWalletSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("sys_user")) {
            log.warn("Skip open wallet migration because sys_user does not exist; initialize the database first");
            return;
        }
        createWalletAccountIfMissing();
        createRechargeOrderIfMissing();
        createWalletRecordIfMissing();
    }

    private void createWalletAccountIfMissing() {
        if (tableExists("open_wallet_account")) return;
        jdbcTemplate.execute("""
            CREATE TABLE open_wallet_account (
                account_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '钱包账户ID',
                user_id BIGINT NOT NULL COMMENT '所属用户ID',
                balance DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
                frozen_balance DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
                currency VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
                status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用，DISABLED禁用',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                UNIQUE KEY uk_wallet_user (user_id),
                KEY idx_wallet_status (status),
                CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台钱包账户表'
            """);
        log.info("Created open_wallet_account");
    }

    private void createRechargeOrderIfMissing() {
        if (tableExists("open_recharge_order")) return;
        jdbcTemplate.execute("""
            CREATE TABLE open_recharge_order (
                order_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '充值订单ID',
                order_no VARCHAR(64) NOT NULL COMMENT '充值订单号',
                user_id BIGINT NOT NULL COMMENT '所属用户ID',
                account_id BIGINT NOT NULL COMMENT '钱包账户ID',
                amount DECIMAL(18, 2) NOT NULL COMMENT '充值金额',
                currency VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
                channel VARCHAR(32) NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道：MOCK/ALIPAY/WECHAT/BANK',
                status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待支付，PAID已支付，CLOSED已关闭',
                paid_at DATETIME DEFAULT NULL COMMENT '支付完成时间',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                UNIQUE KEY uk_recharge_order_no (order_no),
                KEY idx_recharge_user_time (user_id, created_at),
                KEY idx_recharge_account (account_id),
                KEY idx_recharge_status (status),
                CONSTRAINT fk_recharge_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
                CONSTRAINT fk_recharge_account FOREIGN KEY (account_id) REFERENCES open_wallet_account(account_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台充值订单表'
            """);
        log.info("Created open_recharge_order");
    }

    private void createWalletRecordIfMissing() {
        if (tableExists("open_wallet_record")) return;
        jdbcTemplate.execute("""
            CREATE TABLE open_wallet_record (
                record_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '钱包流水ID',
                user_id BIGINT NOT NULL COMMENT '所属用户ID',
                account_id BIGINT NOT NULL COMMENT '钱包账户ID',
                order_no VARCHAR(64) DEFAULT NULL COMMENT '关联订单号',
                type VARCHAR(32) NOT NULL COMMENT '流水类型：RECHARGE充值，CONSUME消费，REFUND退款，ADJUST调整',
                amount DECIMAL(18, 2) NOT NULL COMMENT '变动金额，收入为正，支出为负',
                balance_after DECIMAL(18, 2) NOT NULL COMMENT '变动后余额',
                title VARCHAR(128) NOT NULL COMMENT '流水标题',
                remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                KEY idx_wallet_record_user_time (user_id, created_at),
                KEY idx_wallet_record_account_time (account_id, created_at),
                KEY idx_wallet_record_order (order_no),
                KEY idx_wallet_record_type (type),
                CONSTRAINT fk_wallet_record_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
                CONSTRAINT fk_wallet_record_account FOREIGN KEY (account_id) REFERENCES open_wallet_account(account_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台钱包流水表'
            """);
        log.info("Created open_wallet_record");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
            Integer.class, tableName);
        return count != null && count > 0;
    }
}
