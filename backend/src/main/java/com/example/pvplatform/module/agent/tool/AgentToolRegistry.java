package com.example.pvplatform.module.agent.tool;

import com.example.pvplatform.module.agent.dto.AgentToolDTO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentToolRegistry {
    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(AgentTool::name, Function.identity(), (a, b) -> a));
    }

    public AgentTool require(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null || !tool.enabled()) {
            throw new IllegalArgumentException("工具不存在或未启用: " + name);
        }
        return tool;
    }

    public List<AgentTool> enabledTools(List<String> allowedNames) {
        return tools.values().stream()
            .filter(AgentTool::enabled)
            .filter(tool -> allowedNames == null || allowedNames.isEmpty() || allowedNames.contains(tool.name()))
            .sorted(Comparator.comparing(AgentTool::name))
            .toList();
    }

    public List<AgentToolDTO> list() {
        return tools.values().stream()
            .sorted(Comparator.comparing(AgentTool::name))
            .map(tool -> new AgentToolDTO(tool.name(), tool.displayName(), tool.category(), tool.permissionLevel(),
                tool.requiresApproval(), tool.enabled(), tool.description(), tool.inputSchema()))
            .toList();
    }

    public List<String> enabledNames() {
        return tools.values().stream().filter(AgentTool::enabled).map(AgentTool::name).sorted().toList();
    }
}
