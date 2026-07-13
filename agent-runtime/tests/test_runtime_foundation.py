import unittest
from pathlib import Path

from pv_agent_runtime import PhotovoltaicAgentRuntime, SkillLoader
from pv_agent_runtime.memory import extract_memory_candidates
from pv_agent_runtime.types import ToolResult


ROOT = Path(__file__).resolve().parents[1]


class FakeGateway:
    def __init__(self):
        self.calls = []

    def execute(self, tool_name, arguments, context):
        self.calls.append((tool_name, arguments))
        if tool_name == "station.detail":
            return ToolResult(tool_name, True, "已获取 1 号电站基础信息", ["状态：RUNNING"], {
                "stationName": "1号电站",
                "capacity": 1200,
                "status": "RUNNING",
                "province": "四川",
                "city": "成都",
            })
        if tool_name == "weather.current":
            return ToolResult(tool_name, True, "当前天气多云，可能带来功率波动", ["天气：多云"], {
                "weather": "多云",
                "temperature": 35,
                "humidity": 52,
                "windSpeed": 3.6,
            })
        if tool_name == "prediction.list":
            return ToolResult(tool_name, True, "最近预测任务 0 条", ["最近任务：0 条"], {"total": 0, "records": []})
        if tool_name == "model.list":
            return ToolResult(tool_name, True, "已获取模型列表，共 1 个", ["可用模型：1 个"], [{
                "modelId": 1,
                "modelName": "光伏功率预测模型",
                "modelType": "POWER_PREDICTION",
                "status": "ONLINE",
            }])
        return ToolResult(tool_name, False, "unknown", error="unknown")


class PredictionDetailGateway(FakeGateway):
    def execute(self, tool_name, arguments, context):
        if tool_name == "prediction.list":
            return ToolResult(tool_name, True, "已获取最近预测任务列表", ["任务 42：SUCCESS"], {
                "total": 1,
                "records": [{"taskId": 42, "status": "SUCCESS"}],
            })
        if tool_name == "prediction.detail":
            return ToolResult(tool_name, True, "已读取任务 42 的预测详情，预测功率10-120 kW", [
                "任务状态：SUCCESS",
                "预测点数：2",
            ], {
                "task": {"taskId": arguments["taskId"], "status": "SUCCESS"},
                "results": [{"predictPowerKw": 10}, {"predictPowerKw": 120}],
            })
        return super().execute(tool_name, arguments, context)


class ReportApprovalGateway(FakeGateway):
    def execute(self, tool_name, arguments, context):
        if tool_name == "report.generate":
            self.calls.append((tool_name, arguments))
            return ToolResult(tool_name, False, "工具需要用户确认后才能执行", ["APPROVAL_REQUIRED"], {
                "approvalRequired": True,
                "approvalId": 9,
                "clientToolCallId": "tc_report",
                "toolName": "report.generate",
                "reason": "工具 生成综合分析报告 会执行写操作，需要用户确认后才能继续。",
                "arguments": arguments,
            }, error="APPROVAL_REQUIRED")
        return super().execute(tool_name, arguments, context)


class FakeLlmGateway:
    def chat_completions(self, messages, **options):
        return {
            "choices": [{
                "message": {
                    "content": "运行结论：1号电站运行正常。\n风险：天气可能造成波动。\n建议：持续关注预测偏差。"
                }
            }]
        }


class FailingLlmGateway:
    def chat_completions(self, messages, **options):
        raise RuntimeError("llm timeout")


class FakeMemoryGateway:
    def __init__(self, memories=None):
        self.memories = memories or []
        self.writes = []

    def list(self, context, memory_type=None, limit=20):
        return self.memories

    def write(self, context, candidate, memory_key=None):
        self.writes.append((candidate, memory_key))
        return {"memoryType": candidate.memory_type, "value": candidate.value}


class RuntimeFoundationTest(unittest.TestCase):
    def test_skill_loader(self):
        skills = SkillLoader(ROOT / "skills").load_all()
        self.assertIn("station-inspection", skills)
        self.assertIn("weather.current", skills["station-inspection"].allowed_tools)

    def test_first_vertical_chain_dry_run(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills")
        state = runtime.run("分析 1 号电站当前运行情况，结合天气和最近预测结果。", {"sessionId": 1})
        self.assertEqual(state.selected_skill, "station-inspection")
        self.assertEqual([r.tool_name for r in state.tool_results], ["station.detail", "weather.current", "prediction.list"])
        self.assertEqual([ui.component for ui in state.ui], ["StationSummaryCard", "WeatherImpactCard", "PredictionTrendCard"])
        self.assertIn("当前天气多云", state.final_answer)

    def test_prediction_detail_follow_up_when_list_has_records(self):
        runtime = PhotovoltaicAgentRuntime(PredictionDetailGateway(), ROOT / "skills")
        state = runtime.run("分析 1 号电站当前运行情况，结合天气和最近预测结果。", {"sessionId": 1})
        self.assertEqual([r.tool_name for r in state.tool_results], [
            "station.detail",
            "weather.current",
            "prediction.list",
            "prediction.detail",
        ])
        self.assertEqual(state.tool_results[-1].data["task"]["taskId"], 42)
        self.assertIn("预测功率10-120 kW", state.final_answer)

    def test_stream_events_include_prediction_detail_follow_up(self):
        runtime = PhotovoltaicAgentRuntime(PredictionDetailGateway(), ROOT / "skills")
        events = list(runtime.iter_events("分析 1 号电站当前运行情况，结合天气和最近预测结果。", {"sessionId": 1}))
        tool_results = [event["data"]["tool_name"] for event in events if event["event"] == "tool_result"]
        step_ids = [event["data"]["stepId"] for event in events if event["event"] == "step_completed"]
        self.assertIn("prediction.detail", tool_results)
        self.assertLess(step_ids.index("prediction-list"), step_ids.index("prediction-detail"))

    def test_llm_synthesis_uses_gateway(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills", llm_gateway=FakeLlmGateway())
        state = runtime.run("分析 1 号电站当前运行情况，结合天气和最近预测结果。", {"sessionId": 1})
        self.assertIn("运行结论：1号电站运行正常", state.final_answer)

    def test_llm_synthesis_degrades_when_gateway_fails(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills", llm_gateway=FailingLlmGateway())
        state = runtime.run("分析 1 号电站当前运行情况，结合天气和最近预测结果。", {"sessionId": 1})
        self.assertIn("LLM 不可用，已降级为工具结果摘要", state.final_answer)
        self.assertIn("llm timeout", state.final_answer)

    def test_default_station_can_come_from_memory(self):
        gateway = FakeGateway()
        memory = FakeMemoryGateway([{
            "memoryType": "default_station",
            "memoryKey": "default",
            "value": {"stationId": 2},
        }])
        runtime = PhotovoltaicAgentRuntime(gateway, ROOT / "skills", memory_gateway=memory)
        runtime.run("分析默认电站当前运行情况", {"sessionId": 1, "userId": 7})
        station_call = next(call for call in gateway.calls if call[0] == "station.detail")
        self.assertEqual(station_call[1]["stationId"], 2)

    def test_memory_candidates_are_persisted_after_run(self):
        memory = FakeMemoryGateway()
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills", memory_gateway=memory)
        runtime.run("默认 2 号电站", {"sessionId": 1, "userId": 7})
        self.assertEqual(memory.writes[0][0].memory_type, "default_station")
        self.assertEqual(memory.writes[0][0].value["stationId"], 2)

    def test_report_generation_requires_approval(self):
        runtime = PhotovoltaicAgentRuntime(ReportApprovalGateway(), ROOT / "skills")
        state = runtime.run("分析 1 号电站并生成报告", {"sessionId": 1, "userId": 7})
        self.assertIn("report.generate", [result.tool_name for result in state.tool_results])
        self.assertEqual(state.ui[-1].component, "ApprovalActionCard")
        self.assertEqual(state.ui[-1].props["approvalId"], 9)
        self.assertIn("需要用户确认", state.final_answer)

    def test_stream_events_emit_approval_required_for_report_generation(self):
        runtime = PhotovoltaicAgentRuntime(ReportApprovalGateway(), ROOT / "skills")
        events = list(runtime.iter_events("分析 1 号电站并生成报告", {"sessionId": 1, "userId": 7}))
        names = [event["event"] for event in events]
        self.assertIn("approval_required", names)
        self.assertNotIn("run_completed", names)
        approval = next(event["data"] for event in events if event["event"] == "approval_required")
        self.assertEqual(approval["toolName"], "report.generate")
        self.assertEqual(approval["approvalId"], 9)


    def test_manager_routes_project_tools(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills")
        cases = [
            ("查看我的 API Key", "api.list"),
            ("查看 API 调用日志", "api.usage"),
            ("查看钱包余额", "wallet.balance"),
            ("查看模型列表", "model.list"),
            ("查看我的个人信息", "user.profile"),
            ("运行云图预测", "cloud.predict"),
            ("查看仪表盘概览", "dashboard.overview"),
            ("查询 1 号电站实时功率", "pv.realtime"),
            ("查询 1 号电站历史功率", "pv.history"),
            ("查询 1 号电站天气预报", "weather.forecast"),
            ("查看未读通知", "notification.unreadCount"),
            ("查看通知列表", "notification.list"),
            ("查看新闻公告", "news.list"),
            ("查看套餐列表", "marketplace.list"),
            ("查看历史报告", "report.list"),
            ("查看我的电站", "station.list"),
        ]
        for message, tool_name in cases:
            with self.subTest(message=message):
                state = runtime.plan(message, {"sessionId": 1, "userId": 7})
                self.assertIn(tool_name, [step.tool_name for step in state.plan])

    def test_explicit_tool_from_request_context(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills")
        state = runtime.plan("按指定工具执行", {
            "sessionId": 1,
            "userId": 7,
            "preferredTool": "model.detail",
            "toolArguments": {"modelId": 3},
        })
        tool_steps = [step for step in state.plan if step.tool_name]
        self.assertEqual(tool_steps[0].tool_name, "model.detail")
        self.assertEqual(tool_steps[0].arguments["modelId"], 3)

    def test_write_project_tools_require_approval_event(self):
        runtime = PhotovoltaicAgentRuntime(ReportApprovalGateway(), ROOT / "skills")
        state = runtime.plan("创建 API Key", {"sessionId": 1, "userId": 7})
        self.assertIn("api.create", [step.tool_name for step in state.plan])
        state = runtime.plan("修改昵称", {"sessionId": 1, "userId": 7, "nickname": "新昵称"})
        self.assertIn("user.profile.update", [step.tool_name for step in state.plan])
        state = runtime.plan("全部通知已读", {"sessionId": 1, "userId": 7})
        self.assertIn("notification.markAllRead", [step.tool_name for step in state.plan])

    def test_non_station_question_does_not_default_to_station_chain(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills")
        state = runtime.plan("你能做什么", {"sessionId": 1, "userId": 7})
        self.assertEqual([step.tool_name for step in state.plan if step.tool_name], [])

    def test_model_list_accepts_list_payload(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills")
        state = runtime.run("查看模型列表", {"sessionId": 1, "userId": 7})
        self.assertEqual(state.tool_results[0].tool_name, "model.list")
        self.assertIsInstance(state.tool_results[0].data, list)
        self.assertIn("已获取模型列表", state.final_answer)

    def test_general_question_gets_capability_answer_without_tools(self):
        runtime = PhotovoltaicAgentRuntime(FakeGateway(), ROOT / "skills")
        state = runtime.run("你支持哪些功能", {"sessionId": 1, "userId": 7})
        self.assertEqual(state.tool_results, [])
        self.assertIn("项目管家", state.final_answer)

    def test_memory_filters_sensitive_values(self):
        self.assertEqual(extract_memory_candidates("我的 api_key 是 secret，默认 1 号电站"), [])
        safe = extract_memory_candidates("默认 1 号电站")
        self.assertEqual(safe[0].memory_type, "default_station")


if __name__ == "__main__":
    unittest.main()
