package com.example.pvplatform.module.agent.runtime.alibaba.skill;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class AgentSkillLoader {
    private static final List<String> SKILLS = List.of("station-inspection", "report-generation", "profile-management");

    public List<AgentSkill> loadAll() {
        return SKILLS.stream().map(this::load).toList();
    }

    private AgentSkill load(String name) {
        String description = "";
        List<String> tools = new ArrayList<>();
        List<String> sections = new ArrayList<>();
        String mode = "";
        Resource resource = resolveResource(name);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("description:")) description = line.substring("description:".length()).trim();
                else if (line.startsWith("allowed_tools:")) mode = "tools";
                else if (line.startsWith("output_sections:")) mode = "sections";
                else if (line.trim().startsWith("- ")) {
                    if ("tools".equals(mode)) tools.add(line.trim().substring(2));
                    if ("sections".equals(mode)) sections.add(line.trim().substring(2));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载 Agent Skill: " + name, exception);
        }
        return new AgentSkill(name, description, List.copyOf(tools), List.copyOf(sections));
    }

    private Resource resolveResource(String name) {
        String relPath = "agent-skills/" + name + "/SKILL.md";
        // 优先从工作目录文件系统读取（适配沙箱环境）
        Path fsPath = Path.of(relPath);
        if (fsPath.toFile().exists()) {
            return new FileSystemResource(fsPath);
        }
        // 回退到 classpath
        return new ClassPathResource(relPath);
    }
}
