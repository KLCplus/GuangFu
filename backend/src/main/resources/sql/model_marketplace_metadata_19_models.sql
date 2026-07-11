-- =========================================================
-- 光伏预测平台：19个模型的模型广场元数据迁移脚本
-- 生成日期：2026-07-10
--
-- 数据来源：
-- 1) THUML Time-Series-Library
-- 2) OpenSTL
-- 3) SkyGPT / SUNSET
-- 4) Paletta et al., Solar Energy 224 (2021) 855-867
--
-- 说明：
-- - 本脚本只扩展 model_info 的展示字段，并更新19条已有模型记录。
-- - 不修改 service_model_name、api_path、status 以及平台当前预测窗口参数，
--   避免影响后端模型调用契约。
-- - input_schema / output_schema 中同时区分“平台契约”和“论文参考配置”。
-- - 论文或开源项目中的指标仅作模型广场参考，不代表当前平台部署模型实测结果。
-- - 脚本可重复执行：新增字段与论文参考指标均做了幂等处理。
-- =========================================================

SET NAMES utf8mb4;
USE pv_platform;

-- 运行前检查：确保数据库中存在预期的19个模型编码
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

-- 幂等新增模型广场展示字段
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

-- 01. DLinear
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

-- 02. PatchTST
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

-- 03. iTransformer
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

-- 04. TimeXer
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

-- 05. TimeMixer
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

-- 06. TSMixer
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

-- 07. Transformer
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

-- 08. CNN_MLP
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

-- 09. CNN_LSTM
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

-- 10. 3DCNN_LSTM
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

-- 11. ConvLSTM_LSTM
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

-- 12. SimVP_gSTA
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

-- 13. TAU
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

-- 14. ConvLSTM
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

-- 15. PredRNN
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

-- 16. PredRNN++
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

-- 17. E3D_LSTM
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

-- 18. swinLSTM
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

-- 19. SUNSET
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

-- =========================================================
-- 写入参考论文指标
-- 注意：这里保存的是论文中的 forecast skill、ramp、TDI/TDM 等参考结果，
--       并非当前平台部署模型的 MAE/RMSE 实测，因此标准 mae/rmse/mape/r2 字段留空。
-- =========================================================

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

-- =========================================================
-- 执行结果核对
-- =========================================================
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
