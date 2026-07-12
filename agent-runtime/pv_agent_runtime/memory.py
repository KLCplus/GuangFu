from __future__ import annotations

from dataclasses import dataclass
from typing import Any
import json
import urllib.request


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


class SpringMemoryGateway:
    def __init__(self, base_url: str, internal_token: str):
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token

    def list(self, context: dict[str, Any], memory_type: str | None = None, limit: int = 20) -> list[dict[str, Any]]:
        payload = {
            "userId": context.get("userId"),
            "username": context.get("username"),
            "roles": context.get("roles", ["USER"]),
            "memoryType": memory_type,
            "enabledOnly": True,
            "limit": limit,
        }
        return self._post("/api/internal/agent/memory/list", payload)

    def write(self, context: dict[str, Any], candidate: MemoryCandidate, memory_key: str | None = None) -> dict[str, Any]:
        payload = {
            "userId": context.get("userId"),
            "username": context.get("username"),
            "roles": context.get("roles", ["USER"]),
            "memoryType": candidate.memory_type,
            "memoryKey": memory_key or self._memory_key(candidate),
            "value": candidate.value,
            "sourceMessage": candidate.source_message,
            "confidence": candidate.confidence,
            "enabled": True,
        }
        return self._post("/api/internal/agent/memory/write", payload)

    def _post(self, path: str, payload: dict[str, Any]) -> Any:
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            headers={
                "Content-Type": "application/json",
                "X-Agent-Internal-Token": self.internal_token,
            },
            method="POST",
        )
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))

    def _memory_key(self, candidate: MemoryCandidate) -> str:
        if candidate.memory_type == "default_station":
            return "default"
        if candidate.memory_type == "report_format":
            return "default"
        return "general"
