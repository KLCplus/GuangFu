from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import argparse
import json
import os
from pathlib import Path

from pv_agent_runtime.gateway import SpringToolGateway
from pv_agent_runtime.runtime import PhotovoltaicAgentRuntime


class RuntimeHandler(BaseHTTPRequestHandler):
    runtime: PhotovoltaicAgentRuntime

    def do_GET(self):
        if self.path != "/health":
            self.send_error(404)
            return
        self._json({"status": "ok"})

    def do_POST(self):
        if self.path != "/run/stream":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            task = payload.get("message") or payload.get("task") or ""
            context = payload.get("context") or {}
            if payload.get("sessionId") is not None:
                context.setdefault("sessionId", payload.get("sessionId"))
            for key in ("userId", "username", "roles", "approved"):
                if payload.get(key) is not None:
                    context.setdefault(key, payload.get(key))
        except Exception as exc:
            self.send_error(400, f"Invalid request: {exc}")
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        for event in self.runtime.iter_events(task, context):
            data = json.dumps(event, ensure_ascii=False, default=str)
            self.wfile.write(f"event:{event['event']}\n".encode("utf-8"))
            self.wfile.write(f"data:{data}\n\n".encode("utf-8"))
            self.wfile.flush()

    def log_message(self, fmt, *args):
        return

    def _json(self, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.getenv("AGENT_RUNTIME_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("AGENT_RUNTIME_PORT", "9101")))
    parser.add_argument("--spring-base-url", default=os.getenv("SPRING_AGENT_GATEWAY_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--internal-token", default=os.getenv("AGENT_INTERNAL_TOKEN", ""))
    parser.add_argument("--skills-dir", default=str(Path(__file__).parent / "skills"))
    args = parser.parse_args()
    if not args.internal_token:
        raise SystemExit("AGENT_INTERNAL_TOKEN is required")

    gateway = SpringToolGateway(args.spring_base_url, args.internal_token, None)
    RuntimeHandler.runtime = PhotovoltaicAgentRuntime(gateway, args.skills_dir)
    server = ThreadingHTTPServer((args.host, args.port), RuntimeHandler)
    print(f"agent-runtime listening on http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

