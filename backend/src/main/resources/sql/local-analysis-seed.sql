USE pv_platform;

SET @station_code := 'PV-MOCK-ANALYSIS';
SET @model_id := COALESCE((SELECT model_id FROM model_info WHERE model_code = 'DLinear' LIMIT 1), 1);

INSERT INTO power_station (
    station_code,
    station_name,
    province,
    city,
    district,
    address,
    longitude,
    latitude,
    capacity_kw,
    owner_user_id,
    status,
    description
)
SELECT
    @station_code,
    '成都联调光伏电站',
    '四川省',
    '成都市',
    '高新区',
    '本地联调用 mock 数据电站',
    104.066800,
    30.572800,
    1200.000,
    (SELECT MIN(user_id) FROM sys_user WHERE deleted = 0),
    'RUNNING',
    '用于前后端综合分析报告联调的本地 seed 电站'
WHERE NOT EXISTS (
    SELECT 1 FROM power_station WHERE station_code = @station_code
);

SET @station_id := (SELECT station_id FROM power_station WHERE station_code = @station_code LIMIT 1);

INSERT INTO user_station_permission (user_id, station_id, permission_type)
SELECT user_id, @station_id, 'MANAGE'
FROM sys_user
WHERE deleted = 0
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type);

DELETE FROM pv_data WHERE station_id = @station_id AND data_source = 'MOCK_ANALYSIS_SEED';

INSERT INTO pv_data (
    station_id,
    collect_time,
    power_kw,
    energy_today_kwh,
    energy_total_kwh,
    voltage_v,
    current_a,
    irradiance_w_m2,
    module_temperature_c,
    ambient_temperature_c,
    humidity_percent,
    wind_speed_m_s,
    data_source,
    raw_data
)
WITH RECURSIVE seq(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 29
)
SELECT
    @station_id,
    DATE_ADD(DATE_SUB(NOW(), INTERVAL 29 MINUTE), INTERVAL n MINUTE),
    ROUND(520 + n * 2.6 + IF(n BETWEEN 15 AND 22, -38, 0), 4),
    ROUND(180 + n * 0.8, 4),
    ROUND(88200 + n * 0.8, 4),
    ROUND(380 + SIN(n / 4) * 3, 4),
    ROUND(118 + n * 0.25, 4),
    ROUND(720 + n * 5 - IF(n BETWEEN 15 AND 22, 120, 0), 4),
    ROUND(42 + n * 0.12, 3),
    ROUND(29 + n * 0.06, 3),
    ROUND(58 - n * 0.15, 3),
    ROUND(2.2 + n * 0.02, 3),
    'MOCK_ANALYSIS_SEED',
    JSON_OBJECT('source', 'local-analysis-seed', 'index', n)
FROM seq;

INSERT INTO weather_data (
    station_id,
    weather_time,
    weather_code,
    weather_text,
    temperature_c,
    humidity_percent,
    wind_direction,
    wind_power,
    wind_speed_m_s,
    precipitation_mm,
    cloudiness_percent,
    source,
    raw_data
)
VALUES (
    @station_id,
    NOW(),
    '101',
    '多云',
    31.500,
    56.000,
    '东南风',
    '3级',
    2.800,
    0.000,
    48.000,
    'MOCK_ANALYSIS_SEED',
    JSON_OBJECT('source', 'local-analysis-seed')
)
ON DUPLICATE KEY UPDATE
    weather_text = VALUES(weather_text),
    temperature_c = VALUES(temperature_c),
    humidity_percent = VALUES(humidity_percent),
    wind_direction = VALUES(wind_direction),
    wind_power = VALUES(wind_power),
    wind_speed_m_s = VALUES(wind_speed_m_s),
    precipitation_mm = VALUES(precipitation_mm),
    cloudiness_percent = VALUES(cloudiness_percent),
    raw_data = VALUES(raw_data);

INSERT INTO prediction_task (
    task_no,
    user_id,
    station_id,
    model_id,
    input_mode,
    input_start_time,
    input_end_time,
    predict_start_time,
    predict_end_time,
    status,
    request_params,
    cost_time_ms,
    created_at,
    started_at,
    finished_at
)
SELECT
    CONCAT('MOCK-ANALYSIS-', u.user_id),
    u.user_id,
    @station_id,
    @model_id,
    'STATION_HISTORY',
    DATE_SUB(NOW(), INTERVAL 29 MINUTE),
    NOW(),
    DATE_ADD(NOW(), INTERVAL 5 MINUTE),
    DATE_ADD(NOW(), INTERVAL 30 MINUTE),
    'SUCCESS',
    JSON_OBJECT('source', 'local-analysis-seed', 'stationId', @station_id),
    18,
    NOW(),
    NOW(),
    NOW()
FROM sys_user u
WHERE u.deleted = 0
ON DUPLICATE KEY UPDATE
    station_id = VALUES(station_id),
    model_id = VALUES(model_id),
    input_mode = VALUES(input_mode),
    input_start_time = VALUES(input_start_time),
    input_end_time = VALUES(input_end_time),
    predict_start_time = VALUES(predict_start_time),
    predict_end_time = VALUES(predict_end_time),
    status = VALUES(status),
    request_params = VALUES(request_params),
    cost_time_ms = VALUES(cost_time_ms),
    started_at = VALUES(started_at),
    finished_at = VALUES(finished_at);

DELETE pr
FROM prediction_result pr
INNER JOIN prediction_task pt ON pt.task_id = pr.task_id
WHERE pt.task_no LIKE 'MOCK-ANALYSIS-%';

INSERT INTO prediction_result (
    task_id,
    time_offset_minutes,
    predict_time,
    predict_power_kw,
    actual_power_kw,
    error_value,
    error_rate,
    created_at
)
SELECT
    pt.task_id,
    offsets.offset_minute,
    DATE_ADD(NOW(), INTERVAL offsets.offset_minute MINUTE),
    ROUND(610 + offsets.offset_minute * 1.8, 4),
    NULL,
    NULL,
    NULL,
    NOW()
FROM prediction_task pt
JOIN (
    SELECT 5 AS offset_minute
    UNION ALL SELECT 10
    UNION ALL SELECT 15
    UNION ALL SELECT 20
    UNION ALL SELECT 25
    UNION ALL SELECT 30
) offsets
WHERE pt.task_no LIKE 'MOCK-ANALYSIS-%';

SELECT
    @station_id AS stationId,
    (SELECT COUNT(*) FROM pv_data WHERE station_id = @station_id AND data_source = 'MOCK_ANALYSIS_SEED') AS pvDataCount,
    (SELECT COUNT(*) FROM prediction_task WHERE task_no LIKE 'MOCK-ANALYSIS-%') AS predictionTaskCount,
    (SELECT COUNT(*)
     FROM prediction_result pr
     INNER JOIN prediction_task pt ON pt.task_id = pr.task_id
     WHERE pt.task_no LIKE 'MOCK-ANALYSIS-%') AS predictionResultCount;
