package com.example.pvplatform.module.model.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ModelMarketplaceInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public ModelMarketplaceInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("short_description", "VARCHAR(500)");
        addColumn("tags", "TEXT");
        addColumn("model_family", "VARCHAR(64)");
        addColumn("provider", "VARCHAR(128)");
        addColumn("release_year", "INT");
        addColumn("paper_title", "VARCHAR(255)");
        addColumn("paper_url", "VARCHAR(512)");
        addColumn("source_url", "VARCHAR(512)");
        addColumn("capabilities", "TEXT");
        addColumn("applicable_scenarios", "TEXT");
        addColumn("advantages", "TEXT");
        addColumn("limitations", "TEXT");
        addColumn("supported_input_modes", "TEXT");
        addColumn("reference_info", "TEXT");
        addColumn("marketplace_visible", "BOOLEAN DEFAULT TRUE");
        addColumn("is_featured", "BOOLEAN DEFAULT FALSE");
        addColumn("sort_order", "INT DEFAULT 999");
        seedMetadata();
    }

    private void addColumn(String name, String definition) {
        if (columnExists(name)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE model_info ADD COLUMN " + name + " " + definition);
    }

    private boolean columnExists(String name) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'model_info' AND LOWER(column_name) = LOWER(?)
                """, Integer.class, name);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void seedMetadata() {
        model("DLinear", "TIME_SERIES", "Zeng et al.", 2023, "DLinear", "Long-term Time Series Forecasting with Linear Models", "https://arxiv.org/abs/2205.13504", 10, true,
            "线性分解时序基线，适合快速建立功率预测参照。", "[\"时序基线\",\"低延迟\",\"可调用\"]");
        model("PatchTST", "TIME_SERIES", "Nie et al.", 2023, "PatchTST", "A Time Series is Worth 64 Words", "https://arxiv.org/abs/2211.14730", 20, true,
            "Patch 分块 Transformer，适合多变量光伏短期预测。", "[\"Transformer\",\"时序预测\",\"可调用\"]");
        model("iTransformer", "TIME_SERIES", "Liu et al.", 2024, "iTransformer", "iTransformer: Inverted Transformers Are Effective for Time Series Forecasting", "https://arxiv.org/abs/2310.06625", 30, true,
            "倒置 Transformer 结构，强化变量维度建模。", "[\"Transformer\",\"多变量\",\"可调用\"]");
        model("TimeXer", "TIME_SERIES", "Wang et al.", 2024, "TimeXer", "TimeXer: Empowering Transformers for Time Series Forecasting with Exogenous Variables", "https://arxiv.org/abs/2402.19072", 40, false,
            "面向外生变量增强的时序预测模型。", "[\"外生变量\",\"天气融合\",\"可调用\"]");
        model("TimeMixer", "TIME_SERIES", "Wang et al.", 2024, "TimeMixer", "TimeMixer: Decomposable Multiscale Mixing for Time Series Forecasting", "https://arxiv.org/abs/2405.14616", 50, false,
            "多尺度混合结构，适合处理不同时间尺度波动。", "[\"多尺度\",\"时序预测\",\"可调用\"]");
        model("TSMixer", "TIME_SERIES", "Google Research", 2023, "TSMixer", "TSMixer: An All-MLP Architecture for Time Series Forecasting", "https://arxiv.org/abs/2303.06053", 60, false,
            "MLP 时序混合模型，推理成本低。", "[\"MLP\",\"时序基线\",\"可调用\"]");
        model("Transformer", "TIME_SERIES", "Vaswani et al.", 2017, "Transformer", "Attention Is All You Need", "https://arxiv.org/abs/1706.03762", 70, false,
            "标准 Transformer 时序基线。", "[\"Transformer\",\"基线模型\",\"可调用\"]");
        model("CNN_LSTM", "VISION_FUSION", "Platform Baseline", 2024, "CNN-LSTM", "CNN-LSTM fusion baseline", "", 110, false,
            "图像 CNN 特征与数值 LSTM 融合，适合云图和历史功率联合建模。", "[\"融合模型\",\"云图特征\",\"离线展示\"]");
        model("CNN_MLP", "VISION_FUSION", "Platform Baseline", 2024, "CNN-MLP", "CNN-MLP fusion baseline", "", 120, false,
            "图像特征和结构化数值特征融合的轻量基线。", "[\"融合模型\",\"轻量\",\"离线展示\"]");
        model("3DCNN_LSTM", "VISION_FUSION", "Platform Baseline", 2024, "3DCNN-LSTM", "3D CNN LSTM fusion baseline", "", 130, false,
            "三维卷积提取短时云图块特征，再由 LSTM 建模功率变化。", "[\"3D-CNN\",\"时空融合\",\"离线展示\"]");
        model("ConvLSTM_LSTM", "VISION_FUSION", "Platform Baseline", 2024, "ConvLSTM-LSTM", "ConvLSTM LSTM fusion baseline", "", 140, false,
            "ConvLSTM 云图时空编码与数值 LSTM 融合。", "[\"ConvLSTM\",\"融合模型\",\"离线展示\"]");
        model("SimVP_gSTA", "VIDEO_RECURSIVE", "OpenSTL", 2022, "SimVP", "SimVP: Simpler yet Better Video Prediction", "https://arxiv.org/abs/2206.05099", 210, true,
            "视频预测结构，用于连续云图帧外推和遮挡趋势分析。", "[\"视频预测\",\"云图外推\",\"离线展示\"]");
        model("TAU", "VIDEO_RECURSIVE", "OpenSTL", 2023, "TAU", "Temporal Attention Unit", "", 220, false,
            "时空聚合注意力结构，适合连续云图动态建模。", "[\"注意力\",\"视频预测\",\"离线展示\"]");
        model("ConvLSTM", "VIDEO_RECURSIVE", "Shi et al.", 2015, "ConvLSTM", "Convolutional LSTM Network", "https://arxiv.org/abs/1506.04214", 230, false,
            "经典卷积循环网络，适合云层运动序列建模。", "[\"ConvLSTM\",\"递归时空\",\"离线展示\"]");
        model("PredRNN", "VIDEO_RECURSIVE", "Wang et al.", 2017, "PredRNN", "PredRNN", "https://arxiv.org/abs/1704.03674", 240, false,
            "递归时空预测网络，用于视频帧序列预测。", "[\"PredRNN\",\"递归预测\",\"离线展示\"]");
        model("PredRNN++", "VIDEO_RECURSIVE", "Wang et al.", 2018, "PredRNN++", "PredRNN++", "https://arxiv.org/abs/1804.06300", 250, false,
            "PredRNN 增强版本，提升长期视频预测稳定性。", "[\"PredRNN++\",\"视频预测\",\"离线展示\"]");
        model("E3D_LSTM", "VIDEO_RECURSIVE", "Wang et al.", 2019, "E3D-LSTM", "Eidetic 3D LSTM", "https://arxiv.org/abs/1810.09949", 260, false,
            "三维门控时空记忆网络，适合局部云团运动建模。", "[\"E3D-LSTM\",\"时空记忆\",\"离线展示\"]");
        model("swinLSTM", "VIDEO_RECURSIVE", "OpenSTL", 2023, "SwinLSTM", "SwinLSTM", "", 270, false,
            "Swin Transformer 与 LSTM 结合的视频预测模型。", "[\"Swin\",\"Transformer\",\"离线展示\"]");
        model("SUNSET", "VIDEO_RECURSIVE", "Stanford", 2022, "SUNSET", "SUNSET solar forecasting", "https://github.com/stanford-solar/SUNSET", 280, false,
            "斯坦福太阳能云图预测方向模型，用于遥感图像短时预测展示。", "[\"SUNSET\",\"太阳能预测\",\"离线展示\"]");
    }

    private void model(String code, String family, String provider, int year, String displayFamily,
                       String paperTitle, String paperUrl, int sortOrder, boolean featured,
                       String shortDescription, String tags) {
        jdbcTemplate.update("""
            UPDATE model_info
            SET short_description = COALESCE(short_description, ?),
                tags = COALESCE(tags, ?),
                model_family = COALESCE(model_family, ?),
                provider = COALESCE(provider, ?),
                release_year = COALESCE(release_year, ?),
                paper_title = COALESCE(paper_title, ?),
                paper_url = COALESCE(paper_url, ?),
                capabilities = COALESCE(capabilities, ?),
                applicable_scenarios = COALESCE(applicable_scenarios, ?),
                advantages = COALESCE(advantages, ?),
                limitations = COALESCE(limitations, ?),
                supported_input_modes = COALESCE(supported_input_modes, ?),
                reference_info = COALESCE(reference_info, ?),
                marketplace_visible = COALESCE(marketplace_visible, TRUE),
                is_featured = COALESCE(is_featured, ?),
                sort_order = COALESCE(sort_order, ?)
            WHERE model_code = ?
            """,
            shortDescription,
            tags,
            family,
            provider,
            year,
            paperTitle,
            paperUrl,
            "[\"短时光伏功率预测\",\"模型能力展示\",\"与基线模型对比\"]",
            "[\"模型广场介绍\",\"预测能力对比\",\"教学与演示\"]",
            "[\"结构清晰\",\"适合横向对比\",\"可与平台预测任务联动\"]",
            "[\"论文指标来自公开资料或离线基线说明，不代表当前平台实测\",\"OFFLINE 模型仅展示介绍，不能直接调用\"]",
            family.equals("TIME_SERIES") ? "[\"STATION_HISTORY\",\"MANUAL_INPUT\",\"FILE_UPLOAD\"]" : "[\"IMAGE_SEQUENCE\",\"WEATHER_SERIES\",\"POWER_HISTORY\"]",
            "{\"metricSource\":\"论文或离线基线资料，非平台实时实测\",\"familyName\":\"" + displayFamily + "\"}",
            featured,
            sortOrder,
            code);
    }
}
