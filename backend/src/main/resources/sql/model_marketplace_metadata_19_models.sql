-- Model marketplace metadata migration for existing databases.
-- Run after init.sql if the database was created before marketplace metadata fields existed.

DELIMITER $$
CREATE PROCEDURE add_model_info_column_if_missing(IN col_name VARCHAR(64), IN col_def TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'model_info'
          AND column_name = col_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE model_info ADD COLUMN ', col_name, ' ', col_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_model_info_column_if_missing('short_description', 'VARCHAR(500) DEFAULT NULL COMMENT ''模型广场短描述''');
CALL add_model_info_column_if_missing('tags', 'JSON DEFAULT NULL COMMENT ''模型标签数组''');
CALL add_model_info_column_if_missing('model_family', 'VARCHAR(64) DEFAULT NULL COMMENT ''模型家族''');
CALL add_model_info_column_if_missing('provider', 'VARCHAR(128) DEFAULT NULL COMMENT ''模型来源机构或作者''');
CALL add_model_info_column_if_missing('release_year', 'INT DEFAULT NULL COMMENT ''论文或模型发布时间''');
CALL add_model_info_column_if_missing('paper_title', 'VARCHAR(255) DEFAULT NULL COMMENT ''论文标题''');
CALL add_model_info_column_if_missing('paper_url', 'VARCHAR(512) DEFAULT NULL COMMENT ''论文链接''');
CALL add_model_info_column_if_missing('source_url', 'VARCHAR(512) DEFAULT NULL COMMENT ''源码或项目链接''');
CALL add_model_info_column_if_missing('capabilities', 'JSON DEFAULT NULL COMMENT ''核心能力数组''');
CALL add_model_info_column_if_missing('applicable_scenarios', 'JSON DEFAULT NULL COMMENT ''适用场景数组''');
CALL add_model_info_column_if_missing('advantages', 'JSON DEFAULT NULL COMMENT ''优势数组''');
CALL add_model_info_column_if_missing('limitations', 'JSON DEFAULT NULL COMMENT ''局限数组''');
CALL add_model_info_column_if_missing('supported_input_modes', 'JSON DEFAULT NULL COMMENT ''支持输入方式数组''');
CALL add_model_info_column_if_missing('reference_info', 'JSON DEFAULT NULL COMMENT ''参考信息''');
CALL add_model_info_column_if_missing('marketplace_visible', 'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否在模型广场展示''');
CALL add_model_info_column_if_missing('is_featured', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否推荐展示''');
CALL add_model_info_column_if_missing('sort_order', 'INT NOT NULL DEFAULT 999 COMMENT ''模型广场排序''');
DROP PROCEDURE add_model_info_column_if_missing;

UPDATE model_info
SET marketplace_visible = 1,
    short_description = COALESCE(short_description, description),
    capabilities = COALESCE(capabilities, JSON_ARRAY('短时光伏功率预测', '模型能力展示', '与基线模型对比')),
    applicable_scenarios = COALESCE(applicable_scenarios, JSON_ARRAY('模型广场介绍', '预测能力对比', '教学与演示')),
    advantages = COALESCE(advantages, JSON_ARRAY('结构清晰', '适合横向对比', '可与平台预测任务联动')),
    limitations = COALESCE(limitations, JSON_ARRAY('论文指标来自公开资料或离线基线说明，不代表当前平台实测', 'OFFLINE 模型仅展示介绍，不能直接调用')),
    reference_info = COALESCE(reference_info, JSON_OBJECT('metricSource', '论文或离线基线资料，非平台实时实测'));

UPDATE model_info SET tags = JSON_ARRAY('时序基线', '低延迟', '可调用'), model_family = 'TIME_SERIES', provider = 'Zeng et al.', release_year = 2023, paper_title = 'Long-term Time Series Forecasting with Linear Models', paper_url = 'https://arxiv.org/abs/2205.13504', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), is_featured = 1, sort_order = 10 WHERE model_code = 'DLinear';
UPDATE model_info SET tags = JSON_ARRAY('Transformer', '时序预测', '可调用'), model_family = 'TIME_SERIES', provider = 'Nie et al.', release_year = 2023, paper_title = 'A Time Series is Worth 64 Words', paper_url = 'https://arxiv.org/abs/2211.14730', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), is_featured = 1, sort_order = 20 WHERE model_code = 'PatchTST';
UPDATE model_info SET tags = JSON_ARRAY('Transformer', '多变量', '可调用'), model_family = 'TIME_SERIES', provider = 'Liu et al.', release_year = 2024, paper_title = 'iTransformer: Inverted Transformers Are Effective for Time Series Forecasting', paper_url = 'https://arxiv.org/abs/2310.06625', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), is_featured = 1, sort_order = 30 WHERE model_code = 'iTransformer';
UPDATE model_info SET tags = JSON_ARRAY('外生变量', '天气融合', '可调用'), model_family = 'TIME_SERIES', provider = 'Wang et al.', release_year = 2024, paper_title = 'TimeXer: Empowering Transformers for Time Series Forecasting with Exogenous Variables', paper_url = 'https://arxiv.org/abs/2402.19072', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), sort_order = 40 WHERE model_code = 'TimeXer';
UPDATE model_info SET tags = JSON_ARRAY('多尺度', '时序预测', '可调用'), model_family = 'TIME_SERIES', provider = 'Wang et al.', release_year = 2024, paper_title = 'TimeMixer: Decomposable Multiscale Mixing for Time Series Forecasting', paper_url = 'https://arxiv.org/abs/2405.14616', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), sort_order = 50 WHERE model_code = 'TimeMixer';
UPDATE model_info SET tags = JSON_ARRAY('MLP', '时序基线', '可调用'), model_family = 'TIME_SERIES', provider = 'Google Research', release_year = 2023, paper_title = 'TSMixer: An All-MLP Architecture for Time Series Forecasting', paper_url = 'https://arxiv.org/abs/2303.06053', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), sort_order = 60 WHERE model_code = 'TSMixer';
UPDATE model_info SET tags = JSON_ARRAY('Transformer', '基线模型', '可调用'), model_family = 'TIME_SERIES', provider = 'Vaswani et al.', release_year = 2017, paper_title = 'Attention Is All You Need', paper_url = 'https://arxiv.org/abs/1706.03762', supported_input_modes = JSON_ARRAY('STATION_HISTORY','MANUAL_INPUT','FILE_UPLOAD'), sort_order = 70 WHERE model_code = 'Transformer';
UPDATE model_info SET tags = JSON_ARRAY('融合模型', '云图特征', '离线展示'), model_family = 'VISION_FUSION', provider = 'Platform Baseline', release_year = 2024, supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 110 WHERE model_code = 'CNN_LSTM';
UPDATE model_info SET tags = JSON_ARRAY('融合模型', '轻量', '离线展示'), model_family = 'VISION_FUSION', provider = 'Platform Baseline', release_year = 2024, supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 120 WHERE model_code = 'CNN_MLP';
UPDATE model_info SET tags = JSON_ARRAY('3D-CNN', '时空融合', '离线展示'), model_family = 'VISION_FUSION', provider = 'Platform Baseline', release_year = 2024, supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 130 WHERE model_code = '3DCNN_LSTM';
UPDATE model_info SET tags = JSON_ARRAY('ConvLSTM', '融合模型', '离线展示'), model_family = 'VISION_FUSION', provider = 'Platform Baseline', release_year = 2024, supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 140 WHERE model_code = 'ConvLSTM_LSTM';
UPDATE model_info SET tags = JSON_ARRAY('视频预测', '云图外推', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'OpenSTL', release_year = 2022, paper_title = 'SimVP: Simpler yet Better Video Prediction', paper_url = 'https://arxiv.org/abs/2206.05099', supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), is_featured = 1, sort_order = 210 WHERE model_code = 'SimVP_gSTA';
UPDATE model_info SET tags = JSON_ARRAY('注意力', '视频预测', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'OpenSTL', release_year = 2023, supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 220 WHERE model_code = 'TAU';
UPDATE model_info SET tags = JSON_ARRAY('ConvLSTM', '递归时空', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'Shi et al.', release_year = 2015, paper_title = 'Convolutional LSTM Network', paper_url = 'https://arxiv.org/abs/1506.04214', supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 230 WHERE model_code = 'ConvLSTM';
UPDATE model_info SET tags = JSON_ARRAY('PredRNN', '递归预测', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'Wang et al.', release_year = 2017, paper_title = 'PredRNN', paper_url = 'https://arxiv.org/abs/1704.03674', supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 240 WHERE model_code = 'PredRNN';
UPDATE model_info SET tags = JSON_ARRAY('PredRNN++', '视频预测', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'Wang et al.', release_year = 2018, paper_title = 'PredRNN++', paper_url = 'https://arxiv.org/abs/1804.06300', supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 250 WHERE model_code = 'PredRNN++';
UPDATE model_info SET tags = JSON_ARRAY('E3D-LSTM', '时空记忆', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'Wang et al.', release_year = 2019, paper_title = 'Eidetic 3D LSTM', paper_url = 'https://arxiv.org/abs/1810.09949', supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 260 WHERE model_code = 'E3D_LSTM';
UPDATE model_info SET tags = JSON_ARRAY('Swin', 'Transformer', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'OpenSTL', release_year = 2023, supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 270 WHERE model_code = 'swinLSTM';
UPDATE model_info SET tags = JSON_ARRAY('SUNSET', '太阳能预测', '离线展示'), model_family = 'VIDEO_RECURSIVE', provider = 'Stanford', release_year = 2022, paper_title = 'SUNSET solar forecasting', paper_url = 'https://github.com/stanford-solar/SUNSET', source_url = 'https://github.com/stanford-solar/SUNSET', supported_input_modes = JSON_ARRAY('IMAGE_SEQUENCE','WEATHER_SERIES','POWER_HISTORY'), sort_order = 280 WHERE model_code = 'SUNSET';
