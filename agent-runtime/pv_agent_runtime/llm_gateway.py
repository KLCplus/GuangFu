from __future__ import annotations

from typing import Any
import json
import urllib.request


class SpringLlmGateway:
    def __init__(self, base_url: str, internal_token: str):
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token

    def chat_completions(self, messages: list[dict[str, Any]], **options: Any) -> dict[str, Any]:
        payload = json.dumps({"messages": messages, **options}).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}/api/internal/llm/v1/chat/completions",
            data=payload,
            headers={
                "Content-Type": "application/json",
                "X-Agent-Internal-Token": self.internal_token,
            },
            method="POST",
        )
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))

