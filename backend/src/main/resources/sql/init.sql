CREATE DATABASE IF NOT EXISTS pv_platform
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE pv_platform;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS user_notification;
DROP TABLE IF EXISTS news;
DROP TABLE IF EXISTS file_resource;
DROP TABLE IF EXISTS api_call_log;
DROP TABLE IF EXISTS open_wallet_record;
DROP TABLE IF EXISTS open_recharge_order;
DROP TABLE IF EXISTS open_wallet_account;
DROP TABLE IF EXISTS api_key;
DROP TABLE IF EXISTS agent_approval;
DROP TABLE IF EXISTS agent_tool_call;
DROP TABLE IF EXISTS agent_message;
DROP TABLE IF EXISTS agent_session;
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
DROP TABLE IF EXISTS external_pv_station_status;
DROP TABLE IF EXISTS external_pv_station;
DROP TABLE IF EXISTS power_station;
DROP TABLE IF EXISTS sys_face_auth;
DROP TABLE IF EXISTS sys_oauth_account;
DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;


-- =========================================================
-- 登录审计日志表
-- =========================================================
CREATE TABLE sys_login_log (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID，用户不存在时可为空',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    login_type VARCHAR(32) NOT NULL COMMENT '操作类型：LOGIN登录，LOGOUT退出，REGISTER注册，REFRESH刷新令牌，OAUTH_LOGIN第三方登录，FACE_LOGIN人脸登录',
    login_ip VARCHAR(64) DEFAULT NULL COMMENT '登录IP',
    user_agent VARCHAR(512) DEFAULT NULL COMMENT '用户代理',
    status VARCHAR(16) NOT NULL COMMENT '状态：SUCCESS成功，FAIL失败',
    message VARCHAR(255) DEFAULT NULL COMMENT '附加信息，如失败原因',
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    KEY idx_login_user (user_id),
    KEY idx_login_time (login_time),
    KEY idx_login_status (status),
    KEY idx_login_type (login_type),

    CONSTRAINT fk_login_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录审计日志表';


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
    avatar_file_id BIGINT DEFAULT NULL COMMENT 'OSS头像文件ID',
    gender TINYINT DEFAULT 0 COMMENT '性别：0未知，1男，2女',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP',
    token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本，用于使旧Token失效',
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
    email VARCHAR(128) DEFAULT NULL COMMENT '第三方邮箱',
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
    provider VARCHAR(32) NOT NULL DEFAULT 'local' COMMENT '提供商：local本地mock，aliyun阿里云',
    face_db_name VARCHAR(64) DEFAULT NULL COMMENT '人脸库名称（阿里云）',
    entity_id VARCHAR(128) DEFAULT NULL COMMENT '人脸实体ID',
    face_feature_id VARCHAR(128) DEFAULT NULL COMMENT '人脸特征ID或FaceId',
    face_image_url VARCHAR(512) DEFAULT NULL COMMENT '人脸图片URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_face_user_provider (user_id, provider),
    UNIQUE KEY uk_face_provider_entity (provider, entity_id),

    CONSTRAINT fk_face_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸识别表';


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
-- 7. 外部公开电站表（PVOutput）
-- =========================================================
CREATE TABLE external_pv_station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    source VARCHAR(32) NOT NULL DEFAULT 'PVOUTPUT' COMMENT '数据源',
    external_system_id BIGINT NOT NULL COMMENT '外部系统ID',
    system_name VARCHAR(255) DEFAULT NULL COMMENT '系统名称',
    system_size_w INT DEFAULT NULL COMMENT '系统容量，单位W',
    postcode VARCHAR(64) DEFAULT NULL COMMENT '邮编',
    orientation VARCHAR(32) DEFAULT NULL COMMENT '朝向',
    outputs INT DEFAULT NULL COMMENT '输出记录数',
    last_output_text VARCHAR(64) DEFAULT NULL COMMENT '最近输出文本',
    panel VARCHAR(255) DEFAULT NULL COMMENT '组件信息',
    inverter VARCHAR(255) DEFAULT NULL COMMENT '逆变器信息',
    distance_km DECIMAL(10,2) DEFAULT NULL COMMENT '距离，单位km',
    latitude DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    longitude DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用同步',
    last_sync_time DATETIME DEFAULT NULL COMMENT '最近同步时间',
    last_sync_status VARCHAR(32) DEFAULT NULL COMMENT '最近同步状态',
    last_sync_error VARCHAR(512) DEFAULT NULL COMMENT '最近同步错误',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_external_system_id (external_system_id),
    KEY idx_external_station_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部公开光伏电站表';

CREATE TABLE external_pv_station_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    external_system_id BIGINT NOT NULL COMMENT '外部系统ID',
    sample_time DATETIME NOT NULL COMMENT '采样时间',
    energy_generation_wh INT DEFAULT NULL COMMENT '发电量，单位Wh',
    power_generation_w INT DEFAULT NULL COMMENT '发电功率，单位W',
    energy_consumption_wh INT DEFAULT NULL COMMENT '用电量，单位Wh',
    power_consumption_w INT DEFAULT NULL COMMENT '用电功率，单位W',
    normalised_output DECIMAL(10,4) DEFAULT NULL COMMENT '归一化输出',
    temperature_c DECIMAL(8,2) DEFAULT NULL COMMENT '温度，摄氏度',
    voltage_v DECIMAL(8,2) DEFAULT NULL COMMENT '电压，单位V',
    raw_payload TEXT DEFAULT NULL COMMENT '原始CSV',
    fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',

    UNIQUE KEY uk_external_station_sample_time (external_system_id, sample_time),
    KEY idx_external_system_id (external_system_id),
    KEY idx_sample_time (sample_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部公开光伏电站状态表';


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
    short_description VARCHAR(500) DEFAULT NULL COMMENT '模型广场短描述',
    tags JSON DEFAULT NULL COMMENT '模型标签数组',
    model_family VARCHAR(64) DEFAULT NULL COMMENT '模型家族：TIME_SERIES/VISION_FUSION/VIDEO_RECURSIVE',
    provider VARCHAR(128) DEFAULT NULL COMMENT '模型来源机构或作者',
    release_year INT DEFAULT NULL COMMENT '论文或模型发布时间',
    paper_title VARCHAR(255) DEFAULT NULL COMMENT '论文标题',
    paper_url VARCHAR(512) DEFAULT NULL COMMENT '论文链接',
    source_url VARCHAR(512) DEFAULT NULL COMMENT '源码或项目链接',
    capabilities JSON DEFAULT NULL COMMENT '核心能力数组',
    applicable_scenarios JSON DEFAULT NULL COMMENT '适用场景数组',
    advantages JSON DEFAULT NULL COMMENT '优势数组',
    limitations JSON DEFAULT NULL COMMENT '局限数组',
    supported_input_modes JSON DEFAULT NULL COMMENT '支持输入方式数组',
    reference_info JSON DEFAULT NULL COMMENT '参考信息，指标来源等',
    marketplace_visible TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否在模型广场展示',
    is_featured TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否推荐展示',
    sort_order INT NOT NULL DEFAULT 999 COMMENT '模型广场排序',
    created_by BIGINT DEFAULT NULL COMMENT '创建者用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_model_code (model_code),
    KEY idx_model_type (model_type),
    KEY idx_model_status (status),
    KEY idx_model_marketplace (marketplace_visible, sort_order),

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
    include_weather TINYINT(1) DEFAULT NULL COMMENT '是否包含天气上下文',
    include_prediction TINYINT(1) DEFAULT NULL COMMENT '是否包含预测上下文',
    model_name VARCHAR(128) DEFAULT NULL COMMENT 'LLM模型名称',
    prompt_snapshot LONGTEXT DEFAULT NULL COMMENT 'Prompt快照',
    context_snapshot LONGTEXT DEFAULT NULL COMMENT '上下文快照',
    raw_response LONGTEXT DEFAULT NULL COMMENT '模型原始响应',
    risk_level VARCHAR(32) DEFAULT NULL COMMENT '风险等级',
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：PENDING，SUCCESS，FAILED',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_report_user (user_id),
    KEY idx_report_station (station_id),
    KEY idx_report_task (task_id),
    KEY idx_report_status (status),
    KEY idx_report_created_at (created_at),

    CONSTRAINT fk_report_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_report_station
        FOREIGN KEY (station_id) REFERENCES power_station(station_id),
    CONSTRAINT fk_report_task
        FOREIGN KEY (task_id) REFERENCES prediction_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合分析报告表';


-- =========================================================
-- 17A. Agent 会话表
-- =========================================================
CREATE TABLE agent_session (
    session_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent会话ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    title VARCHAR(255) NOT NULL COMMENT '会话标题',
    archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否归档',
    pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否固定',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE，CLOSED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_agent_session_user (user_id, updated_at),
    KEY idx_agent_session_archived (archived),

    CONSTRAINT fk_agent_session_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';


-- =========================================================
-- 17B. Agent 消息表
-- =========================================================
CREATE TABLE agent_message (
    message_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(32) NOT NULL COMMENT '角色：user，assistant，tool，system',
    content LONGTEXT DEFAULT NULL COMMENT '消息内容',
    metadata_json JSON DEFAULT NULL COMMENT '消息元数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_agent_message_session (session_id, created_at),
    KEY idx_agent_message_user (user_id),

    CONSTRAINT fk_agent_message_session
        FOREIGN KEY (session_id) REFERENCES agent_session(session_id),
    CONSTRAINT fk_agent_message_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';


-- =========================================================
-- 17C. Agent 工具调用表
-- =========================================================
CREATE TABLE agent_tool_call (
    tool_call_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工具调用ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    message_id BIGINT DEFAULT NULL COMMENT '触发消息ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    client_tool_call_id VARCHAR(64) NOT NULL COMMENT '前端展示用工具调用ID',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    display_name VARCHAR(128) DEFAULT NULL COMMENT '工具展示名称',
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
    KEY idx_agent_tool_user (user_id),
    KEY idx_agent_tool_status (status),

    CONSTRAINT fk_agent_tool_session
        FOREIGN KEY (session_id) REFERENCES agent_session(session_id),
    CONSTRAINT fk_agent_tool_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_agent_tool_message
        FOREIGN KEY (message_id) REFERENCES agent_message(message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具调用表';


-- =========================================================
-- 17D. Agent 用户确认表
-- =========================================================
CREATE TABLE agent_approval (
    approval_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '确认请求ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    tool_call_id BIGINT NOT NULL COMMENT '工具调用ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING，APPROVED，REJECTED，EXPIRED',
    reason VARCHAR(512) DEFAULT NULL COMMENT '确认原因',
    arguments_json JSON DEFAULT NULL COMMENT '待执行参数',
    comment VARCHAR(512) DEFAULT NULL COMMENT '用户备注',
    decided_at DATETIME DEFAULT NULL COMMENT '确认时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_agent_approval_user (user_id, status),
    KEY idx_agent_approval_session (session_id),
    KEY idx_agent_approval_tool (tool_call_id),

    CONSTRAINT fk_agent_approval_session
        FOREIGN KEY (session_id) REFERENCES agent_session(session_id),
    CONSTRAINT fk_agent_approval_tool
        FOREIGN KEY (tool_call_id) REFERENCES agent_tool_call(tool_call_id),
    CONSTRAINT fk_agent_approval_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent用户确认表';


-- =========================================================
-- 17E. Agent 长期记忆表
-- =========================================================
CREATE TABLE agent_memory (
    memory_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent记忆ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    memory_type VARCHAR(64) NOT NULL COMMENT '记忆类型',
    memory_key VARCHAR(128) NOT NULL COMMENT '同类型记忆键',
    value_json JSON NOT NULL COMMENT '记忆内容',
    source_message TEXT DEFAULT NULL COMMENT '来源消息',
    confidence DECIMAL(4,3) NOT NULL DEFAULT 0.500 COMMENT '置信度：0-1',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_agent_memory_user_type_key (user_id, memory_type, memory_key),
    KEY idx_agent_memory_user_type (user_id, memory_type, enabled),
    KEY idx_agent_memory_updated (updated_at),

    CONSTRAINT fk_agent_memory_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent长期记忆表';


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
-- 19. 开放平台钱包账户表
-- =========================================================
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

    CONSTRAINT fk_wallet_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台钱包账户表';


-- =========================================================
-- 20. 开放平台充值订单表
-- =========================================================
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

    CONSTRAINT fk_recharge_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_recharge_account
        FOREIGN KEY (account_id) REFERENCES open_wallet_account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台充值订单表';


-- =========================================================
-- 21. 开放平台钱包流水表
-- =========================================================
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

    CONSTRAINT fk_wallet_record_user
        FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_wallet_record_account
        FOREIGN KEY (account_id) REFERENCES open_wallet_account(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台钱包流水表';


-- =========================================================
-- 22. API 调用日志表
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

    input_tokens BIGINT DEFAULT NULL COMMENT '输入Token数',
    output_tokens BIGINT DEFAULT NULL COMMENT '输出Token数',
    total_tokens BIGINT DEFAULT NULL COMMENT '总Token数',

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
    object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS ObjectKey',
    content_type VARCHAR(100) DEFAULT NULL COMMENT 'MIME类型',
    file_status VARCHAR(20) NOT NULL DEFAULT 'BOUND' COMMENT 'TEMP/BOUND/DELETED',
    biz_id BIGINT DEFAULT NULL COMMENT '业务对象ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_file_owner (owner_user_id),
    KEY idx_file_business (business_type),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_biz (business_type, biz_id),

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
    cover_file_id BIGINT DEFAULT NULL COMMENT '封面文件ID',

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
    KEY idx_news_status_publish (status, published_at),
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


-- 所有建表完成，恢复外键校验
SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 23. 初始化全站公告
-- 全站公告统一使用 NOTICE + ALL；publisher_id 留空，避免依赖固定管理员账号。
-- 新闻内容由采集脚本写入 NEWS 类型，不在初始化脚本中预置。
-- =========================================================
INSERT INTO news (
    title,
    summary,
    content,
    news_type,
    target_role,
    status,
    publisher_id,
    published_at
)
VALUES
(
    '欢迎使用光伏预测平台',
    '平台提供电站管理、气象查询、功率预测和分析报告等服务。',
    '欢迎使用光伏预测平台。你可以在平台中管理光伏电站、查看天气信息、调用预测模型，并生成光伏功率分析报告。首次使用时，请先完善个人信息和电站基础数据，以便获得更准确的预测结果。',
    'NOTICE',
    'ALL',
    'PUBLISHED',
    NULL,
    CURRENT_TIMESTAMP
),
(
    '关于预测结果的使用说明',
    '模型预测结果仅供生产调度和趋势研判参考，请结合现场数据综合判断。',
    '平台展示的光伏功率预测结果会受到天气变化、设备状态、数据完整性和模型版本等因素影响。预测结果仅供生产调度、运行分析和趋势研判参考，不应作为唯一决策依据。发现数据明显异常时，请先检查电站数据和气象数据是否完整。',
    'NOTICE',
    'ALL',
    'PUBLISHED',
    NULL,
    CURRENT_TIMESTAMP
),
(
    '平台数据更新说明',
    '天气、电站和预测数据会按服务周期更新，短暂延迟不影响历史数据保存。',
    '平台会按照各数据服务的更新周期同步天气、电站运行和预测结果。受上游服务或网络波动影响，部分实时数据可能出现短暂延迟。系统恢复后会继续同步，已有历史数据不会因此丢失。',
    'NOTICE',
    'ALL',
    'PUBLISHED',
    NULL,
    CURRENT_TIMESTAMP
),
(
    '账号与 API Key 安全提示',
    '请妥善保管登录密码和 API Key，不要在公开代码或聊天记录中泄露凭证。',
    '登录密码和 API Key 都属于敏感凭证。请勿将其提交到公开代码仓库，也不要通过截图、聊天记录或公共文档传播。如发现凭证可能泄露，请及时修改密码、停用原 API Key，并创建新的调用凭证。',
    'NOTICE',
    'ALL',
    'PUBLISHED',
    NULL,
    CURRENT_TIMESTAMP
);


-- =========================================================
-- 24. 初始化基础角色
-- =========================================================
INSERT INTO sys_role (role_code, role_name, description)
VALUES
('ADMIN', '管理员', '拥有系统管理权限'),
('USER', '普通用户', '可以查看电站、调用模型、查看预测结果'),
('API_USER', 'API用户', '可以申请API Key并调用开放模型接口');


-- =========================================================
-- 25. 初始化模型信息
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
),
(
    'CNN_MLP',
    'CNN-MLP融合光伏功率预测模型',
    'FUSION',
    'v1.0',
    30,
    60,
    6,
    5,
    'CNN_MLP',
    '/model-api/predict',
    'OFFLINE',
    'CNN-MLP 图像+数值融合预测模型，调用时需要显式云图输入'
),
(
    '3DCNN_LSTM',
    '3DCNN-LSTM融合光伏功率预测模型',
    'FUSION',
    'v1.0',
    30,
    60,
    6,
    5,
    '3DCNN_LSTM',
    '/model-api/predict',
    'OFFLINE',
    '3DCNN-LSTM 三维CNN+LSTM时空融合模型，调用时需要显式云图输入'
),
(
    'ConvLSTM_LSTM',
    'ConvLSTM-LSTM融合光伏功率预测模型',
    'FUSION',
    'v1.0',
    30,
    60,
    6,
    5,
    'ConvLSTM_LSTM',
    '/model-api/predict',
    'OFFLINE',
    'ConvLSTM-LSTM 卷积LSTM+数值LSTM融合模型，调用时需要显式云图输入'
),
(
    'SimVP_gSTA',
    'SimVP时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'SimVP_gSTA',
    '/model-api/predict',
    'OFFLINE',
    'SimVP (gSTA) 视频预测模型，调用时需要显式云图输入'
),
(
    'TAU',
    'TAU时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'TAU',
    '/model-api/predict',
    'OFFLINE',
    'TAU 时空聚合注意力视频预测模型，调用时需要显式云图输入'
),
(
    'ConvLSTM',
    'ConvLSTM时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'ConvLSTM',
    '/model-api/predict',
    'OFFLINE',
    'ConvLSTM 卷积长短期记忆时空模型，调用时需要显式云图输入'
),
(
    'PredRNN',
    'PredRNN时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'PredRNN',
    '/model-api/predict',
    'OFFLINE',
    'PredRNN 时空预测递归网络，调用时需要显式云图输入'
),
(
    'PredRNN++',
    'PredRNN++时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'PredRNN++',
    '/model-api/predict',
    'OFFLINE',
    'PredRNN++ 时空预测递归网络增强版，调用时需要显式云图输入'
),
(
    'E3D_LSTM',
    'E3D-LSTM时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'E3D_LSTM',
    '/model-api/predict',
    'OFFLINE',
    'E3D-LSTM 三维门控时空记忆网络，调用时需要显式云图输入'
),
(
    'swinLSTM',
    'SwinLSTM时空云图预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'swinLSTM',
    '/model-api/predict',
    'OFFLINE',
    'SwinLSTM Swin Transformer + LSTM 时空模型，调用时需要显式云图输入'
),
(
    'SUNSET',
    'SUNSET太阳能预测模型',
    'MULTIMODAL',
    'v1.0',
    30,
    60,
    6,
    5,
    'SUNSET',
    '/model-api/predict',
    'OFFLINE',
    'SUNSET 斯坦福 CNN 太阳能预测模型，调用时需要显式云图输入'
);


-- Consolidated from backend/src/main/resources/sql/agent_tables_migration.sql

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

CREATE TABLE IF NOT EXISTS agent_memory (
    memory_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Agent记忆ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    memory_type VARCHAR(64) NOT NULL COMMENT '记忆类型',
    memory_key VARCHAR(128) NOT NULL COMMENT '同类型记忆键',
    value_json JSON NOT NULL COMMENT '记忆内容',
    source_message TEXT DEFAULT NULL COMMENT '来源消息',
    confidence DECIMAL(4,3) NOT NULL DEFAULT 0.500 COMMENT '置信度：0-1',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_agent_memory_user_type_key (user_id, memory_type, memory_key),
    KEY idx_agent_memory_user_type (user_id, memory_type, enabled),
    KEY idx_agent_memory_updated (updated_at),
    CONSTRAINT fk_agent_memory_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent长期记忆表';


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

CREATE TABLE IF NOT EXISTS agent_run_event (
    event_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '运行事件ID',
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    message_id BIGINT DEFAULT NULL COMMENT '触发消息ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    sequence_no INT NOT NULL COMMENT '运行内序号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    node_name VARCHAR(100) DEFAULT NULL COMMENT '框架节点',
    tool_name VARCHAR(100) DEFAULT NULL COMMENT '业务工具名',
    tool_call_id BIGINT DEFAULT NULL COMMENT '工具调用ID',
    payload_json JSON DEFAULT NULL COMMENT '事件载荷',
    event_status VARCHAR(30) DEFAULT NULL COMMENT '事件状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_agent_run_event_sequence (run_id, sequence_no),
    KEY idx_agent_run_event_run (run_id, created_at),
    KEY idx_agent_run_event_session (session_id, created_at),
    KEY idx_agent_run_event_tool_call (tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent运行事件表';

DROP PROCEDURE IF EXISTS add_agent_run_event_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_agent_run_event_column_if_missing(
    IN p_column_name VARCHAR(64),
    IN p_alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_run_event' AND COLUMN_NAME = p_column_name
    ) THEN
        SET @agent_event_alter_sql = p_alter_sql;
        PREPARE agent_event_alter_stmt FROM @agent_event_alter_sql;
        EXECUTE agent_event_alter_stmt;
        DEALLOCATE PREPARE agent_event_alter_stmt;
    END IF;
END//
DELIMITER ;
CALL add_agent_run_event_column_if_missing('sequence_no',
    'ALTER TABLE agent_run_event ADD COLUMN sequence_no INT NOT NULL DEFAULT 0 AFTER user_id');
CALL add_agent_run_event_column_if_missing('node_name',
    'ALTER TABLE agent_run_event ADD COLUMN node_name VARCHAR(100) NULL AFTER event_type');
CALL add_agent_run_event_column_if_missing('tool_name',
    'ALTER TABLE agent_run_event ADD COLUMN tool_name VARCHAR(100) NULL AFTER node_name');
CALL add_agent_run_event_column_if_missing('tool_call_id',
    'ALTER TABLE agent_run_event ADD COLUMN tool_call_id BIGINT NULL AFTER tool_name');
CALL add_agent_run_event_column_if_missing('event_status',
    'ALTER TABLE agent_run_event ADD COLUMN event_status VARCHAR(30) NULL AFTER payload_json');
DROP PROCEDURE IF EXISTS add_agent_run_event_column_if_missing;


-- Consolidated from backend/src/main/resources/sql/api_usage_migration.sql

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_api_usage_schema$$
CREATE PROCEDURE migrate_api_usage_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND COLUMN_NAME = 'input_tokens'
    ) THEN
        ALTER TABLE api_call_log ADD COLUMN input_tokens BIGINT DEFAULT NULL COMMENT '输入Token数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND COLUMN_NAME = 'output_tokens'
    ) THEN
        ALTER TABLE api_call_log ADD COLUMN output_tokens BIGINT DEFAULT NULL COMMENT '输出Token数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND COLUMN_NAME = 'total_tokens'
    ) THEN
        ALTER TABLE api_call_log ADD COLUMN total_tokens BIGINT DEFAULT NULL COMMENT '总Token数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND INDEX_NAME = 'idx_call_user_time'
    ) THEN
        ALTER TABLE api_call_log ADD INDEX idx_call_user_time (user_id, request_time);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND INDEX_NAME = 'idx_call_user_model'
    ) THEN
        ALTER TABLE api_call_log ADD INDEX idx_call_user_model (user_id, model_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_call_log' AND INDEX_NAME = 'idx_call_user_key'
    ) THEN
        ALTER TABLE api_call_log ADD INDEX idx_call_user_key (user_id, api_key_id);
    END IF;
END$$

CALL migrate_api_usage_schema()$$
DROP PROCEDURE migrate_api_usage_schema$$

DELIMITER ;


-- Consolidated from backend/src/main/resources/sql/backfill_external_station_coordinates.sql

UPDATE external_pv_station SET latitude = -27.614000, longitude = 152.973000 WHERE external_system_id = 85414;   -- RPM Building 37 (4110 Ipswich)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 76824;   -- Symbio Laboratories - 52 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 77009;   -- Cook Medical 61/1 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 76363;   -- Symbio Laboratories - 44 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 73718;   -- Cook Medical 61/2 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 34257;   -- Cook Medical Australia (4113)
UPDATE external_pv_station SET latitude = -27.250000, longitude = 153.020000 WHERE external_system_id = 77260;   -- Qtank (4500)
UPDATE external_pv_station SET latitude = -27.580000, longitude = 152.940000 WHERE external_system_id = 83299;   -- Pillow Talk Northolt (4076)
UPDATE external_pv_station SET latitude = -27.580000, longitude = 152.940000 WHERE external_system_id = 85307;   -- Pillow Talk Limestone (4076)
UPDATE external_pv_station SET latitude = -25.540000, longitude = 152.700000 WHERE external_system_id = 61555;   -- Victory Church Maryborough (4650)
UPDATE external_pv_station SET latitude = -27.380000, longitude = 153.030000 WHERE external_system_id = 60081;   -- Estilo on Kittyhawk (4032)
UPDATE external_pv_station SET latitude = -27.380000, longitude = 153.030000 WHERE external_system_id = 112897;  -- Pechey Sigenergy (4032)
UPDATE external_pv_station SET latitude = -27.610000, longitude = 152.850000 WHERE external_system_id = 78034;   -- Refresh Waters (4300)
UPDATE external_pv_station SET latitude = -23.380000, longitude = 150.510000 WHERE external_system_id = 78569;   -- Rockhampton Baptist Tabernacle (4701)
UPDATE external_pv_station SET latitude = -16.920000, longitude = 145.770000 WHERE external_system_id = 101178;  -- Autobarn Cairns (4870)
UPDATE external_pv_station SET latitude = -27.070000, longitude = 152.960000 WHERE external_system_id = 47759;   -- Beecham_Holden (4510)
UPDATE external_pv_station SET latitude = -27.640000, longitude = 153.130000 WHERE external_system_id = 63656;   -- Highpoint Business Centre (4127)
UPDATE external_pv_station SET latitude = -27.430000, longitude = 153.000000 WHERE external_system_id = 57262;   -- Enoggera Self Storage (4051)
UPDATE external_pv_station SET latitude = -27.430000, longitude = 153.000000 WHERE external_system_id = 59226;   -- IGear (4051)
UPDATE external_pv_station SET latitude = -27.510000, longitude = 153.010000 WHERE external_system_id = 82727;   -- Renovare Yeronga (4104)
UPDATE external_pv_station SET latitude = -26.720000, longitude = 153.120000 WHERE external_system_id = 108252;  -- Zinc Bokarina Enphase (4575)
UPDATE external_pv_station SET latitude = -26.720000, longitude = 153.120000 WHERE external_system_id = 107468;  -- Revive Birtinya (4575)
UPDATE external_pv_station SET latitude = -27.380000, longitude = 153.050000 WHERE external_system_id = 88347;   -- Village Central Nundah (4012)
UPDATE external_pv_station SET latitude = -20.010000, longitude = 148.250000 WHERE external_system_id = 107648;  -- CE Big 4 (4802)
UPDATE external_pv_station SET latitude = -27.320000, longitude = 153.040000 WHERE external_system_id = 66682;   -- Bracken Ridge Baptist Church (4017)

UPDATE external_pv_station SET latitude = -31.460000, longitude = 152.730000 WHERE external_system_id = 81697;   -- Woodrose (2446)
UPDATE external_pv_station SET latitude = -31.100000, longitude = 150.930000 WHERE external_system_id = 56089;   -- Careys Belmore St (2340)
UPDATE external_pv_station SET latitude = -31.100000, longitude = 150.930000 WHERE external_system_id = 56088;   -- Careys Main Office (2340)
UPDATE external_pv_station SET latitude = -32.910000, longitude = 151.750000 WHERE external_system_id = 83168;   -- Hunter H2O (2304)
UPDATE external_pv_station SET latitude = -28.810000, longitude = 153.280000 WHERE external_system_id = 64847;   -- UCRHSolar (2480)

UPDATE external_pv_station SET latitude = -27.960000, longitude = 153.370000 WHERE external_system_id = 71111;   -- Arcare Parkwood (4214)
UPDATE external_pv_station SET latitude = -27.680000, longitude = 153.100000 WHERE external_system_id = 84975;   -- Pro Tech Distributions Unit 1 (4132)

UPDATE external_pv_station SET latitude = -37.740000, longitude = 142.020000 WHERE external_system_id = 55018;   -- Wannon Water Hamilton WTP (3300)
UPDATE external_pv_station SET latitude = -38.370000, longitude = 142.470000 WHERE external_system_id = 51616;   -- Wannon Water Gateway HQ (3280)

UPDATE external_pv_station SET latitude = 35.680000, longitude = 139.760000 WHERE external_system_id = 75956;    -- HISA3 (Japan)

UPDATE external_pv_station SET latitude = 4.180000, longitude = 73.510000 WHERE external_system_id = 76845;      -- Alila Kothaifaru (Maldives)

UPDATE external_pv_station SET latitude = 13.760000, longitude = 100.500000 WHERE external_system_id = 32225;    -- FRECON-APY_RoofTop#2 (Thailand)



-- Consolidated from backend/src/main/resources/sql/model_marketplace_metadata_19_models.sql

SET NAMES utf8mb4;
USE pv_platform;

DROP PROCEDURE IF EXISTS pv_assert_marketplace_models;
DELIMITER $$
CREATE PROCEDURE pv_assert_marketplace_models()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_count
      FROM model_info
     WHERE model_code IN ('DLinear', 'PatchTST', 'iTransformer', 'TimeXer', 'TimeMixer', 'TSMixer', 'Transformer', 'CNN_MLP', 'CNN_LSTM', '3DCNN_LSTM', 'ConvLSTM_LSTM', 'SimVP_gSTA', 'TAU', 'ConvLSTM', 'PredRNN', 'PredRNN++', 'E3D_LSTM', 'swinLSTM', 'SUNSET');

    IF v_count <> 19 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'model_info 中未找到完整的19个目标模型，请先执行原 init.sql 并检查 model_code';
    END IF;
END$$
DELIMITER ;

CALL pv_assert_marketplace_models();
DROP PROCEDURE IF EXISTS pv_assert_marketplace_models;

DROP PROCEDURE IF EXISTS pv_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE pv_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table_name
       AND COLUMN_NAME = p_column_name;

    IF v_exists = 0 THEN
        SET @ddl_sql = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD COLUMN ',
            p_column_definition
        );
        PREPARE ddl_stmt FROM @ddl_sql;
        EXECUTE ddl_stmt;
        DEALLOCATE PREPARE ddl_stmt;
    END IF;
END$$
DELIMITER ;

CALL pv_add_column_if_missing(
    'model_info', 'short_description',
    '`short_description` VARCHAR(255) DEFAULT NULL COMMENT ''模型广场卡片简要说明'' AFTER `description`'
);
CALL pv_add_column_if_missing(
    'model_info', 'tags',
    '`tags` JSON DEFAULT NULL COMMENT ''模型标签JSON数组'' AFTER `short_description`'
);
CALL pv_add_column_if_missing(
    'model_info', 'model_family',
    '`model_family` VARCHAR(128) DEFAULT NULL COMMENT ''模型架构家族'' AFTER `tags`'
);
CALL pv_add_column_if_missing(
    'model_info', 'provider',
    '`provider` VARCHAR(255) DEFAULT NULL COMMENT ''模型来源项目或机构'' AFTER `model_family`'
);
CALL pv_add_column_if_missing(
    'model_info', 'release_year',
    '`release_year` SMALLINT DEFAULT NULL COMMENT ''论文或模型发布年份'' AFTER `provider`'
);
CALL pv_add_column_if_missing(
    'model_info', 'paper_title',
    '`paper_title` VARCHAR(600) DEFAULT NULL COMMENT ''参考论文标题'' AFTER `release_year`'
);
CALL pv_add_column_if_missing(
    'model_info', 'paper_url',
    '`paper_url` VARCHAR(512) DEFAULT NULL COMMENT ''参考论文地址'' AFTER `paper_title`'
);
CALL pv_add_column_if_missing(
    'model_info', 'source_url',
    '`source_url` VARCHAR(512) DEFAULT NULL COMMENT ''开源实现或项目地址'' AFTER `paper_url`'
);
CALL pv_add_column_if_missing(
    'model_info', 'capabilities',
    '`capabilities` JSON DEFAULT NULL COMMENT ''模型能力JSON数组'' AFTER `source_url`'
);
CALL pv_add_column_if_missing(
    'model_info', 'applicable_scenarios',
    '`applicable_scenarios` JSON DEFAULT NULL COMMENT ''适用场景JSON数组'' AFTER `capabilities`'
);
CALL pv_add_column_if_missing(
    'model_info', 'advantages',
    '`advantages` JSON DEFAULT NULL COMMENT ''模型优势JSON数组'' AFTER `applicable_scenarios`'
);
CALL pv_add_column_if_missing(
    'model_info', 'limitations',
    '`limitations` JSON DEFAULT NULL COMMENT ''限制与注意事项JSON数组'' AFTER `advantages`'
);
CALL pv_add_column_if_missing(
    'model_info', 'supported_input_modes',
    '`supported_input_modes` JSON DEFAULT NULL COMMENT ''支持的输入方式JSON数组'' AFTER `limitations`'
);
CALL pv_add_column_if_missing(
    'model_info', 'reference_info',
    '`reference_info` JSON DEFAULT NULL COMMENT ''论文与开源项目参考配置JSON'' AFTER `supported_input_modes`'
);
CALL pv_add_column_if_missing(
    'model_info', 'marketplace_visible',
    '`marketplace_visible` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否在模型广场展示：1展示，0隐藏'' AFTER `reference_info`'
);
CALL pv_add_column_if_missing(
    'model_info', 'is_featured',
    '`is_featured` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否推荐模型：1推荐，0普通'' AFTER `marketplace_visible`'
);
CALL pv_add_column_if_missing(
    'model_info', 'sort_order',
    '`sort_order` INT NOT NULL DEFAULT 0 COMMENT ''模型广场排序值，越小越靠前'' AFTER `is_featured`'
);

DROP PROCEDURE IF EXISTS pv_add_column_if_missing;

START TRANSACTION;

UPDATE model_info
SET
    `model_name` = 'DLinear线性分解时序预测模型',
    `model_type` = 'NUMERIC',
    `description` = 'DLinear先通过移动平均等方式把时间序列拆分为趋势项与季节项，再使用独立线性层完成预测。它强调简单线性结构在长序列预测中的竞争力。本平台将其用于历史光伏功率的多步预测，适合作为低成本基线和快速对照模型。',
    `short_description` = '将趋势项与季节项分解后分别线性预测的轻量级时序基线。',
    `tags` = '["数值预测","线性模型","趋势分解","轻量级","多步预测"]',
    `model_family` = 'Linear / Decomposition',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2023,
    `paper_title` = 'Are Transformers Effective for Time Series Forecasting?',
    `paper_url` = 'https://arxiv.org/abs/2205.13504',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/DLinear.py',
    `capabilities` = '["多步功率预测","趋势与季节分量建模","单变量与多变量时序处理"]',
    `applicable_scenarios` = '["计算资源受限的快速预测","建立基准结果","周期性较明显的光伏功率序列"]',
    `advantages` = '["参数量小，训练和推理速度快","结构简单，结果较易解释","适合作为复杂模型的基线"]',
    `limitations` = '["对强非线性和突发云量变化的表达能力有限","预测效果依赖序列的趋势和周期结构"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","MANUAL_INPUT","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"display_note":"当前平台默认使用最近30分钟功率序列；实际字段与归一化方式以模型服务接口为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"architecture":"decomposition plus linear layers","implementation_note":"模型广场信息来自论文与TSLib实现；当前平台训练数据和参数可能不同"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 10,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'DLinear';

UPDATE model_info
SET
    `model_name` = 'PatchTST分块Transformer时序预测模型',
    `model_type` = 'NUMERIC',
    `description` = 'PatchTST把连续时间点切分成若干时间补丁，以减少序列长度并提取局部模式，同时采用通道独立设计分别处理不同变量。该结构兼顾局部变化与较长时间依赖，适合从历史功率和气象序列中学习稳定的短期演化规律。',
    `short_description` = '把时间序列切分为补丁，并以通道独立方式建模长程依赖。',
    `tags` = '["数值预测","Transformer","Patch","通道独立","长程依赖"]',
    `model_family` = 'Patch Transformer',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2023,
    `paper_title` = 'A Time Series is Worth 64 Words: Long-term Forecasting with Transformers',
    `paper_url` = 'https://openreview.net/forum?id=Jbdc0vTOcol',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/PatchTST.py',
    `capabilities` = '["多步功率预测","时间补丁表示","长程依赖建模","多变量时序处理"]',
    `applicable_scenarios` = '["较长历史窗口预测","多变量功率与气象联合建模","需要兼顾精度与计算效率的场景"]',
    `advantages` = '["补丁化降低注意力计算负担","可同时捕捉局部模式和长程依赖","通道独立设计具有较好的迁移性"]',
    `limitations` = '["补丁长度等超参数对效果影响较大","极短时突变可能在分块过程中被平滑"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"display_note":"当前平台默认使用最近30分钟功率序列；实际字段与归一化方式以模型服务接口为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"key_design":["patching","channel independence","Transformer encoder"],"implementation_note":"当前平台预测窗口以服务配置为准"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 20,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'PatchTST';

UPDATE model_info
SET
    `model_name` = 'iTransformer光伏功率预测模型',
    `model_type` = 'NUMERIC',
    `description` = 'iTransformer对传统Transformer的输入组织方式进行反转，把每个变量的完整历史序列编码为Token，并通过自注意力学习变量之间的相关性。对于受辐照度、温度、湿度和风速等因素共同影响的光伏功率预测，该结构适合建模跨变量依赖。',
    `short_description` = '将变量而非时间点作为Token，重点学习多变量之间的相关关系。',
    `tags` = '["数值预测","Transformer","变量Token","多变量","相关性建模"]',
    `model_family` = 'Inverted Transformer',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2024,
    `paper_title` = 'iTransformer: Inverted Transformers Are Effective for Time Series Forecasting',
    `paper_url` = 'https://arxiv.org/abs/2310.06625',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/iTransformer.py',
    `capabilities` = '["多步功率预测","跨变量相关性学习","多变量时序建模","长历史窗口处理"]',
    `applicable_scenarios` = '["功率与多种气象变量联合预测","变量相关性明显的电站数据","长短期功率预测"]',
    `advantages` = '["能直接学习变量间依赖","适合高维多变量时序","注意力关系具有一定可解释性"]',
    `limitations` = '["变量较少时结构优势可能不明显","对缺失值、量纲和归一化处理较敏感"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"display_note":"当前平台默认使用最近30分钟功率序列；实际字段与归一化方式以模型服务接口为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"key_design":["variates as tokens","inverted self-attention"],"implementation_note":"若平台仅输入功率单变量，其跨变量建模优势会受限"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 30,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'iTransformer';

UPDATE model_info
SET
    `model_name` = 'TimeXer外生变量增强时序预测模型',
    `model_type` = 'NUMERIC',
    `description` = 'TimeXer面向带外生变量的预测任务，分别构造内生目标序列的补丁级表示和外生变量的变量级表示，并利用内生全局Token连接两类信息。对于光伏场景，可将历史功率作为预测目标，并融合辐照度、温度、湿度、风速和时间特征。',
    `short_description` = '通过内生序列补丁、外生变量表示和全局Token融合外部信息。',
    `tags` = '["数值预测","Transformer","外生变量","气象融合","多步预测"]',
    `model_family` = 'Exogenous Transformer',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2024,
    `paper_title` = 'TimeXer: Empowering Transformers for Time Series Forecasting with Exogenous Variables',
    `paper_url` = 'https://arxiv.org/abs/2402.19072',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/TimeXer.py',
    `capabilities` = '["外生变量增强预测","多步功率预测","功率与气象信息融合","跨变量相关性建模"]',
    `applicable_scenarios` = '["天气因素影响明显的功率预测","具备较完整气象数据的电站","短期电力与能源预测"]',
    `advantages` = '["专门面向外生变量预测范式","能区分目标序列与辅助变量","适合光伏等强天气驱动任务"]',
    `limitations` = '["外生变量质量不足时收益有限","需要保证未来可用特征不会造成数据泄漏"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"recommended_optional_fields":["irradiance_w_m2","temperature_c","humidity_percent","wind_speed_m_s","time_features"],"display_note":"TimeXer在提供高质量外生变量时更能发挥优势；实际字段以模型服务为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"key_design":["endogenous patch representation","exogenous variate representation","endogenous global token"],"implementation_note":"平台需区分历史可观测特征和预测时刻可用特征"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 40,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'TimeXer';

UPDATE model_info
SET
    `model_name` = 'TimeMixer多尺度时序预测模型',
    `model_type` = 'NUMERIC',
    `description` = 'TimeMixer使用多尺度下采样观察时间序列，并对不同尺度上的季节项和趋势项进行分解与混合。细粒度尺度更关注快速波动，粗粒度尺度更关注整体趋势，适合同时学习光伏功率的短时变化与日内演化。',
    `short_description` = '在多个时间尺度上分解并混合季节与趋势信息的全MLP模型。',
    `tags` = '["数值预测","MLP","多尺度","趋势季节分解","高效"]',
    `model_family` = 'Multiscale MLP',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2024,
    `paper_title` = 'TimeMixer: Decomposable Multiscale Mixing for Time Series Forecasting',
    `paper_url` = 'https://openreview.net/forum?id=7oLshfEIC2',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/TimeMixer.py',
    `capabilities` = '["多尺度功率预测","季节与趋势分解","长短期模式混合","多步预测"]',
    `applicable_scenarios` = '["同时存在快速波动与缓慢趋势的功率序列","需要高效推理的多步预测","多时间尺度能源数据分析"]',
    `advantages` = '["全MLP结构便于并行计算","能综合细粒度与粗粒度信息","在精度和效率之间较均衡"]',
    `limitations` = '["下采样尺度和分解方式需要调参","对极端突变的响应仍依赖训练样本覆盖"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"display_note":"当前平台默认使用最近30分钟功率序列；实际字段与归一化方式以模型服务接口为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"key_design":["multiscale downsampling","past decomposable mixing","future multipredictor mixing"],"implementation_note":"当前平台的尺度配置由模型服务决定"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 50,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'TimeMixer';

UPDATE model_info
SET
    `model_name` = 'TSMixer全MLP时序预测模型',
    `model_type` = 'NUMERIC',
    `description` = 'TSMixer使用多层感知机分别沿时间维与变量维进行混合，不依赖循环网络或自注意力。它结构规整、并行度高，可作为光伏功率多变量预测中的高效模型，并适合与Transformer和线性模型进行对照。',
    `short_description` = '沿时间维和变量维交替混合信息的纯MLP预测架构。',
    `tags` = '["数值预测","MLP","时间混合","变量混合","轻量级"]',
    `model_family` = 'All-MLP',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2023,
    `paper_title` = 'TSMixer: An All-MLP Architecture for Time Series Forecasting',
    `paper_url` = 'https://arxiv.org/abs/2303.06053',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/TSMixer.py',
    `capabilities` = '["多步功率预测","时间维信息混合","变量维信息混合","多变量预测"]',
    `applicable_scenarios` = '["需要快速训练与部署的场景","结构化多变量时序","中短历史窗口的功率预测"]',
    `advantages` = '["网络结构简单且并行友好","计算开销通常低于注意力模型","便于工程部署"]',
    `limitations` = '["缺少显式注意力机制","面对超长依赖或复杂非平稳模式时可能受限"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"display_note":"当前平台默认使用最近30分钟功率序列；实际字段与归一化方式以模型服务接口为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"key_design":["time mixing MLP","feature mixing MLP"],"implementation_note":"模型效果依赖变量组织和归一化方式"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 60,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'TSMixer';

UPDATE model_info
SET
    `model_name` = 'Transformer时序预测基线模型',
    `model_type` = 'NUMERIC',
    `description` = '标准Transformer通过多头自注意力和前馈网络建模序列中不同时间位置的全局关系。在本平台中，它作为经典时序预测基线，用于比较补丁化、变量反转和多尺度等新型结构带来的改进。',
    `short_description` = '使用自注意力捕捉时间序列全局依赖的经典预测基线。',
    `tags` = '["数值预测","Transformer","自注意力","编码器解码器","基线模型"]',
    `model_family` = 'Transformer',
    `provider` = 'THUML Time-Series-Library',
    `release_year` = 2017,
    `paper_title` = 'Attention Is All You Need',
    `paper_url` = 'https://arxiv.org/abs/1706.03762',
    `source_url` = 'https://github.com/thuml/Time-Series-Library/blob/main/models/Transformer.py',
    `capabilities` = '["多步功率预测","全局时间依赖建模","多变量序列处理","编码器解码器预测"]',
    `applicable_scenarios` = '["通用时序预测基线","需要全局依赖建模的功率序列","模型结构对比实验"]',
    `advantages` = '["架构成熟、资料丰富","能并行处理序列","可捕捉远距离依赖"]',
    `limitations` = '["长序列注意力计算开销较高","并非专为时间序列设计，通常需要较细致调参"]',
    `supported_input_modes` = '["STATION_HISTORY","FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"numeric_time_series","history_points":30,"history_minutes":30,"sampling_interval_seconds":60,"required_fields":["timestamp","power_kw"],"optional_fields":["temperature_c","irradiance_w_m2","humidity_percent","wind_speed_m_s"]},"display_note":"当前平台默认使用最近30分钟功率序列；实际字段与归一化方式以模型服务接口为准"}',
    `output_schema` = '{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"}',
    `reference_info` = '{"key_design":["multi-head self-attention","encoder-decoder"],"implementation_note":"该条目为TSLib中的时间序列预测实现"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 70,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'Transformer';

UPDATE model_info
SET
    `model_name` = 'CNN-MLP天空图像与数值融合预测模型',
    `model_type` = 'FUSION',
    `description` = '该模型对应参考论文中的CNN方案：卷积网络负责从半球天空图像中提取云层和太阳邻域特征，辅助分支处理历史辐照度与太阳位置等数值变量，随后在全连接层中融合并回归未来辐照度。本平台将其扩展为图像与历史功率联合预测选项。',
    `short_description` = '以CNN提取天空图像特征，再与辅助数值特征通过全连接网络融合。',
    `tags` = '["图像数值融合","CNN","MLP","天空图像","辐照度预测"]',
    `model_family` = '2D CNN + MLP',
    `provider` = 'University of Cambridge / ENGIE Lab CRIGEN',
    `release_year` = 2021,
    `paper_title` = 'Benchmarking of deep learning irradiance forecasting models from sky images – An in-depth analysis',
    `paper_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `source_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `capabilities` = '["天空图像特征提取","图像与数值特征融合","短时辐照度或功率回归","多源输入预测"]',
    `applicable_scenarios` = '["具备全天空相机的电站","短临辐照度预测","云层空间结构较重要的场景"]',
    `advantages` = '["结构直观，图像与数值分支职责清晰","训练与部署复杂度低于时空递归模型","适合作为视觉融合基线"]',
    `limitations` = '["对图像序列的时间动态编码较弱","参考研究发现对突发遮日事件仍可能出现时间滞后"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"image_numeric_fusion","sky_image_sequence_required":true,"numeric_history_required":true,"history_minutes":30,"sampling_interval_seconds":60},"display_note":"当前平台调用时需要显式提供云图或天空图像；实际张量形状以模型服务接口为准","reference_setup":{"context_minutes":8,"frame_interval_minutes":2,"image_pairs_per_time":["short exposure","long exposure"],"auxiliary_features":["GHI","SZA","sin/cos SZA","SAA","sin/cos SAA"]}}',
    `output_schema` = '{"platform_output":{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"},"reference_output":{"target":"GHI","mode":"single-horizon regression","tested_horizons_minutes":[2,6,10,20,30]}}',
    `reference_info` = '{"paper_model_name":"CNN","reference_dataset":"SIRTA 2017-2019","reference_image_size":"128x128 grayscale after preprocessing","warning":"论文指标仅作架构参考，不代表当前平台部署结果"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 110,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'CNN_MLP';

UPDATE model_info
SET
    `model_name` = 'CNN-LSTM天空图像时序融合预测模型',
    `model_type` = 'FUSION',
    `description` = '该模型对应参考论文中的CNN加LSTM方案。每个时刻的天空图像由共享CNN编码成一维特征，随后通过LSTM提取图像序列中的时间变化，并与辅助数值特征融合后预测辐照度。相比仅使用CNN的方案，它更适合建模云层移动和连续天气变化。',
    `short_description` = '先用共享CNN编码逐帧图像，再由LSTM汇总云层随时间的变化。',
    `tags` = '["图像数值融合","CNN","LSTM","时序图像","辐照度预测"]',
    `model_family` = 'CNN + LSTM',
    `provider` = 'University of Cambridge / ENGIE Lab CRIGEN',
    `release_year` = 2021,
    `paper_title` = 'Benchmarking of deep learning irradiance forecasting models from sky images – An in-depth analysis',
    `paper_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `source_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `capabilities` = '["逐帧图像编码","云层时间动态建模","图像与数值融合","短时辐照度或功率预测"]',
    `applicable_scenarios` = '["云层移动明显的短临预测","连续天空图像可用的电站","需要兼顾空间纹理与时间变化的场景"]',
    `advantages` = '["能显式编码图像序列的时间关系","共享CNN减少逐帧编码参数","比纯CNN更适合连续云图"]',
    `limitations` = '["串行LSTM限制并行效率","长序列训练成本较高，且突发事件仍可能预测滞后"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"image_numeric_fusion","sky_image_sequence_required":true,"numeric_history_required":true,"history_minutes":30,"sampling_interval_seconds":60},"display_note":"当前平台调用时需要显式提供云图或天空图像；实际张量形状以模型服务接口为准","reference_setup":{"context_minutes":8,"frame_interval_minutes":2,"image_encoder":"shared 2D CNN","temporal_encoder":"LSTM","auxiliary_features":["GHI","solar angles"]}}',
    `output_schema` = '{"platform_output":{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"},"reference_output":{"target":"GHI","mode":"single-horizon regression","tested_horizons_minutes":[2,6,10,20,30]}}',
    `reference_info` = '{"paper_model_name":"CNN + LSTM","reference_dataset":"SIRTA 2017-2019","warning":"论文指标仅作架构参考，不代表当前平台部署结果"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 120,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'CNN_LSTM';

UPDATE model_info
SET
    `model_name` = '3D-CNN-LSTM天空图像时空融合预测模型',
    `model_type` = 'FUSION',
    `description` = '该模型对应参考论文中的3D-CNN方案。天空图像序列被组织为时间、高度和宽度构成的三维块，通过3D卷积联合提取空间与时间特征；辅助数值序列由LSTM编码，最后融合完成辐照度预测。它适合捕捉云层形态及其运动轨迹。',
    `short_description` = '用3D卷积联合提取图像序列的空间纹理与时间运动信息。',
    `tags` = '["图像数值融合","3D-CNN","LSTM","时空建模","天空图像"]',
    `model_family` = '3D CNN + LSTM',
    `provider` = 'University of Cambridge / ENGIE Lab CRIGEN',
    `release_year` = 2021,
    `paper_title` = 'Benchmarking of deep learning irradiance forecasting models from sky images – An in-depth analysis',
    `paper_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `source_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `capabilities` = '["空间与时间联合卷积","云层运动特征提取","辅助数值序列编码","短时辐照度或功率预测"]',
    `applicable_scenarios` = '["云层运动复杂的短临预测","连续图像帧较完整的场景","需要较强局部时空特征的任务"]',
    `advantages` = '["3D卷积直接联合建模空间和时间","对连续局部运动模式较敏感","适合图像序列预测与回归"]',
    `limitations` = '["显存和计算开销较大","固定时间卷积窗口对超长依赖建模有限"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"image_numeric_fusion","sky_image_sequence_required":true,"numeric_history_required":true,"history_minutes":30,"sampling_interval_seconds":60},"display_note":"当前平台调用时需要显式提供云图或天空图像；实际张量形状以模型服务接口为准","reference_setup":{"context_minutes":8,"frame_interval_minutes":2,"image_encoder":"2D spatial reduction plus 3D convolutions","auxiliary_encoder":"LSTM"}}',
    `output_schema` = '{"platform_output":{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"},"reference_output":{"target":"GHI","mode":"single-horizon regression","tested_horizons_minutes":[2,6,10,20,30]}}',
    `reference_info` = '{"paper_model_name":"3D-CNN","reference_dataset":"SIRTA 2017-2019","warning":"论文指标仅作架构参考，不代表当前平台部署结果"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 130,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = '3DCNN_LSTM';

UPDATE model_info
SET
    `model_name` = 'ConvLSTM-LSTM天空图像时空融合预测模型',
    `model_type` = 'FUSION',
    `description` = '该模型对应参考论文中的ConvLSTM方案。图像分支在保持二维特征图结构的同时，通过卷积门控记忆单元传递时间信息；数值分支使用LSTM编码历史辐照度和太阳位置，融合后预测未来辐照度。该结构在参考研究的10分钟RMSE预测技能上表现最好。',
    `short_description` = '以ConvLSTM保留二维空间结构，并用数值LSTM融合辅助序列。',
    `tags` = '["图像数值融合","ConvLSTM","LSTM","时空记忆","天空图像"]',
    `model_family` = 'ConvLSTM + LSTM',
    `provider` = 'University of Cambridge / ENGIE Lab CRIGEN',
    `release_year` = 2021,
    `paper_title` = 'Benchmarking of deep learning irradiance forecasting models from sky images – An in-depth analysis',
    `paper_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `source_url` = 'https://doi.org/10.1016/j.solener.2021.05.056',
    `capabilities` = '["二维时空记忆","云层运动与形态联合建模","图像与数值序列融合","短时辐照度或功率预测"]',
    `applicable_scenarios` = '["云层遮挡变化频繁的场景","需要较强时空记忆的短临预测","天空图像与数值数据同时可用"]',
    `advantages` = '["保持图像空间结构的同时建模时间依赖","适合连续云图与天气雷达类数据","参考研究中具有较好的综合预测技能"]',
    `limitations` = '["递归计算导致训练和推理并行度较低","参考研究指出峰值和突变预测仍可能滞后"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"image_numeric_fusion","sky_image_sequence_required":true,"numeric_history_required":true,"history_minutes":30,"sampling_interval_seconds":60},"display_note":"当前平台调用时需要显式提供云图或天空图像；实际张量形状以模型服务接口为准","reference_setup":{"context_minutes":8,"frame_interval_minutes":2,"image_encoder":"2D CNN plus ConvLSTM","auxiliary_encoder":"LSTM"}}',
    `output_schema` = '{"platform_output":{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"horizon_minutes":30,"unit":"kW"},"reference_output":{"target":"GHI","mode":"single-horizon regression","tested_horizons_minutes":[2,6,10,20,30]}}',
    `reference_info` = '{"paper_model_name":"ConvLSTM","reference_dataset":"SIRTA 2017-2019","warning":"论文指标仅作架构参考，不代表当前平台部署结果"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 140,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'ConvLSTM_LSTM';

UPDATE model_info
SET
    `model_name` = 'SimVP-gSTA时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'SimVP-gSTA采用编码器、时序转换模块和解码器的纯前馈框架，不依赖逐步递归。gSTA门控时空注意力在卷积特征上聚合时间和空间信息，用于从历史云图生成未来帧。它兼顾预测质量与并行效率，适合构建未来云况预测模块。',
    `short_description` = '无循环结构的编码器-时序模块-解码器，并以gSTA增强时空注意力。',
    `tags` = '["云图预测","SimVP","gSTA","非递归","时空预测"]',
    `model_family` = 'CNN / Gated Spatiotemporal Attention',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2022,
    `paper_title` = 'SimVPv2: Towards Simple yet Powerful Spatiotemporal Predictive Learning',
    `paper_url` = 'https://arxiv.org/abs/2211.12509',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","非递归时空建模","门控时空注意力","批量并行预测"]',
    `applicable_scenarios` = '["短时云图外推","需要较高吞吐量的视频预测","作为功率预测前置的未来天空生成"]',
    `advantages` = '["无需逐帧递归，训练与推理并行度高","结构相对简单且效率较好","适合多种时空预测数据"]',
    `limitations` = '["输出质量依赖图像配准和采样间隔","对罕见快速云变和长期不确定性刻画有限"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"method":"SimVPv2 with gSTA","task":"spatiotemporal predictive learning","implementation_note":"OpenSTL集成实现，当前平台数据配置可能不同"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 210,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'SimVP_gSTA';

UPDATE model_info
SET
    `model_name` = 'TAU时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'TAU使用可并行的时间注意力单元替代传统递归时序模块，将注意力分为帧内静态注意力和帧间动态注意力，并通过差分散度正则关注相邻帧变化。用于云图预测时，它可同时学习天空结构与云层运动。',
    `short_description` = '把时间注意力分解为帧内静态注意力与帧间动态注意力。',
    `tags` = '["云图预测","TAU","时间注意力","非递归","高效"]',
    `model_family` = 'Temporal Attention Unit',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2023,
    `paper_title` = 'Temporal Attention Unit: Towards Efficient Spatiotemporal Predictive Learning',
    `paper_url` = 'https://arxiv.org/abs/2206.12126',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","帧内与帧间注意力","非递归时间建模","变化敏感训练"]',
    `applicable_scenarios` = '["对云层变化速度敏感的预测","需要高并行效率的云图外推","连续图像序列分析"]',
    `advantages` = '["时间模块可并行计算","显式关注帧间动态变化","精度与效率较均衡"]',
    `limitations` = '["对输入序列长度和正则权重较敏感","确定性输出难以完整表达多种未来云况"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"key_design":["intra-frame statical attention","inter-frame dynamical attention","differential divergence regularization"],"implementation_note":"OpenSTL集成实现"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 220,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'TAU';

UPDATE model_info
SET
    `model_name` = 'ConvLSTM时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'ConvLSTM在输入到状态、状态到状态的门控计算中使用卷积，使隐藏状态保持二维空间布局。它最初用于降水临近预报，也是云图、雷达图和视频未来帧预测中的经典时空基线，可学习云团移动及形态变化。',
    `short_description` = '把LSTM中的全连接运算替换为卷积，保留图像空间结构的时序记忆。',
    `tags` = '["云图预测","ConvLSTM","循环网络","时空记忆","经典基线"]',
    `model_family` = 'Convolutional Recurrent Network',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2015,
    `paper_title` = 'Convolutional LSTM Network: A Machine Learning Approach for Precipitation Nowcasting',
    `paper_url` = 'https://arxiv.org/abs/1506.04214',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","二维时空记忆","逐帧递归预测","局部运动建模"]',
    `applicable_scenarios` = '["短时云图与雷达图外推","需要经典可解释基线的场景","连续图像帧预测"]',
    `advantages` = '["同时保留空间结构与时间记忆","应用成熟、实现和资料丰富","适合作为时空递归模型基线"]',
    `limitations` = '["逐步递归导致推理速度较慢","长序列中容易出现误差累积和画面模糊"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"original_task":"precipitation nowcasting","implementation_note":"OpenSTL集成实现；本平台迁移到天空云图预测"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 230,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'ConvLSTM';

UPDATE model_info
SET
    `model_name` = 'PredRNN时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'PredRNN提出时空LSTM单元，在时间方向传递传统记忆，同时引入沿网络层级传播的空间记忆，使模型能够联合建模帧内结构和跨帧动态。用于云图预测时，它适合学习云团的连续演化与多层次运动特征。',
    `short_description` = '通过时空LSTM和跨层记忆流联合传递时间与空间状态。',
    `tags` = '["云图预测","PredRNN","ST-LSTM","记忆网络","递归预测"]',
    `model_family` = 'Spatiotemporal LSTM',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2017,
    `paper_title` = 'PredRNN: Recurrent Neural Networks for Predictive Learning using Spatiotemporal LSTMs',
    `paper_url` = 'https://dl.acm.org/doi/abs/10.5555/3294771.3294855',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","时空LSTM记忆","跨层空间记忆流","递归多步预测"]',
    `applicable_scenarios` = '["云层运动连续性较强的预测","需要深层时空记忆的任务","多步图像外推"]',
    `advantages` = '["比基础ConvLSTM具有更丰富的记忆路径","能联合建模时间和层级空间信息","适合复杂视频动态"]',
    `limitations` = '["模型较重且递归速度慢","多步预测仍可能出现误差累积"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"key_design":["spatiotemporal LSTM","zigzag memory flow"],"implementation_note":"OpenSTL集成实现"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 240,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'PredRNN';

UPDATE model_info
SET
    `model_name` = 'PredRNN++时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'PredRNN++在PredRNN基础上引入Causal LSTM与Gradient Highway Unit，改善深层时间展开中的梯度传播，并增强短期和长期动态的联合学习。它适合对云图进行连续多步外推，尤其用于比较不同递归记忆结构。',
    `short_description` = '以Causal LSTM和Gradient Highway缓解深层递归网络的梯度传播困难。',
    `tags` = '["云图预测","PredRNN++","Causal LSTM","Gradient Highway","递归预测"]',
    `model_family` = 'Causal Spatiotemporal RNN',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2018,
    `paper_title` = 'PredRNN++: Towards A Resolution of the Deep-in-Time Dilemma in Spatiotemporal Predictive Learning',
    `paper_url` = 'https://arxiv.org/abs/1804.06300',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","深层时序记忆","梯度高速通道","递归多步预测"]',
    `applicable_scenarios` = '["较长预测步数的云图外推","复杂动态视频预测","需要比PredRNN更深时间建模的场景"]',
    `advantages` = '["改善深层递归训练稳定性","兼顾短期变化和长期依赖","在经典预测基准上表现稳定"]',
    `limitations` = '["结构复杂，训练和推理成本较高","长期滚动预测仍会累积偏差"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"key_design":["Causal LSTM","Gradient Highway Unit"],"implementation_note":"OpenSTL集成实现"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 250,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'PredRNN++';

UPDATE model_info
SET
    `model_name` = 'E3D-LSTM时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'E3D-LSTM把3D卷积嵌入LSTM式记忆更新，以局部时空窗口捕捉运动，同时通过Eidetic记忆机制回看较早的历史状态。对于云图序列，它能够结合短时云团运动与更长时间的演化线索。',
    `short_description` = '结合3D卷积与Eidetic记忆，强调局部时空窗口和历史状态回忆。',
    `tags` = '["云图预测","E3D-LSTM","3D卷积","Eidetic记忆","长程依赖"]',
    `model_family` = 'Eidetic 3D LSTM',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2019,
    `paper_title` = 'Eidetic 3D LSTM: A Model for Video Prediction and Beyond',
    `paper_url` = 'https://openreview.net/forum?id=B1lKS2AqtX',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","3D时空卷积","历史记忆回忆","长短期动态建模"]',
    `applicable_scenarios` = '["运动形态复杂的云图预测","需要较强历史记忆的长序列","视频未来帧生成"]',
    `advantages` = '["3D卷积与记忆机制结合","能利用较早历史状态","适合复杂时空模式"]',
    `limitations` = '["参数和显存开销较大","训练过程复杂，对数据量要求较高"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"key_design":["3D convolution","eidetic memory transition"],"implementation_note":"OpenSTL集成实现"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 260,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'E3D_LSTM';

UPDATE model_info
SET
    `model_name` = 'SwinLSTM时空云图预测模型',
    `model_type` = 'MULTIMODAL',
    `description` = 'SwinLSTM把Swin Transformer的窗口自注意力融入简化LSTM单元，用自注意力替代ConvLSTM中的局部卷积，以扩大空间感受野并学习更全局的云层结构。它适合复杂天空场景和大范围云团变化预测。',
    `short_description` = '以Swin Transformer块替代局部卷积，增强全局空间依赖建模。',
    `tags` = '["云图预测","SwinLSTM","Swin Transformer","LSTM","全局依赖"]',
    `model_family` = 'Swin Transformer + LSTM',
    `provider` = 'OpenSTL model zoo',
    `release_year` = 2023,
    `paper_title` = 'SwinLSTM: Improving Spatiotemporal Prediction Accuracy using Swin Transformer and LSTM',
    `paper_url` = 'https://arxiv.org/abs/2308.09891',
    `source_url` = 'https://github.com/chengtan9907/OpenSTL',
    `capabilities` = '["未来云图生成","窗口自注意力","全局空间依赖","递归时间建模"]',
    `applicable_scenarios` = '["大范围云层结构变化","局部卷积感受野不足的场景","高分辨率时空预测"]',
    `advantages` = '["比纯卷积递归更擅长捕捉全局空间关系","分层窗口机制兼顾效率与感受野","适合复杂背景视频"]',
    `limitations` = '["窗口大小和分辨率对效果影响明显","计算和显存开销通常高于基础ConvLSTM"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"sky_image_sequence","history_minutes":30,"sampling_interval_seconds":60,"sky_image_sequence_required":true},"display_note":"模型服务需接收连续云图或天空图像张量；分辨率、通道和归一化方式以部署配置为准"}',
    `output_schema` = '{"output_type":"future_sky_frame_sequence","steps":6,"step_minutes":5,"horizon_minutes":30,"display_note":"可将未来云图继续送入图转功率模型形成两阶段预测"}',
    `reference_info` = '{"key_design":["Swin Transformer blocks","simplified LSTM"],"implementation_note":"OpenSTL集成实现"}',
    `marketplace_visible` = 1,
    `is_featured` = 0,
    `sort_order` = 270,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'swinLSTM';

UPDATE model_info
SET
    `model_name` = 'SUNSET天空图像与功率融合预测模型',
    `model_type` = 'FUSION',
    `description` = 'SUNSET是面向超短期光伏预测的端到端卷积网络。SkyGPT项目中的参考实现使用过去15分钟天空图像和光伏输出记录作为输入，直接预测15分钟后的光伏功率。它不先生成未来云图，适合作为图像与数值联合回归的经典基线。',
    `short_description` = '使用AlexNet风格CNN融合历史天空图像与光伏功率，预测15分钟后输出。',
    `tags` = '["图像数值融合","SUNSET","CNN","天空图像","光伏功率"]',
    `model_family` = 'AlexNet-like CNN',
    `provider` = 'SUNSET / SkyGPT reference implementation',
    `release_year` = 2018,
    `paper_title` = 'Solar PV output prediction from video streams using convolutional neural networks',
    `paper_url` = NULL,
    `source_url` = 'https://github.com/YuchiSun/SUNSET',
    `capabilities` = '["历史天空图像编码","历史功率融合","15分钟超短期功率回归","端到端视觉预测"]',
    `applicable_scenarios` = '["部署全天空相机的屋顶光伏系统","15分钟级超短期预测","与未来云图两阶段方案进行对比"]',
    `advantages` = '["输入到功率输出的端到端流程简单","利用天空图像补充云层信息","已有公开太阳能预测实践"]',
    `limitations` = '["原始方案为确定性单点预测","对相机位置、成像条件和电站域迁移较敏感","原始配置与平台多步输出并不完全一致"]',
    `supported_input_modes` = '["FILE_UPLOAD","OPEN_API"]',
    `input_schema` = '{"platform_contract":{"input_type":"image_numeric_fusion","sky_image_sequence_required":true,"numeric_history_required":true,"history_minutes":30,"sampling_interval_seconds":60},"reference_setup":{"history_minutes":15,"image_interval_minutes":1,"inputs":["past sky images","past PV output record"],"forecast_horizon_minutes":15},"display_note":"原始SUNSET为15分钟超短期单点预测；当前平台接口参数以部署服务为准"}',
    `output_schema` = '{"platform_output":{"output_type":"multi_step_power_forecast","steps":6,"step_minutes":5,"unit":"kW"},"reference_output":{"output_type":"single_horizon_pv_power","forecast_horizon_minutes":15}}',
    `reference_info` = '{"repository_reference":"SkyGPT README: SUNSET_PV_forecast.ipynb","architecture":"AlexNet-like CNN","warning":"原始SUNSET配置为15分钟单步预测，当前平台服务可能做了适配"}',
    `marketplace_visible` = 1,
    `is_featured` = 1,
    `sort_order` = 310,
    `updated_at` = CURRENT_TIMESTAMP
WHERE model_code = 'SUNSET';

COMMIT;


DELETE mm
  FROM model_metric mm
  JOIN model_info mi ON mi.model_id = mm.model_id
 WHERE mi.model_code IN ('CNN_MLP','CNN_LSTM','3DCNN_LSTM','ConvLSTM_LSTM')
   AND mm.dataset_name = 'SIRTA 2019 test set（论文参考，10分钟预测，L2损失）';

INSERT INTO model_metric (
    model_id,
    dataset_name,
    mae,
    rmse,
    mape,
    r2_score,
    metric_json,
    evaluated_at
)
SELECT
    model_id,
    'SIRTA 2019 test set（论文参考，10分钟预测，L2损失）',
    NULL,
    NULL,
    NULL,
    NULL,
    '{"source":"reference_paper","paper_model_name":"CNN","target":"GHI","forecast_horizon_minutes":10,"loss":"L2","forecast_skill_percent":{"MSE":32.9,"RMSE":18.1,"MAE":-3.7},"ramp_score_w_m2_per_min":20.2,"ramp_score_change_percent":-30.1,"quantile_95_w_m2":273.6,"quantile_95_change_percent":-21.8,"TDI_percent":10.5,"TDM":0.34,"rmse_forecast_skill_by_horizon_percent":{"2":7.5,"6":16.9,"10":18.1,"20":19.2,"30":19.7},"note":"论文参考结果，不代表当前平台部署模型实测"}',
    NULL
FROM model_info
WHERE model_code = 'CNN_MLP';

INSERT INTO model_metric (
    model_id,
    dataset_name,
    mae,
    rmse,
    mape,
    r2_score,
    metric_json,
    evaluated_at
)
SELECT
    model_id,
    'SIRTA 2019 test set（论文参考，10分钟预测，L2损失）',
    NULL,
    NULL,
    NULL,
    NULL,
    '{"source":"reference_paper","paper_model_name":"CNN + LSTM","target":"GHI","forecast_horizon_minutes":10,"loss":"L2","forecast_skill_percent":{"MSE":34.8,"RMSE":19.2,"MAE":3.1},"ramp_score_w_m2_per_min":20.2,"ramp_score_change_percent":-30.1,"quantile_95_w_m2":275.0,"quantile_95_change_percent":-21.4,"TDI_percent":10.0,"TDM":0.34,"rmse_forecast_skill_by_horizon_percent":{"2":10.8,"6":16.5,"10":19.2,"20":20.4,"30":20.9},"note":"论文参考结果，不代表当前平台部署模型实测"}',
    NULL
FROM model_info
WHERE model_code = 'CNN_LSTM';

INSERT INTO model_metric (
    model_id,
    dataset_name,
    mae,
    rmse,
    mape,
    r2_score,
    metric_json,
    evaluated_at
)
SELECT
    model_id,
    'SIRTA 2019 test set（论文参考，10分钟预测，L2损失）',
    NULL,
    NULL,
    NULL,
    NULL,
    '{"source":"reference_paper","paper_model_name":"3D-CNN","target":"GHI","forecast_horizon_minutes":10,"loss":"L2","forecast_skill_percent":{"MSE":35.5,"RMSE":19.7,"MAE":5.8},"ramp_score_w_m2_per_min":19.6,"ramp_score_change_percent":-32.2,"quantile_95_w_m2":274.3,"quantile_95_change_percent":-21.6,"TDI_percent":9.4,"TDM":0.49,"rmse_forecast_skill_by_horizon_percent":{"2":10.4,"6":17.2,"10":19.7,"20":21.2,"30":22.4},"note":"论文参考结果，不代表当前平台部署模型实测"}',
    NULL
FROM model_info
WHERE model_code = '3DCNN_LSTM';

INSERT INTO model_metric (
    model_id,
    dataset_name,
    mae,
    rmse,
    mape,
    r2_score,
    metric_json,
    evaluated_at
)
SELECT
    model_id,
    'SIRTA 2019 test set（论文参考，10分钟预测，L2损失）',
    NULL,
    NULL,
    NULL,
    NULL,
    '{"source":"reference_paper","paper_model_name":"ConvLSTM","target":"GHI","forecast_horizon_minutes":10,"loss":"L2","forecast_skill_percent":{"MSE":36.6,"RMSE":20.4,"MAE":6.2},"ramp_score_w_m2_per_min":20.2,"ramp_score_change_percent":-30.1,"quantile_95_w_m2":274.1,"quantile_95_change_percent":-21.6,"TDI_percent":9.8,"TDM":0.64,"rmse_forecast_skill_by_horizon_percent":{"2":9.05,"6":16.6,"10":20.4,"20":21.4,"30":22.1},"note":"论文参考结果，不代表当前平台部署模型实测"}',
    NULL
FROM model_info
WHERE model_code = 'ConvLSTM_LSTM';

SELECT
    model_id,
    model_code,
    model_name,
    model_type,
    status,
    short_description,
    model_family,
    provider,
    release_year,
    marketplace_visible,
    is_featured,
    sort_order
FROM model_info
WHERE model_code IN ('DLinear', 'PatchTST', 'iTransformer', 'TimeXer', 'TimeMixer', 'TSMixer', 'Transformer', 'CNN_MLP', 'CNN_LSTM', '3DCNN_LSTM', 'ConvLSTM_LSTM', 'SimVP_gSTA', 'TAU', 'ConvLSTM', 'PredRNN', 'PredRNN++', 'E3D_LSTM', 'swinLSTM', 'SUNSET')
ORDER BY sort_order, model_id;

SELECT
    mi.model_code,
    mm.dataset_name,
    mm.metric_json
FROM model_metric mm
JOIN model_info mi ON mi.model_id = mm.model_id
WHERE mm.dataset_name = 'SIRTA 2019 test set（论文参考，10分钟预测，L2损失）'
ORDER BY mi.model_code;


-- Consolidated from backend/src/main/resources/sql/news_source_migration.sql
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_news_sources$$
CREATE PROCEDURE migrate_news_sources()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='category') THEN ALTER TABLE news ADD COLUMN category VARCHAR(32) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='content_type') THEN ALTER TABLE news ADD COLUMN content_type VARCHAR(32) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='source_type') THEN ALTER TABLE news ADD COLUMN source_type VARCHAR(32) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='source_name') THEN ALTER TABLE news ADD COLUMN source_name VARCHAR(128) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='source_url') THEN ALTER TABLE news ADD COLUMN source_url VARCHAR(1000) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='external_id') THEN ALTER TABLE news ADD COLUMN external_id VARCHAR(255) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='source_published_at') THEN ALTER TABLE news ADD COLUMN source_published_at DATETIME DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='fetched_at') THEN ALTER TABLE news ADD COLUMN fetched_at DATETIME DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='external_content') THEN ALTER TABLE news ADD COLUMN external_content TINYINT NOT NULL DEFAULT 0; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='warning_level') THEN ALTER TABLE news ADD COLUMN warning_level VARCHAR(64) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='warning_region') THEN ALTER TABLE news ADD COLUMN warning_region VARCHAR(255) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='warning_agency') THEN ALTER TABLE news ADD COLUMN warning_agency VARCHAR(255) DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='effective_at') THEN ALTER TABLE news ADD COLUMN effective_at DATETIME DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND COLUMN_NAME='expires_at') THEN ALTER TABLE news ADD COLUMN expires_at DATETIME DEFAULT NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND INDEX_NAME='idx_news_category_publish') THEN ALTER TABLE news ADD INDEX idx_news_category_publish(category,status,published_at); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='news' AND INDEX_NAME='uk_news_external') THEN ALTER TABLE news ADD UNIQUE INDEX uk_news_external(source_type,external_id); END IF;
END$$
CALL migrate_news_sources()$$
DROP PROCEDURE migrate_news_sources$$
DELIMITER ;

UPDATE news SET category=CASE WHEN news_type='INDUSTRY_NEWS' THEN 'INDUSTRY' ELSE 'PLATFORM' END WHERE category IS NULL;
UPDATE news SET content_type=CASE WHEN news_type='NOTICE' THEN 'PLATFORM_NOTICE' ELSE 'PLATFORM_NEWS' END, source_type='PLATFORM', source_name='光伏智云平台' WHERE source_type IS NULL;


-- Consolidated from backend/src/main/resources/sql/oss_news_migration.sql

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
