-- 2026-07-16 PC 联展示例数据（MySQL 8，可重复执行）
-- 说明：这些记录用于本地截图和联调，request_summary/remark 中保留明确的 demoSeed 标记。
-- 不创建假 API Key；仅使用数据库中已有的启用 Key、在线模型和钱包账户。

START TRANSACTION;

-- 一、为已有的四类用户收件记录补齐“平台消息源”。后台消息管理读取 news，
-- user_notification 只是发布后分发给每个用户的收件副本。
INSERT INTO news (
    title, summary, content, news_type, category, content_type, source_type, source_name,
    external_content, target_role, status, published_at, created_at, updated_at, deleted
)
SELECT seed.title, seed.content, seed.content, seed.news_type, 'PLATFORM',
       'PLATFORM_NOTICE', 'PLATFORM', '光伏智云平台', 0, 'ALL', 'PUBLISHED',
       seed.created_at, seed.created_at, seed.created_at, 0
FROM (
    SELECT title, content,
           CASE WHEN notification_type = 'SYSTEM' THEN 'SYSTEM_NOTICE' ELSE notification_type END AS news_type,
           MIN(created_at) AS created_at
    FROM user_notification
    WHERE notification_type IN ('NOTICE', 'MODEL_UPDATE', 'ALERT', 'SYSTEM', 'SYSTEM_NOTICE')
    GROUP BY title, content,
             CASE WHEN notification_type = 'SYSTEM' THEN 'SYSTEM_NOTICE' ELSE notification_type END
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM news n
    WHERE n.deleted = 0 AND n.title = seed.title AND n.news_type = seed.news_type
);

UPDATE user_notification un
JOIN news n ON n.title = un.title
  AND n.deleted = 0
  AND n.news_type = CASE WHEN un.notification_type = 'SYSTEM' THEN 'SYSTEM_NOTICE' ELSE un.notification_type END
SET un.related_type = 'NEWS', un.related_id = n.news_id
WHERE un.notification_type IN ('NOTICE', 'MODEL_UPDATE', 'ALERT', 'SYSTEM', 'SYSTEM_NOTICE')
  AND (un.related_type IS NULL OR un.related_type IN ('NEWS', 'MODEL'));

-- 二、重建 60 条分布在近 30 天的 API 调用演示记录。
-- 数据关联真实用户、真实 Key 和真实在线模型；Token 不可从当前模型响应可靠取得，因此保持 NULL。
DROP TEMPORARY TABLE IF EXISTS demo_seq;
CREATE TEMPORARY TABLE demo_seq (n INT PRIMARY KEY);
INSERT INTO demo_seq (n) VALUES
(0),(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),(15),(16),(17),(18),(19),
(20),(21),(22),(23),(24),(25),(26),(27),(28),(29),(30),(31),(32),(33),(34),(35),(36),(37),(38),(39),
(40),(41),(42),(43),(44),(45),(46),(47),(48),(49),(50),(51),(52),(53),(54),(55),(56),(57),(58),(59);

DROP TEMPORARY TABLE IF EXISTS demo_keys;
CREATE TEMPORARY TABLE demo_keys AS
SELECT api_key_id, user_id, ROW_NUMBER() OVER (ORDER BY api_key_id) AS rn,
       COUNT(*) OVER () AS total
FROM api_key
WHERE status = 'ACTIVE';

DROP TEMPORARY TABLE IF EXISTS demo_models;
CREATE TEMPORARY TABLE demo_models AS
SELECT model_id, ROW_NUMBER() OVER (ORDER BY model_id) AS rn,
       COUNT(*) OVER () AS total
FROM model_info
WHERE status = 'ONLINE';

DELETE FROM api_call_log
WHERE JSON_UNQUOTE(JSON_EXTRACT(request_summary, '$.seedBatch')) IN (
    'pc_usage_demo_20260715_v1', 'pc_usage_demo_20260716_v2'
);

INSERT INTO api_call_log (
    user_id, api_key_id, model_id, request_path, request_method, request_ip,
    request_time, response_time, cost_time_ms, http_status, biz_status, error_message,
    request_summary, response_summary, input_tokens, output_tokens, total_tokens
)
SELECT k.user_id, k.api_key_id, m.model_id, '/openapi/v1/predict', 'POST',
       CONCAT('10.20.0.', 21 + MOD(s.n, 18)),
       DATE_SUB(NOW(), INTERVAL (59 - s.n) * 12 HOUR),
       TIMESTAMPADD(MICROSECOND, (180 + MOD(s.n * 97, 1320)) * 1000,
           DATE_SUB(NOW(), INTERVAL (59 - s.n) * 12 HOUR)),
       180 + MOD(s.n * 97, 1320),
       CASE WHEN MOD(s.n, 11) = 0 THEN 422 ELSE 200 END,
       CASE WHEN MOD(s.n, 11) = 0 THEN 'FAILED' ELSE 'SUCCESS' END,
       CASE WHEN MOD(s.n, 11) = 0 THEN '输入时间序列不完整' ELSE NULL END,
       JSON_OBJECT('seedBatch', 'pc_usage_demo_20260716_v2', 'dataSource', 'DEMO_SEED',
                   'scene', '光伏功率预测联调'),
       JSON_OBJECT('status', CASE WHEN MOD(s.n, 11) = 0 THEN 'FAILED' ELSE 'SUCCESS' END),
       NULL, NULL, NULL
FROM demo_seq s
JOIN demo_keys k ON k.rn = MOD(s.n, k.total) + 1
JOIN demo_models m ON m.rn = MOD(s.n, m.total) + 1;

UPDATE api_key k
JOIN (
    SELECT api_key_id, MAX(request_time) AS last_used_at
    FROM api_call_log
    WHERE biz_status = 'SUCCESS' AND api_key_id IS NOT NULL
    GROUP BY api_key_id
) usage_time ON usage_time.api_key_id = k.api_key_id
SET k.last_used_at = usage_time.last_used_at, k.updated_at = NOW();

-- 三、为成功的演示调用生成按天汇总的消费流水，并保持脚本可重复执行。
-- 每次重跑会先归还上一批 demo 消费，再按本次成功调用数重新扣减。
DROP TEMPORARY TABLE IF EXISTS old_demo_cost;
CREATE TEMPORARY TABLE old_demo_cost AS
SELECT account_id, ABS(SUM(amount)) AS amount
FROM open_wallet_record
WHERE remark = 'demoSeed=pc_usage_demo_20260716_v2'
GROUP BY account_id;

UPDATE open_wallet_account a
JOIN old_demo_cost d ON d.account_id = a.account_id
SET a.balance = a.balance + d.amount;

DELETE FROM open_wallet_record WHERE remark = 'demoSeed=pc_usage_demo_20260716_v2';

DROP TEMPORARY TABLE IF EXISTS demo_daily_cost;
CREATE TEMPORARY TABLE demo_daily_cost AS
SELECT l.user_id, DATE(l.request_time) AS usage_date, COUNT(*) AS call_count,
       COUNT(*) * 0.01 AS cost
FROM api_call_log l
WHERE l.biz_status = 'SUCCESS'
  AND JSON_UNQUOTE(JSON_EXTRACT(l.request_summary, '$.seedBatch')) = 'pc_usage_demo_20260716_v2'
GROUP BY l.user_id, DATE(l.request_time);

INSERT INTO open_wallet_record (
    user_id, account_id, order_no, type, amount, balance_after, title, remark, created_at
)
SELECT d.user_id, a.account_id, NULL, 'CONSUME', -d.cost,
       a.balance - SUM(d.cost) OVER (PARTITION BY d.user_id ORDER BY d.usage_date),
       CONCAT('开放 API 调用扣费（', d.call_count, ' 次）'),
       'demoSeed=pc_usage_demo_20260716_v2',
       TIMESTAMP(d.usage_date, '23:55:00')
FROM demo_daily_cost d
JOIN open_wallet_account a ON a.user_id = d.user_id;

UPDATE open_wallet_account a
JOIN (SELECT user_id, SUM(cost) AS cost FROM demo_daily_cost GROUP BY user_id) d
  ON d.user_id = a.user_id
SET a.balance = a.balance - d.cost, a.updated_at = NOW();

DROP TEMPORARY TABLE IF EXISTS demo_daily_cost;
DROP TEMPORARY TABLE IF EXISTS old_demo_cost;
DROP TEMPORARY TABLE IF EXISTS demo_models;
DROP TEMPORARY TABLE IF EXISTS demo_keys;
DROP TEMPORARY TABLE IF EXISTS demo_seq;

COMMIT;

-- 核对结果
SELECT news_type, COUNT(*) AS platform_message_count
FROM news WHERE news_type IN ('NOTICE', 'MODEL_UPDATE', 'ALERT', 'SYSTEM_NOTICE') AND deleted = 0
GROUP BY news_type;
SELECT api_key_id, key_name, last_used_at FROM api_key ORDER BY api_key_id;
SELECT biz_status, COUNT(*) AS calls FROM api_call_log
WHERE JSON_UNQUOTE(JSON_EXTRACT(request_summary, '$.seedBatch')) = 'pc_usage_demo_20260716_v2'
GROUP BY biz_status;
SELECT type, COUNT(*) AS records, SUM(amount) AS amount
FROM open_wallet_record GROUP BY type;
