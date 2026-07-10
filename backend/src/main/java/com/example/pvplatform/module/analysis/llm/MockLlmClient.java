package com.example.pvplatform.module.analysis.llm;

import com.example.pvplatform.module.analysis.prompt.AnalysisPromptBuilder;
import com.example.pvplatform.module.analysis.service.AnalysisContext;
import com.example.pvplatform.module.analysis.vo.AnalysisSectionVO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockLlmClient implements LlmClient {
    private final LlmProperties properties;
    private final AnalysisPromptBuilder promptBuilder;

    public MockLlmClient(LlmProperties properties, AnalysisPromptBuilder promptBuilder) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public LlmGenerationResult generateAnalysisReport(AnalysisContext context, String userInstruction) {
        long started = System.currentTimeMillis();
        String stationName = context.station() == null ? "当前电站" : context.station().getStationName();
        List<AnalysisSectionVO> sections = List.of(
            new AnalysisSectionVO("天气影响", context.weather() == null ? "当前上下文不足以判断天气影响，天气分析已降级。" : "已读取最近天气数据，请结合云量、温度和湿度变化评估出力。"),
            new AnalysisSectionVO("预测趋势", context.predictionResults().isEmpty() ? "当前上下文不足以判断预测趋势。" : "已读取预测结果，可用于判断未来功率变化方向。"),
            new AnalysisSectionVO("异常诊断", context.powerRows().isEmpty() ? "当前上下文不足以判断异常。" : "已读取历史功率摘要，建议关注采样断档和功率波动。"),
            new AnalysisSectionVO("运维建议", "LLM 已关闭，当前为规则 fallback 报告；如需真实模型结果请启用 ANALYSIS_LLM_ENABLED 并配置 DEEPSEEK_API_KEY。")
        );
        List<String> suggestions = List.of("配置 DEEPSEEK_API_KEY 后重新生成报告", "结合电站告警和预测结果复核运维计划");
        String summary = stationName + "综合分析报告已通过规则 fallback 生成，真实 LLM 当前未启用。";
        String markdown = "# 综合分析报告\n\n" + summary + "\n\n## 运维建议\n- " + String.join("\n- ", suggestions);
        ParsedAnalysisReport report = new ParsedAnalysisReport(summary, "unknown", sections, suggestions, markdown, markdown);
        return new LlmGenerationResult(report, "mock-analysis-fallback",
            promptBuilder.buildPromptSnapshot(context, userInstruction), markdown, false,
            properties.getProvider(), System.currentTimeMillis() - started);
    }
}
