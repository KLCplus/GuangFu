from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal


StageName = Literal[
    "Understand",
    "Collect",
    "Diagnose",
    "Evaluate",
    "Recommend",
    "Synthesize",
]


@dataclass(frozen=True)
class PlanStep:
    step_id: str
    stage: StageName
    title: str
    purpose: str
    tool_name: str | None = None
    arguments: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ToolResult:
    tool_name: str
    success: bool
    summary: str
    highlights: list[str] = field(default_factory=list)
    data: Any = field(default_factory=dict)
    error: str | None = None


@dataclass(frozen=True)
class UIInstruction:
    component: str
    props: dict[str, Any]


@dataclass
class AgentState:
    session_id: int | None
    user_task: str
    selected_skill: str | None = None
    memories: list[dict[str, Any]] = field(default_factory=list)
    plan: list[PlanStep] = field(default_factory=list)
    tool_results: list[ToolResult] = field(default_factory=list)
    ui: list[UIInstruction] = field(default_factory=list)
    final_answer: str = ""
