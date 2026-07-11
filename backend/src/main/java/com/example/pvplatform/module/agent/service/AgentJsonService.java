package com.example.pvplatform.module.agent.service;

import com.example.pvplatform.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentJsonService {
    private final ObjectMapper objectMapper;

    public AgentJsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(500, "Agent JSON 序列化失败");
        }
    }

    public Map<String, Object> map(String json) {
        Object value = value(json);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public Object value(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            return json;
        }
    }
}
