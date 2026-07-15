-- 2026-07-15 站内消息初始化（可重复执行）
-- 为当前所有启用且未删除的用户补充四类消息：公告提醒、模型更新、异常提醒、系统通知。
-- 标题与类型相同的消息不会重复插入；不修改已有消息的已读状态。

START TRANSACTION;

INSERT INTO user_notification (
    user_id,
    title,
    content,
    notification_type,
    related_type,
    related_id,
    read_status,
    read_time,
    created_at
)
SELECT
    u.user_id,
    seed.title,
    seed.content,
    seed.notification_type,
    seed.related_type,
    seed.related_id,
    0,
    NULL,
    CURRENT_TIMESTAMP
FROM sys_user u
CROSS JOIN (
    SELECT
        '欢迎使用资讯与消息中心' AS title,
        '这里会集中展示平台公告、模型更新、异常提醒和系统通知。' AS content,
        'NOTICE' AS notification_type,
        NULL AS related_type,
        NULL AS related_id
    UNION ALL
    SELECT
        'CNN+MLP 模型现已上线',
        'CNN+MLP 已加入可调用模型目录，可在模型广场查看模型信息。',
        'MODEL_UPDATE',
        'MODEL',
        9
    UNION ALL
    SELECT
        '异常提醒功能已启用',
        '预测任务或平台资源出现异常时，相关提醒会显示在此分类中。',
        'ALERT',
        NULL,
        NULL
    UNION ALL
    SELECT
        '消息中心初始化完成',
        '站内消息支持未读筛选、类型筛选、单条已读和全部已读。',
        'SYSTEM',
        NULL,
        NULL
) seed
WHERE u.status = 1
  AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM user_notification existing
      WHERE existing.user_id = u.user_id
        AND existing.notification_type = seed.notification_type
        AND existing.title = seed.title
  );

COMMIT;

-- 核对每个用户的消息总数和未读数。
SELECT
    u.user_id,
    u.username,
    COUNT(n.notification_id) AS message_count,
    SUM(CASE WHEN n.read_status = 0 THEN 1 ELSE 0 END) AS unread_count
FROM sys_user u
LEFT JOIN user_notification n ON n.user_id = u.user_id
WHERE u.status = 1 AND u.deleted = 0
GROUP BY u.user_id, u.username
ORDER BY u.user_id;

-- 核对本次四类消息。
SELECT notification_id, user_id, notification_type, title, read_status, created_at
FROM user_notification
WHERE title IN (
    '欢迎使用资讯与消息中心',
    'CNN+MLP 模型现已上线',
    '异常提醒功能已启用',
    '消息中心初始化完成'
)
ORDER BY user_id, notification_id;

