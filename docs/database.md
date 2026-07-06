# 数据库设计草案

所有表建议统一包含 `create_time`、`update_time`，按需增加逻辑删除字段。密码只存安全哈希，API Key 只存哈希与可展示前缀。

| 表 | 关键字段 |
|---|---|
| `user` | `user_id bigint PK`、`username varchar(64) UNIQUE`、`password_hash varchar(255)`、`email varchar(128)`、`phone varchar(32)`、`role varchar(32)`、`status varchar(32)`、`create_time datetime`、`update_time datetime` |
| `power_station` | `station_id bigint PK`、`station_name varchar(128)`、`province varchar(64)`、`city varchar(64)`、`address varchar(255)`、`longitude decimal(10,6)`、`latitude decimal(10,6)`、`capacity decimal(12,2)`、`status varchar(32)`、`description text` |
| `pv_data` | `data_id bigint PK`、`station_id bigint INDEX`、`collect_time datetime INDEX`、`power decimal(12,3)`、`voltage decimal(12,3)`、`current decimal(12,3)`、`irradiance decimal(12,3)`、`temperature decimal(8,3)`、`humidity decimal(8,3)`、`wind_speed decimal(8,3)` |
| `weather_data` | `weather_id bigint PK`、`station_id bigint INDEX`、`weather_time datetime`、`weather varchar(64)`、`temperature decimal(8,3)`、`humidity decimal(8,3)`、`wind_direction varchar(32)`、`wind_speed decimal(8,3)`、`raw_json json` |
| `model_info` | `model_id bigint PK`、`model_name varchar(128)`、`model_code varchar(64) UNIQUE`、`model_type varchar(32)`、`model_version varchar(32)`、`model_status varchar(32)`、`input_description text`、`output_description text`、`description text` |
| `prediction_task` | `task_id bigint PK`、`user_id bigint`、`station_id bigint`、`model_id bigint`、`input_mode varchar(32)`、`input_start_time datetime`、`input_end_time datetime`、`task_status varchar(32)`、`error_message text`、`cost_time bigint`、`create_time datetime`、`finish_time datetime` |
| `prediction_result` | `result_id bigint PK`、`task_id bigint INDEX`、`predict_time datetime`、`time_offset int`、`predict_power decimal(12,3)`、`actual_power decimal(12,3) NULL`、`error_value decimal(12,3) NULL` |
| `api_key` | `api_key_id bigint PK`、`user_id bigint`、`key_prefix varchar(16)`、`key_hash varchar(255)`、`status varchar(32)`、`call_limit int`、`expire_time datetime NULL`、`create_time datetime` |
| `api_call_log` | `log_id bigint PK`、`api_key_id bigint`、`model_id bigint`、`request_id varchar(64)`、`request_time datetime INDEX`、`cost_time bigint`、`status varchar(32)`、`error_message text` |
| `news` | `news_id bigint PK`、`title varchar(255)`、`content text`、`type varchar(32)`、`publisher_id bigint`、`status varchar(32)`、`publish_time datetime`、`create_time datetime` |

## 索引和约束建议

- `pv_data(station_id, collect_time)` 建联合唯一或普通索引，取决于采集端是否允许重复帧。
- `prediction_result(task_id, time_offset)` 建唯一索引。
- 所有业务外键可在数据库层建立，也可由应用层维护；团队应统一策略。
- 大规模历史数据可按月分区或迁移至时序数据库，但不属于骨架阶段。
