package com.example.pvplatform.module.analysis.prompt;

import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.analysis.service.AnalysisContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnalysisPromptBuilder {
    public static final String SYSTEM_PROMPT = """
        你是一个光伏电站智能分析 Agent，负责基于平台提供的真实业务上下文生成结构化综合分析报告。
        你必须遵守：
        1. 只基于给定上下文分析，不要编造没有提供的数据。
        2. 如果数据缺失，要明确说明“当前上下文不足以判断”。
        3. 输出必须是 JSON，不要输出 Markdown 代码块，不要输出额外解释。
        4. 报告面向光伏运维和平台用户，语言专业、简洁、可执行。
        5. 必须包含 summary、sections、markdown、riskLevel、suggestions。
        6. 每个结论都尽量说明依据来自天气、预测、历史功率或电站信息。
        """;

    private final ObjectMapper objectMapper;

    public AnalysisPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildUserPrompt(AnalysisContext context, String userInstruction) {
        return """
            用户任务：
            %s

            当前用户：
            %s

            电站上下文：
            %s

            天气上下文：
            %s

            预测任务上下文：
            %s

            历史功率摘要：
            %s

            平台上下文：
            %s

            请严格返回 JSON，格式如下：

            {
              "summary": "一句话总结",
              "riskLevel": "low | medium | high | unknown",
              "sections": [
                {
                  "title": "天气影响",
                  "content": "..."
                },
                {
                  "title": "预测趋势",
                  "content": "..."
                },
                {
                  "title": "异常诊断",
                  "content": "..."
                },
                {
                  "title": "运维建议",
                  "content": "..."
                }
              ],
              "suggestions": [
                "建议1",
                "建议2"
              ],
              "markdown": "# 综合分析报告\\n..."
            }
            """.formatted(
            blankToDefault(userInstruction, "生成光伏电站综合分析报告"),
            json(context.user()),
            json(context.stationContext()),
            json(context.weatherContext()),
            json(context.predictionContext()),
            json(context.powerSummary()),
            json(context.platformContext())
        );
    }

    public String buildPromptSnapshot(AnalysisContext context, String userInstruction) {
        return json(Map.of(
            "system", SYSTEM_PROMPT,
            "user", buildUserPrompt(context, userInstruction)
        ));
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "分析 Prompt 序列化失败");
        }
    }
}
