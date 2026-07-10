package com.example.pvplatform.module.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.pvplatform.common.PageResult;
import com.example.pvplatform.common.exception.BusinessException;
import com.example.pvplatform.module.analysis.dto.AnalysisRequest;
import com.example.pvplatform.module.analysis.llm.AnalysisLlmException;
import com.example.pvplatform.module.analysis.llm.LlmGenerationResult;
import com.example.pvplatform.module.analysis.llm.LlmProperties;
import com.example.pvplatform.module.analysis.llm.ParsedAnalysisReport;
import com.example.pvplatform.module.analysis.vo.AnalysisReportListItemVO;
import com.example.pvplatform.module.analysis.vo.AnalysisReportVO;
import com.example.pvplatform.module.analysis.vo.AnalysisSectionVO;
import com.example.pvplatform.module.station.service.StationPermissionService;
import com.example.pvplatform.persistence.entity.AnalysisReportDO;
import com.example.pvplatform.persistence.mapper.AnalysisReportMapper;
import com.example.pvplatform.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {
    private final StationPermissionService stationPermissionService;
    private final AnalysisContextService contextService;
    private final AnalysisAgentService agentService;
    private final AnalysisReportMapper reportMapper;
    private final ObjectMapper objectMapper;
    private final LlmProperties llmProperties;

    public AnalysisService(StationPermissionService stationPermissionService,
                           AnalysisContextService contextService,
                           AnalysisAgentService agentService,
                           AnalysisReportMapper reportMapper,
                           ObjectMapper objectMapper,
                           LlmProperties llmProperties) {
        this.stationPermissionService = stationPermissionService;
        this.contextService = contextService;
        this.agentService = agentService;
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AnalysisReportVO report(AnalysisRequest request) {
        AnalysisContext context = contextService.build(request);
        String instruction = firstText(request.userInstruction(), request.title(), "生成光伏电站综合分析报告");
        String title = firstText(request.title(),
            context.station().getStationName() + "综合分析报告");

        AnalysisReportDO row = new AnalysisReportDO();
        row.setUserId(context.userId());
        row.setStationId(context.station().getStationId());
        row.setTaskId(context.task() == null ? null : context.task().getTaskId());
        row.setTitle(title);
        row.setIncludeWeather(request.includeWeather());
        row.setIncludePrediction(request.includePrediction());
        row.setModelName(llmProperties.isEnabled() ? llmProperties.getModel() : "mock-analysis-fallback");
        row.setContextSnapshot(contextSnapshot(context));
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(row.getCreatedAt());
        reportMapper.insert(row);

        try {
            LlmGenerationResult result = agentService.generate(context, instruction);
            ParsedAnalysisReport parsed = result.report();
            row.setSummary(parsed.summary());
            row.setRiskLevel(parsed.riskLevel());
            row.setWeatherAnalysis(section(parsed.sections(), "天气"));
            row.setPredictionAnalysis(section(parsed.sections(), "预测"));
            row.setAbnormalAnalysis(section(parsed.sections(), "异常"));
            row.setSuggestion(parsed.suggestions().isEmpty()
                ? section(parsed.sections(), "建议") : String.join("\n", parsed.suggestions()));
            row.setReportContent(parsed.markdown());
            row.setReportJson(reportJson(parsed, result));
            row.setModelName(result.modelName());
            row.setPromptSnapshot(result.promptSnapshot());
            row.setRawResponse(result.rawResponse());
            row.setStatus("SUCCESS");
            row.setErrorMessage(null);
            row.setUpdatedAt(LocalDateTime.now());
            reportMapper.updateById(row);
            return toVO(row);
        } catch (AnalysisLlmException e) {
            markFailed(row, e.getMessage(), e.getRawResponse());
            throw e;
        } catch (BusinessException e) {
            markFailed(row, e.getMessage(), null);
            throw e;
        } catch (Exception e) {
            markFailed(row, "DeepSeek API 调用失败", null);
            throw new BusinessException(502, "DeepSeek API 调用失败");
        }
    }

    public PageResult<AnalysisReportListItemVO> history(int pageNum, int pageSize, Long stationId) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "分页参数不合法");
        }
        Long userId = SecurityUtils.requireCurrentUserId();
        if (stationId != null) {
            stationPermissionService.requireView(stationId);
        }
        var query = Wrappers.<AnalysisReportDO>lambdaQuery()
            .eq(!stationPermissionService.isAdmin(), AnalysisReportDO::getUserId, userId)
            .eq(stationId != null, AnalysisReportDO::getStationId, stationId)
            .orderByDesc(AnalysisReportDO::getCreatedAt);
        Page<AnalysisReportDO> page = reportMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream()
            .map(r -> new AnalysisReportListItemVO(r.getReportId(), r.getReportId(), r.getStationId(), r.getTaskId(),
                r.getTitle(), r.getSummary(), r.getRiskLevel(), r.getStatus(), r.getModelName(), r.getCreatedAt()))
            .toList());
    }

    public AnalysisReportVO detail(Long reportId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        AnalysisReportDO row = reportMapper.selectOne(Wrappers.<AnalysisReportDO>lambdaQuery()
            .eq(AnalysisReportDO::getReportId, reportId)
            .eq(!stationPermissionService.isAdmin(), AnalysisReportDO::getUserId, userId));
        if (row == null) {
            throw new BusinessException(404, "报告不存在");
        }
        return toVO(row);
    }

    private void markFailed(AnalysisReportDO row, String message, String rawResponse) {
        row.setStatus("FAILED");
        row.setErrorMessage(message);
        if (rawResponse != null && !rawResponse.isBlank()) {
            row.setRawResponse(rawResponse);
        }
        row.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(row);
    }

    private String contextSnapshot(AnalysisContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("user", context.user());
        snapshot.put("stationContext", context.stationContext());
        snapshot.put("weatherContext", context.weatherContext());
        snapshot.put("predictionContext", context.predictionContext());
        snapshot.put("powerSummary", context.powerSummary());
        snapshot.put("platformContext", context.platformContext());
        return json(snapshot);
    }

    private String reportJson(ParsedAnalysisReport parsed, LlmGenerationResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", parsed.summary());
        body.put("riskLevel", parsed.riskLevel());
        body.put("sections", parsed.sections());
        body.put("suggestions", parsed.suggestions());
        body.put("markdown", parsed.markdown());
        body.put("modelName", result.modelName());
        body.put("llmEnabled", result.llmEnabled());
        body.put("llmProvider", result.llmProvider());
        body.put("durationMs", result.durationMs());
        return json(body);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "报告结构化数据生成失败");
        }
    }

    private AnalysisReportVO toVO(AnalysisReportDO r) {
        ReportJson parsed = parseReportJson(r);
        List<AnalysisSectionVO> sections = parsed.sections().isEmpty() ? fallbackSections(r) : parsed.sections();
        List<String> suggestions = parsed.suggestions().isEmpty() && r.getSuggestion() != null
            ? List.of(r.getSuggestion().split("\\n")) : parsed.suggestions();
        String summary = firstText(r.getSummary(), parsed.summary());
        String markdown = firstText(parsed.markdown(), r.getReportContent());
        return new AnalysisReportVO(r.getReportId(), r.getReportId(), r.getUserId(), r.getStationId(), r.getTaskId(),
            r.getTitle(), summary, firstText(r.getRiskLevel(), parsed.riskLevel(), "unknown"),
            sections, suggestions, markdown, r.getWeatherAnalysis(), r.getPredictionAnalysis(),
            r.getAbnormalAnalysis(), r.getSuggestion(), r.getReportContent(), r.getReportJson(),
            r.getIncludeWeather(), r.getIncludePrediction(), r.getModelName(), r.getStatus(), r.getErrorMessage(),
            r.getRawResponse(), r.getPromptSnapshot(), r.getContextSnapshot(), parsed.llmEnabled(),
            firstText(parsed.llmProvider(), llmProperties.getProvider()), r.getCreatedAt(), r.getUpdatedAt());
    }

    private ReportJson parseReportJson(AnalysisReportDO row) {
        if (row.getReportJson() == null || row.getReportJson().isBlank()) {
            return new ReportJson("", "", List.of(), List.of(), "", null, "");
        }
        try {
            JsonNode root = objectMapper.readTree(row.getReportJson());
            List<AnalysisSectionVO> sections = new ArrayList<>();
            JsonNode sectionNode = root.get("sections");
            if (sectionNode != null && sectionNode.isArray()) {
                for (JsonNode item : sectionNode) {
                    sections.add(new AnalysisSectionVO(text(item, "title"), text(item, "content")));
                }
            }
            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionNode = root.get("suggestions");
            if (suggestionNode != null && suggestionNode.isArray()) {
                suggestionNode.forEach(item -> {
                    if (item.isTextual()) {
                        suggestions.add(item.asText());
                    }
                });
            }
            Boolean llmEnabled = root.has("llmEnabled") && !root.get("llmEnabled").isNull()
                ? root.get("llmEnabled").asBoolean() : null;
            return new ReportJson(text(root, "summary"), text(root, "riskLevel"), sections, suggestions,
                text(root, "markdown"), llmEnabled, text(root, "llmProvider"));
        } catch (Exception e) {
            return new ReportJson("", "", List.of(), List.of(), "", null, "");
        }
    }

    private List<AnalysisSectionVO> fallbackSections(AnalysisReportDO row) {
        List<AnalysisSectionVO> sections = new ArrayList<>();
        addSection(sections, "天气影响", row.getWeatherAnalysis());
        addSection(sections, "预测趋势", row.getPredictionAnalysis());
        addSection(sections, "异常诊断", row.getAbnormalAnalysis());
        addSection(sections, "运维建议", row.getSuggestion());
        return sections;
    }

    private void addSection(List<AnalysisSectionVO> sections, String title, String content) {
        if (content != null && !content.isBlank()) {
            sections.add(new AnalysisSectionVO(title, content));
        }
    }

    private String section(List<AnalysisSectionVO> sections, String keyword) {
        return sections.stream()
            .filter(item -> item.title() != null && item.title().contains(keyword))
            .map(AnalysisSectionVO::content)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record ReportJson(String summary, String riskLevel, List<AnalysisSectionVO> sections,
                              List<String> suggestions, String markdown, Boolean llmEnabled,
                              String llmProvider) {}
}
