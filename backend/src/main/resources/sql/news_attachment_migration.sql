-- 新闻主要附件增量迁移。可重复执行，不删除或重建既有数据。
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_news_attachments$$
CREATE PROCEDURE migrate_news_attachments()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='attachment_name') THEN ALTER TABLE news ADD COLUMN attachment_name VARCHAR(255) DEFAULT NULL COMMENT '主要附件名称'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='attachment_type') THEN ALTER TABLE news ADD COLUMN attachment_type VARCHAR(16) DEFAULT NULL COMMENT '主要附件类型'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='attachment_url') THEN ALTER TABLE news ADD COLUMN attachment_url VARCHAR(1000) DEFAULT NULL COMMENT '主要附件外链'; END IF;
END$$
CALL migrate_news_attachments()$$
DROP PROCEDURE migrate_news_attachments$$
DELIMITER ;
