package com.example.pvplatform.module.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmToolCallParser {
    private final ObjectMapper objectMapper;

    public LlmToolCallParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentLlmDecision parse(String content) {
        String json = strip(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = text(root, "type", "final");
            String reason = text(root, "reason", "");
            String answer = text(root, "answer", "");
            String question = text(root, "question", "");
            List<AgentToolCallSpec> calls = new ArrayList<>();
            JsonNode toolCalls = root.get("toolCalls");
            if (toolCalls == null) toolCalls = root.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode node : toolCalls) {
                    String toolName = text(node, "toolName", text(node, "name", ""));
                    JsonNode argsNode = node.get("arguments");
                    Map<String, Object> args = argsNode == null || argsNode.isNull()
                        ? Map.of() : objectMapper.convertValue(argsNode, LinkedHashMap.class);
                    if (!toolName.isBlank()) {
                        calls.add(new AgentToolCallSpec(toolName, args));
                    }
                }
            }
            if ("tool_call".equals(type) && calls.isEmpty()) {
                type = "ask_user";
                question = question.isBlank() ? "我需要更多参数才能调用工具。" : question;
            }
            return new AgentLlmDecision(type, reason, calls, answer, question);
        } catch (Exception exception) {
            return new AgentLlmDecision("final", "模型输出不是合法 JSON", List.of(),
                content == null || content.isBlank() ? "模型没有返回可解析内容。" : content, "");
        }
    }

    private String strip(String content) {
        if (content == null) return "{}";
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : fallback;
    }
}
