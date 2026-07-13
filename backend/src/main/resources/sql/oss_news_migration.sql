-- OSS 文件、头像与新闻图片增量迁移。
-- 可安全重复执行；不删除、不重建既有业务数据。

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_oss_news_schema$$
CREATE PROCEDURE migrate_oss_news_schema()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'object_key') THEN
        ALTER TABLE file_resource ADD COLUMN object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS ObjectKey';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'content_type') THEN
        ALTER TABLE file_resource ADD COLUMN content_type VARCHAR(100) DEFAULT NULL COMMENT 'MIME类型';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'file_status') THEN
        ALTER TABLE file_resource ADD COLUMN file_status VARCHAR(20) NOT NULL DEFAULT 'BOUND' COMMENT 'TEMP/BOUND/DELETED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'biz_id') THEN
        ALTER TABLE file_resource ADD COLUMN biz_id BIGINT DEFAULT NULL COMMENT '业务对象ID';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'updated_at') THEN
        ALTER TABLE file_resource ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND INDEX_NAME = 'uk_file_object_key') THEN
        ALTER TABLE file_resource ADD UNIQUE KEY uk_file_object_key (object_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND INDEX_NAME = 'idx_file_biz') THEN
        ALTER TABLE file_resource ADD KEY idx_file_biz (business_type, biz_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_file_id') THEN
        ALTER TABLE sys_user ADD COLUMN avatar_file_id BIGINT DEFAULT NULL COMMENT 'OSS头像文件ID';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_user_avatar_file') THEN
        ALTER TABLE sys_user ADD KEY idx_user_avatar_file (avatar_file_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'news' AND COLUMN_NAME = 'cover_file_id') THEN
        ALTER TABLE news ADD COLUMN cover_file_id BIGINT DEFAULT NULL COMMENT '封面文件ID';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'news' AND INDEX_NAME = 'idx_news_status_publish') THEN
        ALTER TABLE news ADD KEY idx_news_status_publish (status, published_at);
    END IF;
END$$

CALL migrate_oss_news_schema()$$
DROP PROCEDURE migrate_oss_news_schema$$

DELIMITER ;
