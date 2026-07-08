CREATE DATABASE IF NOT EXISTS pv_platform
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE pv_platform;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS user_notification;
DROP TABLE IF EXISTS news;
DROP TABLE IF EXISTS file_resource;
DROP TABLE IF EXISTS api_call_log;
DROP TABLE IF EXISTS api_key;
DROP TABLE IF EXISTS analysis_report;
DROP TABLE IF EXISTS prediction_result;
DROP TABLE IF EXISTS prediction_input_snapshot;
DROP TABLE IF EXISTS prediction_task;
DROP TABLE IF EXISTS model_file;
DROP TABLE IF EXISTS model_metric;
DROP TABLE IF EXISTS model_info;
DROP TABLE IF EXISTS weather_data;
DROP TABLE IF EXISTS pv_data_import_task;
DROP TABLE IF EXISTS pv_data;
DROP TABLE IF EXISTS user_station_permission;
DROP TABLE IF EXISTS power_station;
DROP TABLE IF EXISTS sys_face_auth;
DROP TABLE IF EXISTS sys_oauth_account;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

SET FOREIGN_KEY_CHECKS = 1;


-- =========================================================
-- 1. 用户表
-- =========================================================
CREATE TABLE sys_user (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    gender TINYINT DEFAULT 0 COMMENT '性别：0未知，1男，2女',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- =========================================================
-- 2. 角色表
-- =========================================================
CREATE TABLE sys_role (
    role_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码，如ADMIN、USER、API_USER',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '角色说明',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';


-- =========================================================
-- 3. 用户角色关联表
-- =========================================================
CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_user (user_id),
    KEY idx_user_role_role (role_id),

    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id) REFERENCES sys_role(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';


-- =========================================================
-- 4. 第三方登录账号表
-- =========================================================
CREATE TABLE sys_oauth_account (
    oauth_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '第三方账号ID',
    user_id BIGINT NOT NULL COMMENT '绑定用户ID',
    provider VARCHAR(32) NOT NULL COMMENT '第三方平台：WECHAT、QQ、GITHUB等',
    open_id VARCHAR(128) NOT NULL COMMENT '第三方平台openId',
    union_id VARCHAR(128) DEFAULT NULL COMMENT 'unionId',
    nickname VARCHAR(128) DEFAULT NULL COMMENT '第三方昵称',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '第三方头像',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_provider_openid (provider, open_id),
    KEY idx_oauth_user (user_id),

    CONSTRAINT fk_oauth_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方登录账号表';


-- =========================================================
-- 5. 人脸识别预留表
-- 注意：课程项目可以只做预留，不一定实现真实人脸识别
-- =========================================================
CREATE TABLE sys_face_auth (
    face_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '人脸认证ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    face_feature_id VARCHAR(128) DEFAULT NULL COMMENT '人脸特征ID或第三方平台ID',
    face_image_url VARCHAR(512) DEFAULT NULL COMMENT '人脸图片URL，不建议生产环境直接保存原图',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_face_user (user_id),

    CONSTRAINT fk_face_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸识别预留表';


-- =========================================================
-- 6. 光伏电站表
-- =========================================================
CREATE TABLE power_station (
    station_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '电站ID',
    station_code VARCHAR(64) NOT NULL COMMENT '电站编码',
    station_name VARCHAR(128) NOT NULL COMMENT '电站名称',
    province VARCHAR(64) DEFAULT NULL COMMENT '省份',
    city VARCHAR(64) DEFAULT NULL COMMENT '城市',
    district VARCHAR(64) DEFAULT NULL COMMENT '区县',
    address VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    longitude DECIMAL(10, 6) DEFAULT NULL COMMENT '经度',
    latitude DECIMAL(10, 6) DEFAULT NULL COMMENT '纬度',
    capacity_kw DECIMAL(12, 3) DEFAULT NULL COMMENT '装机容量，单位kW',
    installed_area DECIMAL(12, 3) DEFAULT NULL COMMENT '安装面积，单位平方米',
    grid_connected_date DATE DEFAULT NULL COMMENT '并网日期',
    owner_user_id BIGINT DEFAULT NULL COMMENT '电站负责人用户ID',
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING运行中，MAINTENANCE维护中，OFFLINE离线',
    description VARCHAR(500) DEFAULT NULL COMMENT '电站描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_station_code (station_code),
    KEY idx_station_region (province, city, district),
    KEY idx_station_status (status),
    KEY idx_station_owner (owner_user_id),

    CONSTRAINT fk_station_owner
        FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='光伏电站表';


-- =========================================================
-- 7. 用户电站权限表
-- 用于控制普通用户能查看哪些电站
-- =========================================================
CREATE TABLE user_station_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    station_id BIGINT NOT NULL COMMENT '电站ID',
    permission_type VARCHAR(32) NOT NULL DEFAULT 'VIEW' COMMENT '权限类型：VIEW查看，MANAGE管理',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_user_station (user_id, station_id),
    KEY idx_user_station_user (user_id),
    KEY idx_user_station_station (station_id),

    CONSTRAINT fk_user_station_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_user_station_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户电站权限表';


-- =========================================================
-- 8. 光伏采集数据表
-- 高频数据表，核心字段是 station_id + collect_time
-- =========================================================
CREATE TABLE pv_data (
    data_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '数据ID',
    station_id BIGINT NOT NULL COMMENT '电站ID',
    collect_time DATETIME NOT NULL COMMENT '采集时间',

    power_kw DECIMAL(12, 4) DEFAULT NULL COMMENT '实时功率，单位kW',
    energy_today_kwh DECIMAL(12, 4) DEFAULT NULL COMMENT '当日发电量，单位kWh',
    energy_total_kwh DECIMAL(16, 4) DEFAULT NULL COMMENT '累计发电量，单位kWh',

    voltage_v DECIMAL(12, 4) DEFAULT NULL COMMENT '电压，单位V',
    current_a DECIMAL(12, 4) DEFAULT NULL COMMENT '电流，单位A',
    irradiance_w_m2 DECIMAL(12, 4) DEFAULT NULL COMMENT '辐照度，单位W/m²',
    module_temperature_c DECIMAL(8, 3) DEFAULT NULL COMMENT '组件温度，单位℃',
    ambient_temperature_c DECIMAL(8, 3) DEFAULT NULL COMMENT '环境温度，单位℃',
    humidity_percent DECIMAL(8, 3) DEFAULT NULL COMMENT '湿度，单位%',
    wind_speed_m_s DECIMAL(8, 3) DEFAULT NULL COMMENT '风速，单位m/s',

    data_source VARCHAR(64) DEFAULT 'MANUAL' COMMENT '数据来源：MANUAL手动，API接口，IMPORT导入，MOCK模拟',
    raw_data JSON DEFAULT NULL COMMENT '原始数据JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_station_collect_time (station_id, collect_time),
    KEY idx_pv_station_time (station_id, collect_time),
    KEY idx_pv_collect_time (collect_time),

    CONSTRAINT fk_pv_data_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='光伏采集数据表';


-- =========================================================
-- 9. 光伏数据导入任务表
-- =========================================================
CREATE TABLE pv_data_import_task (
    import_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '导入任务ID',
    user_id BIGINT NOT NULL COMMENT '上传用户ID',
    station_id BIGINT NOT NULL COMMENT '电站ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_url VARCHAR(512) DEFAULT NULL COMMENT '文件存储地址',
    total_count INT DEFAULT 0 COMMENT '总数据条数',
    success_count INT DEFAULT 0 COMMENT '成功条数',
    fail_count INT DEFAULT 0 COMMENT '失败条数',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING，PROCESSING，SUCCESS，FAILED',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at DATETIME DEFAULT NULL COMMENT '完成时间',

    KEY idx_import_user (user_id),
    KEY idx_import_station (station_id),
    KEY idx_import_status (status),

    CONSTRAINT fk_import_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_import_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='光伏数据导入任务表';


-- =========================================================
-- 10. 天气数据表
-- =========================================================
CREATE TABLE weather_data (
    weather_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '天气数据ID',
    station_id BIGINT NOT NULL COMMENT '电站ID',
    weather_time DATETIME NOT NULL COMMENT '天气时间',

    weather_code VARCHAR(32) DEFAULT NULL COMMENT '天气编码',
    weather_text VARCHAR(64) DEFAULT NULL COMMENT '天气描述，如晴、多云、小雨',
    temperature_c DECIMAL(8, 3) DEFAULT NULL COMMENT '温度，单位℃',
    humidity_percent DECIMAL(8, 3) DEFAULT NULL COMMENT '湿度，单位%',
    wind_direction VARCHAR(32) DEFAULT NULL COMMENT '风向',
    wind_power VARCHAR(32) DEFAULT NULL COMMENT '风力等级',
    wind_speed_m_s DECIMAL(8, 3) DEFAULT NULL COMMENT '风速，单位m/s',
    precipitation_mm DECIMAL(8, 3) DEFAULT NULL COMMENT '降水量，单位mm',
    cloudiness_percent DECIMAL(8, 3) DEFAULT NULL COMMENT '云量，单位%',

    source VARCHAR(64) DEFAULT 'AMAP' COMMENT '来源：AMAP高德，MANUAL手动，MOCK模拟等',
    raw_data JSON DEFAULT NULL COMMENT '原始天气接口数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_weather_station_time_source (station_id, weather_time, source),
    KEY idx_weather_station_time (station_id, weather_time),

    CONSTRAINT fk_weather_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气数据表';


-- =========================================================
-- 11. 模型信息表
-- =========================================================
CREATE TABLE model_info (
    model_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型ID',
    model_code VARCHAR(64) NOT NULL COMMENT '模型编码，如DLinear、iTransformer',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    model_type VARCHAR(64) NOT NULL COMMENT '模型类型：NUMERIC数值预测，MULTIMODAL云图时空模型，FUSION图像+数值融合模型，IMAGE_TO_NUMERIC图转数值',
    model_version VARCHAR(64) DEFAULT 'v1.0' COMMENT '模型版本',

    input_window_minutes INT NOT NULL DEFAULT 30 COMMENT '输入窗口长度，单位分钟',
    input_frame_interval_seconds INT NOT NULL DEFAULT 60 COMMENT '输入帧间隔，单位秒',
    output_steps INT NOT NULL DEFAULT 6 COMMENT '输出步数',
    output_step_minutes INT NOT NULL DEFAULT 5 COMMENT '每个输出步长，单位分钟',

    service_model_name VARCHAR(128) NOT NULL COMMENT '模型服务中的模型名称',
    api_path VARCHAR(255) DEFAULT '/model-api/predict' COMMENT '模型服务接口路径',

    input_schema JSON DEFAULT NULL COMMENT '输入格式说明JSON',
    output_schema JSON DEFAULT NULL COMMENT '输出格式说明JSON',

    status VARCHAR(32) NOT NULL DEFAULT 'ONLINE' COMMENT '状态：ONLINE上线，OFFLINE下线，TESTING测试中',
    description VARCHAR(1000) DEFAULT NULL COMMENT '模型描述',
    created_by BIGINT DEFAULT NULL COMMENT '创建者用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_model_code (model_code),
    KEY idx_model_type (model_type),
    KEY idx_model_status (status),

    CONSTRAINT fk_model_created_by
        FOREIGN KEY (created_by) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型信息表';


-- =========================================================
-- 12. 模型指标表
-- 用于记录模型评估结果，如 MAE、RMSE、MAPE
-- =========================================================
CREATE TABLE model_metric (
    metric_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型指标ID',
    model_id BIGINT NOT NULL COMMENT '模型ID',
    dataset_name VARCHAR(128) DEFAULT NULL COMMENT '测试数据集名称',
    mae DECIMAL(12, 6) DEFAULT NULL COMMENT '平均绝对误差MAE',
    rmse DECIMAL(12, 6) DEFAULT NULL COMMENT '均方根误差RMSE',
    mape DECIMAL(12, 6) DEFAULT NULL COMMENT '平均绝对百分比误差MAPE',
    r2_score DECIMAL(12, 6) DEFAULT NULL COMMENT 'R2分数',
    metric_json JSON DEFAULT NULL COMMENT '其他指标JSON',
    evaluated_at DATETIME DEFAULT NULL COMMENT '评估时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_metric_model (model_id),

    CONSTRAINT fk_metric_model
        FOREIGN KEY (model_id) REFERENCES model_info(model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型指标表';


-- =========================================================
-- 13. 模型文件表
-- 用于记录真实模型文件路径，当前可以先不用
-- =========================================================
CREATE TABLE model_file (
    file_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模型文件ID',
    model_id BIGINT NOT NULL COMMENT '模型ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
    file_type VARCHAR(64) DEFAULT NULL COMMENT '文件类型：pth，pt，onnx，pkl等',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小，单位字节',
    checksum VARCHAR(128) DEFAULT NULL COMMENT '文件校验值',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_model_file_model (model_id),

    CONSTRAINT fk_model_file_model
        FOREIGN KEY (model_id) REFERENCES model_info(model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型文件表';


-- =========================================================
-- 14. 预测任务表
-- =========================================================
CREATE TABLE prediction_task (
    task_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '预测任务ID',
    task_no VARCHAR(64) NOT NULL COMMENT '预测任务编号',

    user_id BIGINT NOT NULL COMMENT '发起用户ID',
    station_id BIGINT DEFAULT NULL COMMENT '电站ID',
    model_id BIGINT NOT NULL COMMENT '模型ID',

    input_mode VARCHAR(32) NOT NULL DEFAULT 'STATION_HISTORY' COMMENT '输入方式：STATION_HISTORY历史数据，FILE_UPLOAD文件上传，MANUAL_INPUT手动输入，OPEN_API开放API',
    input_start_time DATETIME DEFAULT NULL COMMENT '输入数据开始时间',
    input_end_time DATETIME DEFAULT NULL COMMENT '输入数据结束时间',
    predict_start_time DATETIME DEFAULT NULL COMMENT '预测开始时间',
    predict_end_time DATETIME DEFAULT NULL COMMENT '预测结束时间',

    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING，RUNNING，SUCCESS，FAILED',
    request_params JSON DEFAULT NULL COMMENT '请求参数快照',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    cost_time_ms BIGINT DEFAULT NULL COMMENT '预测耗时，单位毫秒',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    started_at DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME DEFAULT NULL COMMENT '完成时间',

    UNIQUE KEY uk_prediction_task_no (task_no),
    KEY idx_prediction_user (user_id),
    KEY idx_prediction_station (station_id),
    KEY idx_prediction_model (model_id),
    KEY idx_prediction_status (status),
    KEY idx_prediction_created_at (created_at),

    CONSTRAINT fk_prediction_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_prediction_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id),
    CONSTRAINT fk_prediction_model
        FOREIGN KEY (model_id) REFERENCES model_info(model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测任务表';


-- =========================================================
-- 15. 预测输入快照表
-- 保存调用模型时实际使用的输入数据，方便复现
-- =========================================================
CREATE TABLE prediction_input_snapshot (
    input_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '输入快照ID',
    task_id BIGINT NOT NULL COMMENT '预测任务ID',
    point_time DATETIME NOT NULL COMMENT '输入时间点',

    power_kw DECIMAL(12, 4) DEFAULT NULL COMMENT '输入功率，单位kW',
    temperature_c DECIMAL(8, 3) DEFAULT NULL COMMENT '温度，单位℃',
    irradiance_w_m2 DECIMAL(12, 4) DEFAULT NULL COMMENT '辐照度，单位W/m²',
    humidity_percent DECIMAL(8, 3) DEFAULT NULL COMMENT '湿度，单位%',
    wind_speed_m_s DECIMAL(8, 3) DEFAULT NULL COMMENT '风速，单位m/s',

    raw_data JSON DEFAULT NULL COMMENT '该时间点完整输入数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_task_point_time (task_id, point_time),
    KEY idx_input_task (task_id),

    CONSTRAINT fk_input_task
        FOREIGN KEY (task_id) REFERENCES prediction_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测输入快照表';


-- =========================================================
-- 16. 预测结果表
-- 每个任务一般保存6条结果：t+5, t+10, ..., t+30
-- =========================================================
CREATE TABLE prediction_result (
    result_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '预测结果ID',
    task_id BIGINT NOT NULL COMMENT '预测任务ID',
    time_offset_minutes INT NOT NULL COMMENT '相对预测时间偏移，单位分钟，如5、10、15',
    predict_time DATETIME NOT NULL COMMENT '预测对应的实际时间',
    predict_power_kw DECIMAL(12, 4) NOT NULL COMMENT '预测功率，单位kW',

    actual_power_kw DECIMAL(12, 4) DEFAULT NULL COMMENT '真实功率，后续有真实值后回填',
    error_value DECIMAL(12, 4) DEFAULT NULL COMMENT '误差值：actual - predict',
    error_rate DECIMAL(12, 6) DEFAULT NULL COMMENT '误差率',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_task_offset (task_id, time_offset_minutes),
    KEY idx_result_task (task_id),
    KEY idx_result_predict_time (predict_time),

    CONSTRAINT fk_result_task
        FOREIGN KEY (task_id) REFERENCES prediction_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测结果表';


-- =========================================================
-- 17. 综合分析报告表
-- =========================================================
CREATE TABLE analysis_report (
    report_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
    user_id BIGINT NOT NULL COMMENT '生成用户ID',
    station_id BIGINT DEFAULT NULL COMMENT '电站ID',
    task_id BIGINT DEFAULT NULL COMMENT '关联预测任务ID',

    title VARCHAR(255) NOT NULL COMMENT '报告标题',
    summary TEXT DEFAULT NULL COMMENT '综合摘要',
    weather_analysis TEXT DEFAULT NULL COMMENT '天气分析',
    prediction_analysis TEXT DEFAULT NULL COMMENT '预测分析',
    abnormal_analysis TEXT DEFAULT NULL COMMENT '异常分析',
    suggestion TEXT DEFAULT NULL COMMENT '运维建议',
    report_content LONGTEXT DEFAULT NULL COMMENT '完整报告正文',
    report_json JSON DEFAULT NULL COMMENT '结构化报告JSON',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_report_user (user_id),
    KEY idx_report_station (station_id),
    KEY idx_report_task (task_id),
    KEY idx_report_created_at (created_at),

    CONSTRAINT fk_report_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_report_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id),
    CONSTRAINT fk_report_task
        FOREIGN KEY (task_id) REFERENCES prediction_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合分析报告表';


-- =========================================================
-- 18. API Key 表
-- 用于模型开放平台
-- =========================================================
CREATE TABLE api_key (
    api_key_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'API Key ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    key_name VARCHAR(128) NOT NULL COMMENT 'Key名称',
    api_key_prefix VARCHAR(32) NOT NULL COMMENT 'Key前缀，用于展示',
    api_key_hash VARCHAR(255) NOT NULL COMMENT 'API Key哈希值，不保存明文',

    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用，DISABLED禁用，EXPIRED过期',
    rate_limit_per_minute INT DEFAULT 60 COMMENT '每分钟调用限制',
    daily_quota INT DEFAULT 1000 COMMENT '每日调用额度',
    expire_time DATETIME DEFAULT NULL COMMENT '过期时间',
    last_used_at DATETIME DEFAULT NULL COMMENT '最后使用时间',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_api_key_hash (api_key_hash),
    KEY idx_api_key_user (user_id),
    KEY idx_api_key_status (status),

    CONSTRAINT fk_api_key_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key表';


-- =========================================================
-- 19. API 调用日志表
-- =========================================================
CREATE TABLE api_call_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '调用日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    api_key_id BIGINT DEFAULT NULL COMMENT 'API Key ID',
    model_id BIGINT DEFAULT NULL COMMENT '模型ID',

    request_path VARCHAR(255) NOT NULL COMMENT '请求路径',
    request_method VARCHAR(16) NOT NULL COMMENT '请求方法',
    request_ip VARCHAR(64) DEFAULT NULL COMMENT '请求IP',
    request_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
    response_time DATETIME DEFAULT NULL COMMENT '响应时间',
    cost_time_ms BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',

    http_status INT DEFAULT NULL COMMENT 'HTTP状态码',
    biz_status VARCHAR(32) DEFAULT NULL COMMENT '业务状态：SUCCESS，FAILED',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    request_summary JSON DEFAULT NULL COMMENT '请求摘要，不建议存完整敏感请求',
    response_summary JSON DEFAULT NULL COMMENT '响应摘要',

    KEY idx_call_user (user_id),
    KEY idx_call_key (api_key_id),
    KEY idx_call_model (model_id),
    KEY idx_call_time (request_time),
    KEY idx_call_status (biz_status),

    CONSTRAINT fk_call_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_call_api_key
        FOREIGN KEY (api_key_id) REFERENCES api_key(api_key_id),
    CONSTRAINT fk_call_model
        FOREIGN KEY (model_id) REFERENCES model_info(model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API调用日志表';


-- =========================================================
-- 20. 文件资源表
-- 用于保存用户上传的数据文件、图片、模型文件等
-- =========================================================
CREATE TABLE file_resource (
    file_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    owner_user_id BIGINT DEFAULT NULL COMMENT '上传用户ID',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_name VARCHAR(255) NOT NULL COMMENT '存储文件名',
    storage_path VARCHAR(512) NOT NULL COMMENT '存储路径',
    file_url VARCHAR(512) DEFAULT NULL COMMENT '访问URL',
    file_type VARCHAR(64) DEFAULT NULL COMMENT '文件类型，如csv、xlsx、png、jpg',
    business_type VARCHAR(64) DEFAULT NULL COMMENT '业务类型：PV_DATA，MODEL_FILE，NEWS_COVER，AVATAR等',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小，单位字节',
    checksum VARCHAR(128) DEFAULT NULL COMMENT '文件校验值',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_file_owner (owner_user_id),
    KEY idx_file_business (business_type),

    CONSTRAINT fk_file_owner
        FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资源表';


-- =========================================================
-- 21. 新闻通知表
-- =========================================================
CREATE TABLE news (
    news_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '新闻ID',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    summary VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    content LONGTEXT NOT NULL COMMENT '正文内容',
    cover_url VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',

    news_type VARCHAR(32) NOT NULL DEFAULT 'NEWS' COMMENT '类型：NEWS新闻，NOTICE公告，MODEL_UPDATE模型更新，ALERT异常提醒',
    target_role VARCHAR(64) DEFAULT 'ALL' COMMENT '目标角色：ALL，USER，ADMIN，API_USER',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿，PUBLISHED已发布，OFFLINE下线',

    publisher_id BIGINT DEFAULT NULL COMMENT '发布者ID',
    published_at DATETIME DEFAULT NULL COMMENT '发布时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    KEY idx_news_type (news_type),
    KEY idx_news_status (status),
    KEY idx_news_published_at (published_at),
    KEY idx_news_publisher (publisher_id),

    CONSTRAINT fk_news_publisher
        FOREIGN KEY (publisher_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻通知表';


-- =========================================================
-- 22. 用户站内通知表
-- =========================================================
CREATE TABLE user_notification (
    notification_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    title VARCHAR(255) NOT NULL COMMENT '通知标题',
    content TEXT DEFAULT NULL COMMENT '通知内容',
    notification_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '通知类型：SYSTEM系统，NEWS新闻，PREDICTION预测，ALERT异常',
    related_type VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型，如NEWS、PREDICTION_TASK',
    related_id BIGINT DEFAULT NULL COMMENT '关联业务ID',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '阅读状态：0未读，1已读',
    read_time DATETIME DEFAULT NULL COMMENT '阅读时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_notification_user (user_id),
    KEY idx_notification_read (read_status),
    KEY idx_notification_created_at (created_at),

    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户站内通知表';


-- =========================================================
-- 23. 初始化基础角色
-- =========================================================
INSERT INTO sys_role (role_code, role_name, description)
VALUES
('ADMIN', '管理员', '拥有系统管理权限'),
('USER', '普通用户', '可以查看电站、调用模型、查看预测结果'),
('API_USER', 'API用户', '可以申请API Key并调用开放模型接口');


-- =========================================================
-- 24. 初始化模型信息
-- service_model_name 必须与 model-service /model-api/models 返回值一致。
-- 仅数值模型默认 ONLINE，可直接使用 STATION_HISTORY 30 帧功率数据调用；
-- MULTIMODAL/FUSION 需要显式云图输入，默认 OFFLINE，避免普通预测入口误调用。
-- =========================================================
INSERT INTO model_info (
    model_code,
    model_name,
    model_type,
    model_version,
    input_window_minutes,
    input_frame_interval_seconds,
    output_steps,
    output_step_minutes,
    service_model_name,
    api_path,
    status,
    description
)
VALUES
(
    'DLinear',
    'DLinear线性分解时序预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'DLinear',
    '/model-api/predict',
    'ONLINE',
    'DLinear 线性分解时序预测模型，可直接使用历史功率序列预测未来30分钟功率'
),
(
    'PatchTST',
    'PatchTST时序预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'PatchTST',
    '/model-api/predict',
    'ONLINE',
    'PatchTST 分块 Transformer 时序预测模型'
),
(
    'iTransformer',
    'iTransformer光伏功率预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'iTransformer',
    '/model-api/predict',
    'ONLINE',
    'iTransformer 通道独立 Transformer 时序预测模型'
),
(
    'TimeXer',
    'TimeXer外生变量增强时序预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'TimeXer',
    '/model-api/predict',
    'ONLINE',
    'TimeXer 外生变量增强时序预测模型'
),
(
    'TimeMixer',
    'TimeMixer多尺度时序预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'TimeMixer',
    '/model-api/predict',
    'ONLINE',
    'TimeMixer 多尺度混合时序预测模型'
),
(
    'TSMixer',
    'TSMixer时序混合MLP预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'TSMixer',
    '/model-api/predict',
    'ONLINE',
    'TSMixer 时序混合 MLP 预测模型'
),
(
    'Transformer',
    'Transformer光伏功率预测模型',
    'NUMERIC',
    'v1.0',
    30,
    60,
    6,
    5,
    'Transformer',
    '/model-api/predict',
    'ONLINE',
    '标准 Transformer 时序预测模型'
),
(
    'CNN_LSTM',
    'CNN-LSTM融合光伏功率预测模型',
    'FUSION',
    'v1.0',
    30,
    60,
    6,
    5,
    'CNN_LSTM',
    '/model-api/predict',
    'OFFLINE',
    'CNN-LSTM 图像CNN+数值LSTM融合模型，调用时需要显式云图输入'
);
