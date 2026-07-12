from __future__ import annotations

from typing import Any
import json
import urllib.request

from .types import ToolResult


class SpringToolGateway:
    def __init__(self, base_url: str, internal_token: str, session_id: int | None):
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token
        self.session_id = session_id

    def execute(self, tool_name: str, arguments: dict[str, Any], context: dict[str, Any]) -> ToolResult:
        payload = json.dumps({
            "sessionId": self.session_id,
            "userId": context.get("userId"),
            "username": context.get("username"),
            "roles": context.get("roles", ["USER"]),
            "arguments": arguments,
            "context": context,
            "approved": context.get("approved", False),
        }).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}/api/internal/agent/tools/{tool_name}/execute",
            data=payload,
            headers={
                "Content-Type": "application/json",
                "X-Agent-Internal-Token": self.internal_token,
            },
            method="POST",
        )
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(request, timeout=30) as response:
            body = json.loads(response.read().decode("utf-8"))
        return ToolResult(
            tool_name=tool_name,
            success=bool(body.get("success")),
            summary=str(body.get("summary") or ""),
            highlights=list(body.get("highlights") or []),
            data=dict(body.get("data") or {}),
            error=body.get("error"),
        )
