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
    ToolCapability("station.list", "station", "List stations"),
    ToolCapability("station.detail", "station", "Fetch station detail"),
    ToolCapability("station.create", "station", "Create station", requires_approval=True),
    ToolCapability("station.update", "station", "Update station", requires_approval=True),
    ToolCapability("station.disable", "station", "Disable station", requires_approval=True),
    ToolCapability("station.delete", "station", "Delete station", requires_approval=True),
    ToolCapability("pvoutput.station.list", "station", "List public PVOutput stations"),
    ToolCapability("pvoutput.station.detail", "station", "Fetch public PVOutput station detail"),
    ToolCapability("pvoutput.status.latest", "pv", "Fetch latest public PVOutput station status"),
    ToolCapability("pvoutput.status.history", "pv", "Fetch public PVOutput station status history"),
    ToolCapability("pvoutput.weather.current", "weather", "Fetch public PVOutput station weather"),
    ToolCapability("pvoutput.weather.forecast", "weather", "Fetch public PVOutput station weather forecast"),
    ToolCapability("weather.current", "weather", "Fetch current station weather"),
    ToolCapability("weather.location", "weather", "Fetch weather by location"),
    ToolCapability("weather.forecast", "weather", "Fetch station weather forecast"),
    ToolCapability("weather.locationForecast", "weather", "Fetch location weather forecast"),
    ToolCapability("dashboard.overview", "dashboard", "Show dashboard overview"),
    ToolCapability("pv.realtime", "pv", "Fetch realtime PV data"),
    ToolCapability("pv.history", "pv", "Fetch historical PV data"),
    ToolCapability("prediction.list", "prediction", "List prediction tasks"),
    ToolCapability("prediction.detail", "prediction", "Fetch prediction detail"),
    ToolCapability("report.list", "report", "List reports"),
    ToolCapability("report.detail", "report", "Fetch report detail"),
    ToolCapability("report.generate", "report", "Generate report", requires_approval=True),
    ToolCapability("report.conversation", "report", "Generate conversation report"),
    ToolCapability("model.list", "model", "List models"),
    ToolCapability("model.detail", "model", "Fetch model detail"),
    ToolCapability("model.run", "model", "Run prediction model", requires_approval=True),
    ToolCapability("cloud.predict", "cloud", "Run cloud image prediction", requires_approval=True),
    ToolCapability("api.list", "api", "List API keys"),
    ToolCapability("api.usage", "api", "Show API usage"),
    ToolCapability("api.create", "api", "Create API key", requires_approval=True),
    ToolCapability("api.reset", "api", "Reset API key", requires_approval=True),
    ToolCapability("api.delete", "api", "Delete API key", requires_approval=True),
    ToolCapability("user.profile", "user", "Show current user profile"),
    ToolCapability("user.profile.update", "user", "Update current user profile", requires_approval=True),
    ToolCapability("wallet.balance", "wallet", "Show wallet balance"),
    ToolCapability("marketplace.list", "marketplace", "List marketplace plans"),
    ToolCapability("marketplace.purchase", "marketplace", "Purchase marketplace plan", requires_approval=True),
    ToolCapability("news.list", "news", "List news and notifications"),
    ToolCapability("news.detail", "news", "Fetch news detail"),
    ToolCapability("notification.list", "notification", "List user notifications"),
    ToolCapability("notification.unreadCount", "notification", "Show unread notification count"),
    ToolCapability("notification.markRead", "notification", "Mark notification read", requires_approval=True),
    ToolCapability("notification.markAllRead", "notification", "Mark all notifications read", requires_approval=True),
    ToolCapability("admin.userApi.list", "admin", "Admin list user API keys", requires_approval=True),
    ToolCapability("admin.station.manage", "admin", "Disabled broad station admin tool", requires_approval=True),
    ToolCapability("admin.model.manage", "admin", "Disabled broad model admin tool", requires_approval=True),
]
