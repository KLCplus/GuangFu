from __future__ import annotations

from dataclasses import asdict
from typing import Any

from .types import UIInstruction


ALLOWED_COMPONENTS = {
    "StationSummaryCard",
    "WeatherImpactCard",
    "PredictionTrendCard",
    "PowerMetricCard",
    "RiskAssessmentCard",
    "MaintenanceRecommendationCard",
    "ReportPreviewCard",
    "ApprovalActionCard",
    "ToolProgressCard",
    "ErrorRecoveryCard",
}


def ui_instruction(component: str, props: dict[str, Any]) -> UIInstruction:
    if component not in ALLOWED_COMPONENTS:
        raise ValueError(f"UI component is not allowed: {component}")
    if not isinstance(props, dict):
        raise ValueError("UI props must be an object")
    return UIInstruction(component=component, props=props)


def stream_event(event: str, data: Any) -> dict[str, Any]:
    if hasattr(data, "__dataclass_fields__"):
        data = asdict(data)
    return {"event": event, "data": data}

