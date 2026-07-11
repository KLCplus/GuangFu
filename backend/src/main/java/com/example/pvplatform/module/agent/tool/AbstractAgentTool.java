package com.example.pvplatform.module.agent.tool;

import com.example.pvplatform.common.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractAgentTool implements AgentTool {
    @Override
    public boolean enabled() {
        return true;
    }

    protected Long longArg(Map<String, Object> args, String key, boolean required) {
        Object value = args == null ? null : args.get(key);
        if (value == null && required) {
            throw new BusinessException(400, "缺少参数: " + key);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "参数不是有效数字: " + key);
        }
    }

    protected Boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    protected String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object value = args == null ? null : args.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }

    protected Map<String, Object> schema(Object... entries) {
        Map<String, Object> schema = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            schema.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return schema;
    }

    protected ToolExecutionResult guard(CheckedSupplier supplier) {
        try {
            return supplier.get();
        } catch (BusinessException exception) {
            return ToolExecutionResult.failure("BUSINESS_ERROR", exception.getMessage());
        } catch (Exception exception) {
            return ToolExecutionResult.failure("TOOL_EXECUTION_FAILED", exception.getMessage() == null ? "工具执行失败" : exception.getMessage());
        }
    }

    @FunctionalInterface
    protected interface CheckedSupplier {
        ToolExecutionResult get();
    }
}
