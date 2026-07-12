from __future__ import annotations

from pathlib import Path
from typing import Any

from .events import stream_event, ui_instruction
from .skill_loader import SkillLoader
from .tool_router import DEFAULT_CAPABILITIES, ToolGateway, ToolRouter
from .types import AgentState, PlanStep, ToolResult


class PhotovoltaicAgentRuntime:
    """Migrated runtime foundation.

    Inspired by the reference LangGraph/CopilotKit runtime, but currently kept
    dependency-light so it can be tested before Spring Gateway integration.
    """

    def __init__(self, gateway: ToolGateway, skills_dir: str | Path):
        self.skill_loader = SkillLoader(skills_dir)
        self.skills = self.skill_loader.load_all()
        self.router = ToolRouter(gateway, DEFAULT_CAPABILITIES)

    def plan(self, task: str, context: dict[str, Any]) -> AgentState:
        skill = self.skill_loader.select(task, self.skills)
        station_id = int(context.get("stationId") or self._extract_station_id(task) or 1)
        state = AgentState(session_id=context.get("sessionId"), user_task=task, selected_skill=skill.name)
        state.plan = [
            PlanStep("understand", "Understand", "理解任务", "识别电站运行分析目标"),
            PlanStep("station", "Collect", "查询电站", "确认电站基础信息", "station.detail", {"stationId": station_id}),
            PlanStep("weather", "Collect", "查询天气", "收集当前天气和发电影响", "weather.current", {"stationId": station_id}),
            PlanStep("prediction-list", "Collect", "查询预测", "查找最近预测任务", "prediction.list", {"stationId": station_id}),
            PlanStep("evaluate", "Evaluate", "校验数据", "检查工具结果完整性"),
            PlanStep("recommend", "Recommend", "生成建议", "形成运维建议"),
            PlanStep("synthesize", "Synthesize", "生成结论", "输出结构化结论和 UI 指令"),
        ]
        return state

    def run(self, task: str, context: dict[str, Any]) -> AgentState:
        state = self.plan(task, context)
        for step in state.plan:
            result = self.router.execute_step(step, context)
            if result:
                state.tool_results.append(result)
                state.ui.extend(self._ui_for_result(result))
        state.final_answer = self._synthesize(state)
        return state

    def iter_events(self, task: str, context: dict[str, Any]):
        state = self.plan(task, context)
        yield stream_event("run_started", {
            "sessionId": state.session_id,
            "skill": state.selected_skill,
            "task": state.user_task,
        })
        for step in state.plan:
            yield stream_event("step_started", step)
            result = self.router.execute_step(step, context)
            if result:
                state.tool_results.append(result)
                yield stream_event("tool_result", result)
                for instruction in self._ui_for_result(result):
                    state.ui.append(instruction)
                    yield stream_event("ui_instruction", instruction)
            yield stream_event("step_completed", {
                "stepId": step.step_id,
                "title": step.title,
            })
        state.final_answer = self._synthesize(state)
        yield stream_event("run_completed", {
            "sessionId": state.session_id,
            "skill": state.selected_skill,
            "answer": state.final_answer,
            "ui": [instruction.__dict__ for instruction in state.ui],
        })

    def _ui_for_result(self, result: ToolResult):
        data = result.data
        if result.tool_name == "station.detail" and result.success:
            return [ui_instruction("StationSummaryCard", {
                "stationName": data.get("stationName") or data.get("name") or "未知电站",
                "capacity": data.get("capacity"),
                "status": data.get("status"),
                "location": " ".join(str(data.get(k) or "") for k in ("province", "city", "address")).strip(),
            })]
        if result.tool_name == "weather.current" and result.success:
            return [ui_instruction("WeatherImpactCard", {
                "weather": data.get("weather"),
                "temperature": data.get("temperature"),
                "humidity": data.get("humidity"),
                "windSpeed": data.get("windSpeed"),
                "impact": result.summary,
            })]
        if result.tool_name.startswith("prediction.") and result.success:
            return [ui_instruction("PredictionTrendCard", {
                "summary": result.summary,
                "highlights": result.highlights,
                "data": data,
            })]
        if not result.success:
            return [ui_instruction("ErrorRecoveryCard", {
                "toolName": result.tool_name,
                "error": result.error or result.summary,
            })]
        return []

    def _synthesize(self, state: AgentState) -> str:
        failed = [item for item in state.tool_results if not item.success]
        summaries = [item.summary for item in state.tool_results if item.summary]
        if failed:
            return "部分数据获取失败：" + "；".join(item.summary for item in failed)
        return "；".join(summaries) if summaries else "已完成任务规划，但没有可用工具结果。"

    def _extract_station_id(self, task: str) -> int | None:
        digits = "".join(ch if ch.isdigit() else " " for ch in task).split()
        return int(digits[0]) if digits else None
