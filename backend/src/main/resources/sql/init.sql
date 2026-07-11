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


-- 所有建表完成，恢复外键校验
SET FOREIGN_KEY_CHECKS = 1;

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