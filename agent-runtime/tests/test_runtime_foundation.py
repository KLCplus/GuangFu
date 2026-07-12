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

    def test_memory_filters_sensitive_values(self):
        self.assertEqual(extract_memory_candidates("我的 api_key 是 secret，默认 1 号电站"), [])
        safe = extract_memory_candidates("默认 1 号电站")
        self.assertEqual(safe[0].memory_type, "default_station")


if __name__ == "__main__":
    unittest.main()

