package com.example.pvplatform.module.agent.runtime.alibaba;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.example.pvplatform.module.agent.runtime.alibaba.tool.SpringAiToolCallbackFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Duration;
import java.util.List;

@Component
public class AlibabaReactAgentFactory {
    private final SpringAiToolCallbackFactory tools;
    private final AlibabaAgentProperties properties;

    public AlibabaReactAgentFactory(SpringAiToolCallbackFactory tools, AlibabaAgentProperties properties) {
        this.tools = tools; this.properties = properties;
    }

    public ReactAgent create(ChatModel model, AgentRunContext context, SseEmitter emitter, String prompt) {
        List<ToolCallback> callbacks = tools.create(context, emitter, properties.getMaxToolCalls());
        return ReactAgent.builder()
            .name("pv_platform_agent")
            .model(model)
            .systemPrompt(prompt)
            .tools(callbacks)
            .compileConfig(CompileConfig.builder().recursionLimit(Math.max(4, properties.getMaxModelCalls() * 2)).build())
            .toolExecutionTimeout(Duration.ofMillis(properties.getToolTimeoutMs()))
            .parallelToolExecution(false)
            .returnReasoningContents(false)
            .build();
    }

    public java.util.Map<String, Object> requestWriteApproval(String toolName, java.util.Map<String, Object> arguments,
                                                               AgentRunContext context, SseEmitter emitter) {
        return tools.requestWriteApproval(toolName, arguments, context, emitter);
    }
}
