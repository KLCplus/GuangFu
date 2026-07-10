package com.example.pvplatform.module.analysis.llm;

import com.example.pvplatform.module.analysis.vo.AnalysisSectionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmResultParser {
    private final ObjectMapper objectMapper;

    public LlmResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedAnalysisReport parse(String rawText) {
        String cleaned = cleanup(rawText);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            String summary = text(root, "summary", "当前上下文不足以生成摘要。");
            String riskLevel = normalizeRisk(text(root, "riskLevel", "unknown"));
            List<AnalysisSectionVO> sections = parseSections(root.get("sections"));
            List<String> suggestions = parseStringArray(root.get("suggestions"));
            String markdown = text(root, "markdown", buildMarkdown(summary, sections, suggestions));
            return new ParsedAnalysisReport(summary, riskLevel, sections, suggestions, markdown, rawText);
        } catch (Exception e) {
            throw new AnalysisLlmException(502, "模型返回格式解析失败", rawText);
        }
    }

    private String cleanup(String rawText) {
        if (rawText == null) {
            return "";
        }
        String text = rawText.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).trim();
            }
        }
        return text;
    }

    private List<AnalysisSectionVO> parseSections(JsonNode node) {
        List<AnalysisSectionVO> sections = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                sections.add(new AnalysisSectionVO(
                    text(item, "title", "未命名章节"),
                    text(item, "content", "当前上下文不足以判断。")
                ));
            }
        }
        if (sections.isEmpty()) {
            sections.add(new AnalysisSectionVO("天气影响", "当前上下文不足以判断。"));
            sections.add(new AnalysisSectionVO("预测趋势", "当前上下文不足以判断。"));
            sections.add(new AnalysisSectionVO("异常诊断", "当前上下文不足以判断。"));
            sections.add(new AnalysisSectionVO("运维建议", "当前上下文不足以判断。"));
        }
        return sections;
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    values.add(item.asText().trim());
                }
            }
        }
        return values;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            return value.asText().trim();
        }
        return fallback;
    }

    private String normalizeRisk(String riskLevel) {
        String value = riskLevel == null ? "" : riskLevel.trim().toLowerCase();
        return switch (value) {
            case "low", "medium", "high" -> value;
            default -> "unknown";
        };
    }

    private String buildMarkdown(String summary, List<AnalysisSectionVO> sections, List<String> suggestions) {
        StringBuilder builder = new StringBuilder("# 综合分析报告\n\n");
        builder.append(summary).append("\n");
        for (AnalysisSectionVO section : sections) {
            builder.append("\n## ").append(section.title()).append("\n").append(section.content()).append("\n");
        }
        if (!suggestions.isEmpty()) {
            builder.append("\n## 建议\n");
            for (String suggestion : suggestions) {
                builder.append("- ").append(suggestion).append("\n");
            }
        }
        return builder.toString();
    }
}
