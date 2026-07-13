from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .events import stream_event, ui_instruction
from .memory import extract_memory_candidates
from .skill_loader import SkillLoader
from .tool_router import DEFAULT_CAPABILITIES, ToolGateway, ToolRouter
from .types import AgentState, PlanStep, ToolResult


class PhotovoltaicAgentRuntime:
    """Migrated runtime foundation.

    Inspired by the reference LangGraph/CopilotKit runtime, but currently kept
    dependency-light so it can be tested before Spring Gateway integration.
    """

    def __init__(self, gateway: ToolGateway, skills_dir: str | Path,
                 llm_gateway: Any | None = None, memory_gateway: Any | None = None):
        self.skill_loader = SkillLoader(skills_dir)
        self.skills = self.skill_loader.load_all()
        self.router = ToolRouter(gateway, DEFAULT_CAPABILITIES)
        self.llm_gateway = llm_gateway
        self.memory_gateway = memory_gateway

    def plan(self, task: str, context: dict[str, Any]) -> AgentState:
        skill = self.skill_loader.select(task, self.skills)
        memories = self._load_memories(context)
        context["memories"] = memories
        conversation_history = self._conversation_history(context)
        explicit_station_id = self._extract_station_id(task)
        station_id = int(
            context.get("stationId")
            or explicit_station_id
            or self._station_id_from_history(conversation_history)
            or self._default_station_id(memories)
            or 1
        )
        state = AgentState(
            session_id=context.get("sessionId"),
            user_task=task,
            selected_skill=skill.name,
            memories=memories,
            conversation_history=conversation_history,
        )
        explicit_tool = self._explicit_tool_step(task, context)
        if explicit_tool:
            state.plan = [
                PlanStep("understand", "Understand", "理解任务", "识别用户要查询或执行的平台功能"),
                explicit_tool,
                PlanStep("synthesize", "Synthesize", "生成结论", "基于工具结果回答用户"),
            ]
            return state

        manager_steps = self._manager_steps(task, context, station_id)
        if manager_steps and not self._should_use_station_analysis_chain(task):
            state.plan = [PlanStep("understand", "Understand", "理解任务", "识别项目管家请求")]
            state.plan.extend(manager_steps)
            state.plan.append(PlanStep("synthesize", "Synthesize", "生成结论", "基于工具结果回答用户"))
            return state

        if not self._is_station_analysis_task(task):
            state.plan = [
                PlanStep("understand", "Understand", "理解任务", "识别为普通对话或暂未接入的项目功能"),
                PlanStep("synthesize", "Synthesize", "生成结论", "说明可用能力或提示需要补充的信息"),
            ]
            return state

        state.plan = [
            PlanStep("understand", "Understand", "理解任务", "识别电站运行分析目标"),
            PlanStep("station", "Collect", "查询电站", "确认电站基础信息", "station.detail", {"stationId": station_id}),
            PlanStep("weather", "Collect", "查询天气", "收集当前天气和发电影响", "weather.current", {"stationId": station_id}),
            PlanStep("prediction-list", "Collect", "查询预测", "查找最近预测任务", "prediction.list", {"stationId": station_id}),
            PlanStep("evaluate", "Evaluate", "校验数据", "检查工具结果完整性"),
            PlanStep("recommend", "Recommend", "生成建议", "形成运维建议"),
        ]
        if self._wants_report(task):
            state.plan.append(PlanStep("report-generate", "Synthesize", "生成报告", "生成并保存综合分析报告", "report.generate", {
                "stationId": station_id,
                "title": f"{station_id}号电站综合分析报告",
                "includeWeather": True,
                "includePrediction": True,
            }))
        state.plan.append(PlanStep("synthesize", "Synthesize", "生成结论", "输出结构化结论和 UI 指令"))
        return state

    def run(self, task: str, context: dict[str, Any]) -> AgentState:
        state = self.plan(task, context)
        for step in list(state.plan):
            result = self._execute_step(state, step, context)
            if self._is_approval_required(result):
                state.final_answer = self._approval_message(result)
                self._persist_memory_candidates(state.user_task, context)
                return state
            follow_up = self._prediction_detail_follow_up(result)
            if follow_up:
                state.plan.insert(state.plan.index(step) + 1, follow_up)
                self._execute_step(state, follow_up, context)
        state.final_answer = self._synthesize(state)
        self._persist_memory_candidates(state.user_task, context)
        return state

    def iter_events(self, task: str, context: dict[str, Any]):
        state = self.plan(task, context)
        yield stream_event("run_started", {
            "sessionId": state.session_id,
            "skill": state.selected_skill,
            "task": state.user_task,
        })
        index = 0
        while index < len(state.plan):
            step = state.plan[index]
            yield stream_event("step_started", step)
            result = self.router.execute_step(step, context)
            if result:
                state.tool_results.append(result)
                yield stream_event("tool_result", result)
                for instruction in self._ui_for_result(result):
                    state.ui.append(instruction)
                    yield stream_event("ui_instruction", instruction)
                if self._is_approval_required(result):
                    yield stream_event("approval_required", self._approval_event_data(result))
                    yield stream_event("step_completed", {
                        "stepId": step.step_id,
                        "title": step.title,
                    })
                    state.final_answer = self._approval_message(result)
                    self._persist_memory_candidates(state.user_task, context)
                    return
            yield stream_event("step_completed", {
                "stepId": step.step_id,
                "title": step.title,
            })
            follow_up = self._prediction_detail_follow_up(result)
            if follow_up:
                state.plan.insert(index + 1, follow_up)
            index += 1
        state.final_answer = self._synthesize(state)
        self._persist_memory_candidates(state.user_task, context)
        yield stream_event("run_completed", {
            "sessionId": state.session_id,
            "skill": state.selected_skill,
            "answer": state.final_answer,
            "ui": [instruction.__dict__ for instruction in state.ui],
        })

    def _execute_step(self, state: AgentState, step: PlanStep, context: dict[str, Any]) -> ToolResult | None:
        result = self.router.execute_step(step, context)
        if result:
            state.tool_results.append(result)
            state.ui.extend(self._ui_for_result(result))
        return result

    def _prediction_detail_follow_up(self, result: ToolResult | None) -> PlanStep | None:
        if result is None or result.tool_name != "prediction.list" or not result.success:
            return None
        task_id = self._latest_prediction_task_id(result.data)
        if task_id is None:
            return None
        return PlanStep(
            "prediction-detail",
            "Collect",
            "查询预测详情",
            "读取最近预测任务的曲线和趋势",
            "prediction.detail",
            {"taskId": task_id},
        )

    def _latest_prediction_task_id(self, data: dict[str, Any]) -> int | None:
        if not isinstance(data, dict):
            return None
        records = data.get("records")
        if not isinstance(records, list) or not records:
            return None
        first = records[0]
        if not isinstance(first, dict):
            return None
        task_id = first.get("taskId") or first.get("id") or first.get("predictionTaskId")
        try:
            return int(task_id)
        except (TypeError, ValueError):
            return None

    def _ui_for_result(self, result: ToolResult):
        data = result.data
        data_map = data if isinstance(data, dict) else {}
        if self._is_approval_required(result):
            return [ui_instruction("ApprovalActionCard", self._approval_event_data(result))]
        if result.tool_name == "station.detail" and result.success:
            return [ui_instruction("StationSummaryCard", {
                "stationName": data_map.get("stationName") or data_map.get("name") or "未知电站",
                "capacity": data_map.get("capacity"),
                "status": data_map.get("status"),
                "location": " ".join(str(data_map.get(k) or "") for k in ("province", "city", "address")).strip(),
            })]
        if result.tool_name == "weather.current" and result.success:
            return [ui_instruction("WeatherImpactCard", {
                "weather": data_map.get("weather"),
                "temperature": data_map.get("temperature"),
                "humidity": data_map.get("humidity"),
                "windSpeed": data_map.get("windSpeed"),
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

    def _is_approval_required(self, result: ToolResult | None) -> bool:
        if result is None:
            return False
        data = result.data if isinstance(result.data, dict) else {}
        return result.error == "APPROVAL_REQUIRED" or data.get("approvalRequired") is True

    def _approval_event_data(self, result: ToolResult) -> dict[str, Any]:
        data = result.data if isinstance(result.data, dict) else {}
        return {
            "approvalId": data.get("approvalId"),
            "toolCallId": data.get("clientToolCallId") or data.get("toolCallId"),
            "toolName": data.get("toolName") or result.tool_name,
            "reason": data.get("reason") or result.summary,
            "arguments": data.get("arguments") or {},
        }

    def _approval_message(self, result: ToolResult | None) -> str:
        if result is None:
            return "需要用户确认后继续。"
        return self._approval_event_data(result)["reason"] or "需要用户确认后继续。"

    def _synthesize(self, state: AgentState) -> str:
        failed = [item for item in state.tool_results if not item.success]
        summaries = [item.summary for item in state.tool_results if item.summary]
        if not state.tool_results:
            return self._general_answer(state)
        if self.llm_gateway and summaries:
            try:
                answer = self._llm_synthesize(state)
                if answer:
                    return answer
            except Exception as exc:
                fallback = "；".join(summaries) if summaries else "没有可用工具结果。"
                return f"LLM 不可用，已降级为工具结果摘要：{fallback}（原因：{exc}）"
        if failed:
            return "部分数据获取失败：" + "；".join(item.summary for item in failed)
        return "；".join(summaries) if summaries else self._general_answer(state)

    def _general_answer(self, state: AgentState) -> str:
        if self.llm_gateway:
            try:
                response = self.llm_gateway.chat_completions(
                    messages=[
                        {
                            "role": "system",
                            "content": (
                                "你是光伏预测与运维平台的项目管家。先回答用户问题，不要编造实时业务数据。"
                                "当问题需要真实数据时，说明可以调用哪些工具；当用户问能力范围时，直接概括能力。"
                                "结合 conversationHistory 理解省略、指代和追问。"
                            ),
                        },
                        {
                            "role": "user",
                            "content": json.dumps({
                                "task": state.user_task,
                                "conversationHistory": state.conversation_history,
                                "availableCapabilities": self._capability_summary(),
                            }, ensure_ascii=False),
                        },
                    ],
                    temperature=0.3,
                    max_tokens=700,
                )
                choices = response.get("choices") or []
                content = ((choices[0].get("message") or {}).get("content") if choices else "")
                if isinstance(content, str) and content.strip():
                    return content.strip()
            except Exception:
                pass
        return self._capability_summary()

    def _capability_summary(self) -> str:
        return (
            "我可以作为光伏平台项目管家协助你：查询电站和公开电站信息、分析天气影响、查看预测任务和预测详情、"
            "查询实时/历史功率、天气预报、仪表盘概览、模型列表和模型详情、说明模型运行所需输入、处理云图预测输入要求、"
            "查看报告和生成需确认的报告、查询 API Key 与调用用量、查看新闻/通知/未读数、钱包余额、市场套餐和当前用户资料，"
            "也可以在确认后修改个人资料、标记通知已读等。"
            "涉及真实数据时我会调用受控工具；一般解释、操作指引、能力说明和排障建议会直接回答。"
        )

    def _llm_synthesize(self, state: AgentState) -> str:
        tool_context = [
            {
                "tool": item.tool_name,
                "success": item.success,
                "summary": item.summary,
                "highlights": item.highlights,
                "error": item.error,
            }
            for item in state.tool_results
        ]
        response = self.llm_gateway.chat_completions(
            messages=[
                {
                    "role": "system",
                    "content": (
                        "你是光伏预测与运维平台的项目管家。基于给定工具结果回答；"
                        "如果数据不完整，要明确指出缺口。不要让工具过程盖过结论。"
                        "结合 conversationHistory 理解用户追问，但结论必须以本轮工具结果为准。"
                    ),
                },
                {
                    "role": "user",
                    "content": json.dumps({
                        "task": state.user_task,
                        "skill": state.selected_skill,
                        "conversationHistory": state.conversation_history,
                        "memories": state.memories,
                        "toolResults": tool_context,
                    }, ensure_ascii=False),
                },
            ],
            temperature=0.2,
            max_tokens=900,
        )
        choices = response.get("choices") or []
        if not choices:
            return ""
        message = choices[0].get("message") or {}
        content = message.get("content")
        return content.strip() if isinstance(content, str) else ""


    def _explicit_tool_step(self, task: str, context: dict[str, Any]) -> PlanStep | None:
        tool_name = context.get("preferredTool")
        if not isinstance(tool_name, str) or tool_name not in self.router.capabilities:
            return None
        arguments = context.get("toolArguments") if isinstance(context.get("toolArguments"), dict) else {}
        return PlanStep("tool-explicit", "Collect", self._tool_title(tool_name), "按用户指定工具执行", tool_name, arguments)

    def _manager_steps(self, task: str, context: dict[str, Any], station_id: int) -> list[PlanStep]:
        text = task.lower()
        steps: list[PlanStep] = []
        task_id = self._extract_task_id(task)
        report_id = self._extract_report_id(task)
        model_id = self._extract_model_id(task)
        explicit_station_id = self._extract_station_id(task)
        history_station_id = self._station_id_from_history(self._conversation_history(context))
        has_station_context = explicit_station_id is not None or context.get("stationId") is not None or history_station_id is not None

        def add(step_id: str, title: str, tool_name: str, arguments: dict[str, Any] | None = None):
            steps.append(PlanStep(step_id, "Collect", title, self._tool_title(tool_name), tool_name, arguments or {}))

        if any(word in task for word in ("修改个人", "更新个人", "改昵称", "改邮箱", "改手机号", "修改昵称", "修改邮箱", "修改手机号")):
            args = {}
            for key in ("nickname", "email", "phone", "avatarUrl", "gender"):
                if key in context:
                    args[key] = context[key]
            add("user-profile-update", "修改个人资料", "user.profile.update", args)
        elif any(word in task for word in ("个人信息", "个人资料", "我的资料", "用户信息", "我的账号", "账户信息")) or "profile" in text:
            add("user-profile", "查询个人信息", "user.profile")

        if "仪表盘" in task or "dashboard" in text or "概览" in task:
            add("dashboard-overview", "查询仪表盘概览", "dashboard.overview", {"stationId": explicit_station_id} if explicit_station_id else {})

        if "电站列表" in task or "所有电站" in task or "我的电站" in task:
            add("station-list", "查询电站列表", "station.list")
        elif any(word in task for word in ("电站", "站点")) and not any(word in task for word in ("运行", "分析", "天气", "预测")):
            add("station-detail", "查询电站", "station.detail", {"stationId": station_id})

        if ("天气预报" in task or "forecast" in text) and ("电站" in task or has_station_context):
            add("weather-forecast", "查询天气预报", "weather.forecast", {"stationId": station_id})
        elif ("天气预报" in task or "forecast" in text) and ("电站" not in task and not has_station_context):
            location = context.get("location") or self._extract_location(task)
            add("weather-location-forecast", "查询地点天气预报", "weather.locationForecast", {"location": location} if location else {})
        elif "城市天气" in task or "当地天气" in task or "天气" in task and "电站" not in task and not has_station_context:
            location = context.get("location") or self._extract_location(task)
            add("weather-location", "查询地点天气", "weather.location", {"location": location} if location else {})
        elif "天气" in task and has_station_context:
            add("weather-current", "查询电站天气", "weather.current", {"stationId": station_id})

        if any(word in task for word in ("实时功率", "实时数据", "当前功率", "最新功率")):
            add("pv-realtime", "查询实时功率", "pv.realtime", {"stationId": station_id})
        elif any(word in task for word in ("历史功率", "历史数据", "功率曲线", "发电历史")):
            args = {"stationId": station_id}
            for key in ("startTime", "endTime", "interval"):
                if key in context:
                    args[key] = context[key]
            add("pv-history", "查询历史功率", "pv.history", args)

        if "预测详情" in task and task_id:
            add("prediction-detail", "查询预测详情", "prediction.detail", {"taskId": task_id})
        elif "预测" in task and not any(word in task for word in ("运行", "综合分析")):
            add("prediction-list", "查询预测列表", "prediction.list", {"stationId": explicit_station_id} if explicit_station_id else {})

        if "报告详情" in task and report_id:
            add("report-detail", "查询报告详情", "report.detail", {"reportId": report_id})
        elif "历史报告" in task or "报告列表" in task or ("报告" in task and "生成" not in task and "创建" not in task):
            add("report-list", "查询报告列表", "report.list", {"stationId": station_id} if self._extract_station_id(task) else {})
        elif "会话报告" in task or "工作报告" in task:
            add("conversation-report", "生成会话报告", "report.conversation")

        if "云图" in task or "cloud" in text:
            cloud_args = context.get("cloudArguments") if isinstance(context.get("cloudArguments"), dict) else {}
            add("cloud-predict", "运行云图预测", "cloud.predict", cloud_args)

        if ("模型" in task or "model" in text) and any(word in task for word in ("运行", "调用", "执行", "创建预测")):
            model_args = context.get("modelRunArguments") if isinstance(context.get("modelRunArguments"), dict) else {}
            add("model-run", "运行预测模型", "model.run", model_args)
        elif "模型详情" in task and model_id:
            add("model-detail", "查询模型详情", "model.detail", {"modelId": model_id})
        elif "模型" in task and "运行" not in task and "预测" not in task:
            add("model-list", "查询模型列表", "model.list")

        if "api" in text or "接口" in task:
            if "用量" in task or "调用" in task or "日志" in task:
                add("api-usage", "查询 API 用量", "api.usage")
            elif "创建" in task or "新增" in task:
                add("api-create", "创建 API Key", "api.create", {"name": "Agent 创建的 API Key"})
            elif "重置" in task:
                add("api-reset", "重置 API Key", "api.reset", self._id_argument(task, "apiKeyId"))
            elif "删除" in task:
                add("api-delete", "删除 API Key", "api.delete", self._id_argument(task, "apiKeyId"))
            else:
                add("api-list", "查询 API Key", "api.list")

        if "钱包" in task or "余额" in task or "账单" in task:
            add("wallet-balance", "查询钱包余额", "wallet.balance")

        if "市场" in task or "套餐" in task or "购买" in task:
            if "购买" in task:
                add("marketplace-purchase", "购买套餐", "marketplace.purchase", self._id_argument(task, "planId"))
            else:
                add("marketplace-list", "查询套餐", "marketplace.list")

        if any(word in task for word in ("全部已读", "全部通知已读", "通知全部已读")):
            add("notification-mark-all-read", "全部通知已读", "notification.markAllRead")
        elif any(word in task for word in ("标记通知", "通知已读", "设为已读")):
            notification_id = self._extract_notification_id(task)
            add("notification-mark-read", "标记通知已读", "notification.markRead", {"notificationId": notification_id} if notification_id else {})
        elif "未读通知" in task or "未读消息" in task:
            add("notification-unread", "查询未读通知数", "notification.unreadCount")
        elif "通知" in task:
            add("notification-list", "查询通知列表", "notification.list")
        elif "新闻详情" in task or "公告详情" in task:
            news_id = self._extract_news_id(task)
            add("news-detail", "查询新闻详情", "news.detail", {"newsId": news_id} if news_id else {})
        elif "新闻" in task or "公告" in task:
            add("news-list", "查询新闻通知", "news.list")

        if "管理员" in task or "admin" in text:
            if "api" in text or "接口" in task:
                add("admin-user-api-list", "管理员查询用户 API", "admin.userApi.list")

        return steps

    def _is_station_analysis_task(self, task: str) -> bool:
        lower = task.lower()
        mentions_station = "电站" in task or "站点" in task or "station" in lower or self._extract_station_id(task) is not None
        analysis_words = ("运行", "分析", "功率", "运维", "异常", "故障", "风险", "综合", "发电", "预测结果")
        return mentions_station and any(word in task for word in analysis_words)

    def _should_use_station_analysis_chain(self, task: str) -> bool:
        if not self._is_station_analysis_task(task):
            return False
        direct_lookup_words = ("实时功率", "历史功率", "功率曲线", "天气预报", "预测详情", "报告详情")
        if any(word in task for word in direct_lookup_words):
            return False
        return any(word in task for word in ("运行情况", "综合分析", "分析", "运维", "异常", "故障", "风险", "预测结果"))

    def _tool_title(self, tool_name: str) -> str:
        return self.router.capabilities.get(tool_name).description if tool_name in self.router.capabilities else tool_name

    def _extract_task_id(self, task: str) -> int | None:
        if "任务" not in task and "task" not in task.lower():
            return None
        return self._extract_station_id(task)

    def _extract_report_id(self, task: str) -> int | None:
        if "报告" not in task and "report" not in task.lower():
            return None
        return self._extract_station_id(task)

    def _extract_news_id(self, task: str) -> int | None:
        if "新闻" not in task and "公告" not in task and "news" not in task.lower():
            return None
        return self._extract_station_id(task)

    def _extract_notification_id(self, task: str) -> int | None:
        if "通知" not in task and "notification" not in task.lower():
            return None
        return self._extract_station_id(task)

    def _extract_model_id(self, task: str) -> int | None:
        if "模型" not in task and "model" not in task.lower():
            return None
        return self._extract_station_id(task)

    def _id_argument(self, task: str, key: str) -> dict[str, Any]:
        value = self._extract_station_id(task)
        return {key: value} if value else {}

    def _extract_location(self, task: str) -> str | None:
        for suffix in ("天气", "的天气"):
            if suffix in task:
                value = task.split(suffix)[0].strip(" ，,。查询查看")
                if value and len(value) <= 20:
                    return value
        return None

    def _extract_station_id(self, task: str) -> int | None:
        digits = "".join(ch if ch.isdigit() else " " for ch in task).split()
        return int(digits[0]) if digits else None

    def _conversation_history(self, context: dict[str, Any]) -> list[dict[str, Any]]:
        history = context.get("conversationHistory") or context.get("history") or []
        if not isinstance(history, list):
            return []
        cleaned: list[dict[str, Any]] = []
        for item in history[-12:]:
            if not isinstance(item, dict):
                continue
            role = item.get("role")
            content = item.get("content")
            if not isinstance(role, str) or not isinstance(content, str) or not content.strip():
                continue
            cleaned.append({
                "role": role,
                "content": content.strip()[:1200],
            })
        return cleaned

    def _station_id_from_history(self, history: list[dict[str, Any]]) -> int | None:
        for item in reversed(history):
            content = item.get("content")
            if not isinstance(content, str):
                continue
            station_id = self._extract_station_id(content)
            if station_id is not None and ("电站" in content or "站点" in content or "station" in content.lower()):
                return station_id
        return None

    def _wants_report(self, task: str) -> bool:
        normalized = task.lower()
        return ("报告" in task and ("生成" in task or "创建" in task or "保存" in task)) or "generate report" in normalized

    def _load_memories(self, context: dict[str, Any]) -> list[dict[str, Any]]:
        if not self.memory_gateway or not context.get("userId"):
            return []
        try:
            value = self.memory_gateway.list(context, limit=20)
            return value if isinstance(value, list) else []
        except Exception:
            return []

    def _default_station_id(self, memories: list[dict[str, Any]]) -> int | None:
        for memory in memories:
            if memory.get("memoryType") != "default_station":
                continue
            value = memory.get("value")
            if not isinstance(value, dict):
                continue
            station_id = value.get("stationId")
            try:
                return int(station_id)
            except (TypeError, ValueError):
                continue
        return None

    def _persist_memory_candidates(self, message: str, context: dict[str, Any]) -> None:
        if not self.memory_gateway or not context.get("userId"):
            return
        for candidate in extract_memory_candidates(message):
            try:
                self.memory_gateway.write(context, candidate)
            except Exception:
                continue
