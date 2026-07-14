CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar_url VARCHAR(512),
    avatar_file_id BIGINT,
    gender INT DEFAULT 0,
    status INT DEFAULT 1,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(64),
    token_version INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role (
    role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_oauth_account (
    oauth_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    provider VARCHAR(32),
    open_id VARCHAR(128),
    union_id VARCHAR(128),
    nickname VARCHAR(128),
    avatar_url VARCHAR(512),
    email VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_face_auth (
    face_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    provider VARCHAR(32) DEFAULT 'local',
    face_db_name VARCHAR(64),
    entity_id VARCHAR(128),
    face_feature_id VARCHAR(128),
    face_image_url VARCHAR(512),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS external_pv_station (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(32) DEFAULT 'PVOUTPUT',
    external_system_id BIGINT NOT NULL UNIQUE,
    system_name VARCHAR(255),
    system_size_w INT,
    postcode VARCHAR(64),
    orientation VARCHAR(32),
    outputs INT,
    last_output_text VARCHAR(64),
    panel VARCHAR(255),
    inverter VARCHAR(255),
    distance_km DECIMAL(10,2),
    latitude DECIMAL(10,6),
    longitude DECIMAL(10,6),
    enabled BOOLEAN DEFAULT TRUE,
    last_sync_time TIMESTAMP,
    last_sync_status VARCHAR(32),
    last_sync_error VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS external_pv_station_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_system_id BIGINT NOT NULL,
    sample_time TIMESTAMP NOT NULL,
    energy_generation_wh INT,
    power_generation_w INT,
    energy_consumption_wh INT,
    power_consumption_w INT,
    normalised_output DECIMAL(10,4),
    temperature_c DECIMAL(8,2),
    voltage_v DECIMAL(8,2),
    raw_payload TEXT,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(external_system_id, sample_time)
);

CREATE TABLE IF NOT EXISTS model_info (
    model_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_code VARCHAR(64) NOT NULL UNIQUE,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    model_version VARCHAR(32),
    input_window_minutes INT DEFAULT 30,
    input_frame_interval_seconds INT DEFAULT 60,
    output_steps INT DEFAULT 6,
    output_step_minutes INT DEFAULT 5,
    service_model_name VARCHAR(128) NOT NULL,
    api_path VARCHAR(256),
    input_schema TEXT,
    output_schema TEXT,
    status VARCHAR(16) DEFAULT 'OFFLINE',
    description VARCHAR(512),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS model_metric (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT NOT NULL,
    dataset_name VARCHAR(128),
    mae DECIMAL(18, 6),
    rmse DECIMAL(18, 6),
    mape DECIMAL(18, 6),
    r2_score DECIMAL(18, 6),
    metric_json TEXT,
    evaluated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_file (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_id BIGINT NOT NULL,
    file_name VARCHAR(256),
    file_path VARCHAR(512),
    file_type VARCHAR(64),
    file_size BIGINT,
    checksum VARCHAR(128),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prediction_task (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    input_mode VARCHAR(32),
    input_start_time TIMESTAMP,
    input_end_time TIMESTAMP,
    predict_start_time TIMESTAMP,
    predict_end_time TIMESTAMP,
    status VARCHAR(16) DEFAULT 'PENDING',
    request_params TEXT,
    error_message VARCHAR(1024),
    cost_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prediction_input_snapshot (
    input_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    point_time TIMESTAMP NOT NULL,
    power_kw DECIMAL(18, 6),
    temperature_c DECIMAL(18, 6),
    irradiance_w_m2 DECIMAL(18, 6),
    humidity_percent DECIMAL(18, 6),
    wind_speed_m_s DECIMAL(18, 6),
    raw_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prediction_result (
    result_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    time_offset_minutes INT NOT NULL,
    predict_time TIMESTAMP,
    predict_power_kw DECIMAL(18, 6),
    actual_power_kw DECIMAL(18, 6),
    error_value DECIMAL(18, 6),
    error_rate DECIMAL(18, 6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS power_station (
    station_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_code VARCHAR(32),
    station_name VARCHAR(128),
    province VARCHAR(64),
    city VARCHAR(64),
    district VARCHAR(64),
    address VARCHAR(256),
    longitude DECIMAL(18, 10),
    latitude DECIMAL(18, 10),
    capacity_kw DECIMAL(18, 6),
    installed_area DECIMAL(18, 6),
    grid_connected_date DATE,
    owner_user_id BIGINT,
    status VARCHAR(16) DEFAULT 'RUNNING',
    description VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_station_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    permission_type VARCHAR(32) NOT NULL DEFAULT 'VIEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, station_id)
);

CREATE TABLE IF NOT EXISTS pv_data (
    data_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id BIGINT NOT NULL,
    collect_time TIMESTAMP NOT NULL,
    power_kw DECIMAL(18, 6),
    energy_today_kwh DECIMAL(18, 6),
    energy_total_kwh DECIMAL(18, 6),
    voltage_v DECIMAL(18, 6),
    current_a DECIMAL(18, 6),
    irradiance_w_m2 DECIMAL(18, 6),
    module_temperature_c DECIMAL(18, 6),
    ambient_temperature_c DECIMAL(18, 6),
    humidity_percent DECIMAL(18, 6),
    wind_speed_m_s DECIMAL(18, 6),
    data_source VARCHAR(64),
    raw_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS weather_data (
    weather_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id BIGINT,
    weather_time TIMESTAMP,
    weather_code VARCHAR(32),
    weather_text VARCHAR(64),
    temperature_c DECIMAL(8, 3),
    humidity_percent DECIMAL(8, 3),
    wind_direction VARCHAR(32),
    wind_power VARCHAR(32),
    wind_speed_m_s DECIMAL(8, 3),
    precipitation_mm DECIMAL(8, 3),
    cloudiness_percent DECIMAL(8, 3),
    source VARCHAR(64),
    raw_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pv_data_import_task (
    import_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    station_id BIGINT,
    file_name VARCHAR(255),
    file_url VARCHAR(512),
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analysis_report (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    station_id BIGINT,
    task_id BIGINT,
    title VARCHAR(255),
    summary TEXT,
    weather_analysis TEXT,
    prediction_analysis TEXT,
    abnormal_analysis TEXT,
    suggestion TEXT,
    report_content TEXT,
    report_json TEXT,
    include_weather BOOLEAN,
    include_prediction BOOLEAN,
    model_name VARCHAR(128),
    prompt_snapshot TEXT,
    context_snapshot TEXT,
    raw_response TEXT,
    risk_level VARCHAR(32),
    status VARCHAR(32) DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_key (
    api_key_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    key_name VARCHAR(128),
    api_key_prefix VARCHAR(32),
    api_key_hash VARCHAR(255),
    status VARCHAR(32) DEFAULT 'ACTIVE',
    rate_limit_per_minute INT DEFAULT 60,
    daily_quota INT DEFAULT 1000,
    expire_time TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_call_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    api_key_id BIGINT,
    model_id BIGINT,
    request_path VARCHAR(255),
    request_method VARCHAR(16),
    request_ip VARCHAR(64),
    request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    response_time TIMESTAMP,
    cost_time_ms BIGINT,
    http_status INT,
    biz_status VARCHAR(32),
    error_message TEXT,
    request_summary TEXT,
    response_summary TEXT
);

CREATE TABLE IF NOT EXISTS file_resource (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT,
    original_name VARCHAR(255),
    storage_name VARCHAR(255),
    storage_path VARCHAR(512),
    file_url VARCHAR(512),
    file_type VARCHAR(64),
    business_type VARCHAR(64),
    file_size BIGINT,
    checksum VARCHAR(128),
    object_key VARCHAR(500),
    content_type VARCHAR(100),
    file_status VARCHAR(20) DEFAULT 'BOUND',
    biz_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS news (
    news_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    summary VARCHAR(500),
    content TEXT,
    cover_url VARCHAR(512),
    cover_file_id BIGINT,
    news_type VARCHAR(32) DEFAULT 'NEWS',
    category VARCHAR(32),
    content_type VARCHAR(32),
    source_type VARCHAR(32),
    source_name VARCHAR(128),
    source_url VARCHAR(1000),
    external_id VARCHAR(255),
    source_published_at TIMESTAMP,
    fetched_at TIMESTAMP,
    external_content INT DEFAULT 0,
    warning_level VARCHAR(64),
    warning_region VARCHAR(255),
    warning_agency VARCHAR(255),
    effective_at TIMESTAMP,
    expires_at TIMESTAMP,
    target_role VARCHAR(64) DEFAULT 'ALL',
    status VARCHAR(32) DEFAULT 'DRAFT',
    publisher_id BIGINT,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_login_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    login_type VARCHAR(32),
    login_ip VARCHAR(64),
    user_agent VARCHAR(512),
    status VARCHAR(16),
    message VARCHAR(255),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    title VARCHAR(255),
    content TEXT,
    notification_type VARCHAR(32) DEFAULT 'SYSTEM',
    related_type VARCHAR(64),
    related_id BIGINT,
    read_status INT DEFAULT 0,
    read_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
