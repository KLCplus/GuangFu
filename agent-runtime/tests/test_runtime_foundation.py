import unittest
from pathlib import Path

from pv_agent_runtime import PhotovoltaicAgentRuntime, SkillLoader
from pv_agent_runtime.memory import extract_memory_candidates
from pv_agent_runtime.types import ToolResult


ROOT = Path(__file__).resolve().parents[1]


class FakeGateway:
    def execute(self, tool_name, arguments, context):
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

    def test_memory_filters_sensitive_values(self):
        self.assertEqual(extract_memory_candidates("我的 api_key 是 secret，默认 1 号电站"), [])
        safe = extract_memory_candidates("默认 1 号电站")
        self.assertEqual(safe[0].memory_type, "default_station")


if __name__ == "__main__":
    unittest.main()
