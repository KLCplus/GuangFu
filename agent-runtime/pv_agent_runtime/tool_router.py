from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from .types import PlanStep, ToolResult


class ToolGateway(Protocol):
    def execute(self, tool_name: str, arguments: dict[str, Any], context: dict[str, Any]) -> ToolResult:
        ...


@dataclass(frozen=True)
class ToolCapability:
    name: str
    category: str
    description: str
    requires_approval: bool = False


class ToolRouter:
    """Routes planned tool calls through a Spring Agent Gateway style adapter."""

    def __init__(self, gateway: ToolGateway, capabilities: list[ToolCapability]):
        self.gateway = gateway
        self.capabilities = {item.name: item for item in capabilities}

    def execute_step(self, step: PlanStep, context: dict[str, Any]) -> ToolResult | None:
        if not step.tool_name:
            return None
        if step.tool_name not in self.capabilities:
            return ToolResult(step.tool_name, False, f"Tool not available: {step.tool_name}", error="TOOL_NOT_AVAILABLE")
        try:
            return self.gateway.execute(step.tool_name, step.arguments, context)
        except Exception as exc:
            return ToolResult(step.tool_name, False, f"Tool failed: {exc}", error=str(exc))


DEFAULT_CAPABILITIES = [
    ToolCapability("station.detail", "station", "Fetch station detail"),
    ToolCapability("weather.current", "weather", "Fetch current station weather"),
    ToolCapability("prediction.list", "prediction", "List prediction tasks"),
    ToolCapability("prediction.detail", "prediction", "Fetch prediction detail"),
    ToolCapability("report.generate", "report", "Generate report", requires_approval=True),
]

