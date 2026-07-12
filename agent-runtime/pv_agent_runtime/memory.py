from __future__ import annotations

from dataclasses import dataclass
from typing import Any


SENSITIVE_KEYS = {"api_key", "apikey", "token", "password", "secret", "private_key", "credential"}
ALLOWED_MEMORY_TYPES = {
    "user_preference",
    "default_station",
    "report_format",
    "frequent_metric",
    "recent_context",
    "station_focus",
    "ops_preference",
}


@dataclass(frozen=True)
class MemoryCandidate:
    memory_type: str
    value: dict[str, Any]
    source_message: str
    confidence: float


def extract_memory_candidates(message: str) -> list[MemoryCandidate]:
    candidates: list[MemoryCandidate] = []
    if "默认" in message and "电站" in message:
        digits = "".join(ch for ch in message if ch.isdigit())
        if digits:
            candidates.append(MemoryCandidate("default_station", {"stationId": int(digits)}, message, 0.7))
    if "报告" in message and ("格式" in message or "模板" in message):
        candidates.append(MemoryCandidate("report_format", {"preference": message[:120]}, message, 0.6))
    return [item for item in candidates if is_memory_safe(item)]


def is_memory_safe(candidate: MemoryCandidate) -> bool:
    if candidate.memory_type not in ALLOWED_MEMORY_TYPES:
        return False
    blob = f"{candidate.value} {candidate.source_message}".lower()
    return not any(key in blob for key in SENSITIVE_KEYS)

