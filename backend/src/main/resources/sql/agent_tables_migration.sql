-- Agent tables migration for existing pv_platform databases.
-- Safe to run multiple times on MySQL 8.x.

USE pv_platform;

CREATE TABLE IF NOT EXISTS agent_session (
    session_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent会话ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    title VARCHAR(255) NOT NULL COMMENT '会话标题',
    archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否归档',
    pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否固定',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE，CLOSED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_agent_session_user (user_id, updated_at),
    KEY idx_agent_session_archived (archived),
    CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

CREATE TABLE IF NOT EXISTS agent_message (
    message_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(32) NOT NULL COMMENT '角色：user，assistant，tool，system',
    content LONGTEXT DEFAULT NULL COMMENT '消息内容',
    metadata_json JSON DEFAULT NULL COMMENT '消息元数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_agent_message_session (session_id, created_at),
    KEY idx_agent_message_user (user_id),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES agent_session(session_id),
    CONSTRAINT fk_agent_message_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';

CREATE TABLE IF NOT EXISTS agent_tool_call (
    tool_call_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工具调用ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    message_id BIGINT DEFAULT NULL COMMENT '触发消息ID',
    client_tool_call_id VARCHAR(64) NOT NULL COMMENT '前端展示用工具调用ID',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    arguments_json JSON DEFAULT NULL COMMENT '工具参数',
    result_json JSON DEFAULT NULL COMMENT '工具结果',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING，AWAITING_APPROVAL，SUCCESS，FAILED',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_agent_tool_client_id (client_tool_call_id),
    KEY idx_agent_tool_session (session_id, created_at),
    KEY idx_agent_tool_message (message_id),
    KEY idx_agent_tool_status (status),
    CONSTRAINT fk_agent_tool_session FOREIGN KEY (session_id) REFERENCES agent_session(session_id),
    CONSTRAINT fk_agent_tool_message FOREIGN KEY (message_id) REFERENCES agent_message(message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具调用表';

CREATE TABLE IF NOT EXISTS agent_approval (
    approval_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '确认请求ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    tool_call_id BIGINT NOT NULL COMMENT '工具调用ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING，APPROVED，REJECTED，EXPIRED',
    reason VARCHAR(512) DEFAULT NULL COMMENT '确认原因',
    arguments_json JSON DEFAULT NULL COMMENT '待执行参数',
    comment VARCHAR(512) DEFAULT NULL COMMENT '用户备注',
    decided_at DATETIME DEFAULT NULL COMMENT '确认时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_agent_approval_user (user_id, status),
    KEY idx_agent_approval_session (session_id),
    KEY idx_agent_approval_tool (tool_call_id),
    CONSTRAINT fk_agent_approval_session FOREIGN KEY (session_id) REFERENCES agent_session(session_id),
    CONSTRAINT fk_agent_approval_tool FOREIGN KEY (tool_call_id) REFERENCES agent_tool_call(tool_call_id),
    CONSTRAINT fk_agent_approval_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent用户确认表';


-- Phase 2 additions. Compatible with MySQL versions that do not support ADD COLUMN IF NOT EXISTS.
DROP PROCEDURE IF EXISTS add_agent_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_agent_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @agent_alter_sql = p_alter_sql;
        PREPARE agent_alter_stmt FROM @agent_alter_sql;
        EXECUTE agent_alter_stmt;
        DEALLOCATE PREPARE agent_alter_stmt;
    END IF;
END//
DELIMITER ;

CALL add_agent_column_if_missing('agent_session', 'deleted',
    'ALTER TABLE agent_session ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除'' AFTER status');
CALL add_agent_column_if_missing('agent_tool_call', 'user_id',
    'ALTER TABLE agent_tool_call ADD COLUMN user_id BIGINT NULL COMMENT ''用户ID'' AFTER message_id');
CALL add_agent_column_if_missing('agent_tool_call', 'display_name',
    'ALTER TABLE agent_tool_call ADD COLUMN display_name VARCHAR(128) DEFAULT NULL COMMENT ''工具展示名称'' AFTER tool_name');
CALL add_agent_column_if_missing('agent_approval', 'tool_name',
    'ALTER TABLE agent_approval ADD COLUMN tool_name VARCHAR(128) NULL COMMENT ''工具名称'' AFTER user_id');

DROP PROCEDURE IF EXISTS add_agent_column_if_missing;

UPDATE agent_tool_call tc
JOIN agent_message msg ON tc.message_id = msg.message_id
SET tc.user_id = msg.user_id
WHERE tc.user_id IS NULL;

UPDATE agent_approval ap
JOIN agent_tool_call tc ON ap.tool_call_id = tc.tool_call_id
SET ap.tool_name = tc.tool_name
WHERE ap.tool_name IS NULL;
