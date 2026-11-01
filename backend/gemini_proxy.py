"""Development-only Gemini alarm-text proxy with fail-closed auth and durable quotas.

A shared bearer token is NOT an account system: never embed it in a public APK.
Public release requires verified per-user authentication and server-side spend limits.
Python 3.10+, standard library only.
"""

import hmac
import json
import os
import re
import sqlite3
import threading
import urllib.error
import urllib.request
from contextlib import closing
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

KEYS = {i: os.environ.get(f"GEMINI_API_KEY_{i}", "") for i in range(1, 6)}
if not any(KEYS.values()) and os.environ.get("GEMINI_API_KEY"):
    KEYS[1] = os.environ["GEMINI_API_KEY"]
TOKEN = os.environ.get("APP_ACCESS_TOKEN", "")
MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
ALLOWED_MODELS = frozenset(("gemini-2.5-flash", "gemini-2.5-pro"))
DAILY_LIMIT = int(os.environ.get("GEMINI_DAILY_REQUEST_LIMIT", "50"))
MAX_CONCURRENT = int(os.environ.get("GEMINI_MAX_CONCURRENT", "2"))
QUOTA_DB = os.environ.get("GEMINI_QUOTA_DB", str(Path(__file__).with_name("gemini_quota.sqlite3")))
SLOTS = threading.BoundedSemaphore(MAX_CONCURRENT if MAX_CONCURRENT > 0 else 1)


def validate_config():
    """Fail at startup instead of accidentally serving unauthenticated requests."""
    if len(TOKEN) < 24:
        raise ValueError("APP_ACCESS_TOKEN must be a strong, private token (24+ chars)")
    if not any(KEYS.values()):
        raise ValueError("At least one GEMINI_API_KEY(_1..5) must be configured")
    if MODEL not in ALLOWED_MODELS or not re.fullmatch(r"[a-zA-Z0-9._-]+", MODEL):
        raise ValueError("GEMINI_MODEL is not allowlisted")
    if not (1 <= DAILY_LIMIT <= 10000) or not (1 <= MAX_CONCURRENT <= 16):
        raise ValueError("Invalid quota/concurrency limits")


def reserve_daily_attempt(path, day, limit):
    """Atomically reserve a global upstream attempt; persists across server restarts.

    Failed upstream calls also count; this is NOT a monetary spend cap.
    """
    # sqlite3's connection context manager commits but does NOT close connections.
    # Explicitly close per request to avoid leaking file handles under load.
    with closing(sqlite3.connect(path, timeout=5)) as conn:
        with conn:
            conn.execute("CREATE TABLE IF NOT EXISTS quota (day TEXT PRIMARY KEY, attempts INTEGER NOT NULL)")
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute("SELECT attempts FROM quota WHERE day=?", (day,)).fetchone()
            if row and row[0] >= limit:
                return False
            conn.execute("INSERT INTO quota(day, attempts) VALUES(?, 1) "
                         "ON CONFLICT(day) DO UPDATE SET attempts=attempts+1", (day,))
            conn.execute("DELETE FROM quota WHERE day < ?", (day[:4] + "-01-01",))
            return True


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Never log user-supplied topics, tokens or URL query strings.
        pass

    def reply(self, status, payload, extra_headers=None):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for name, value in (extra_headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/healthz":
            self.reply(200, {"status": "ok"})
        else:
            self.reply(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/generate":
            self.reply(404, {"error": "not_found"})
            return
        if len(TOKEN) < 24 or not any(KEYS.values()):
            self.reply(503, {"error": "not_configured"})
            return
        auth = self.headers.get("Authorization", "")
        if not hmac.compare_digest(auth, "Bearer " + TOKEN):
            self.reply(401, {"error": "unauthorized"})
            return
        if self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower() != "application/json":
            self.reply(415, {"error": "unsupported_media_type"})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 2048:
                self.reply(413, {"error": "invalid_size"})
                return
            payload = json.loads(self.rfile.read(size))
            if not isinstance(payload, dict):
                raise ValueError("expected object")
            topic = payload.get("topic", "")
            profile = payload.get("profile", 1)
            if not isinstance(topic, str) or len(topic) > 400 or not isinstance(profile, int) or isinstance(profile, bool):
                raise ValueError("invalid fields")
            if profile not in KEYS or not KEYS[profile]:
                self.reply(400, {"error": "profile_unavailable"})
                return
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
            self.reply(400, {"error": "invalid_request"})
            return
        if not SLOTS.acquire(blocking=False):
            self.reply(429, {"error": "server_busy"}, {"Retry-After": "5"})
            return
        try:
            day = datetime.now(timezone.utc).date().isoformat()
            try:
                allowed = reserve_daily_attempt(QUOTA_DB, day, DAILY_LIMIT)
            except (sqlite3.Error, OSError):
                self.reply(503, {"error": "quota_storage_unavailable"})
                return
            if not allowed:
                self.reply(429, {"error": "daily_limit_reached"}, {"Retry-After": "3600"})
                return
            prompt = ("Viết lời đánh thức tiếng Việt độc đáo, đáng yêu, vui nhộn, dài 1-3 câu, "
                      "không markdown, không thông tin bịa về người dùng. Chủ đề: " + topic)
            body = json.dumps({"contents": [{"parts": [{"text": prompt}]}],
                               "generationConfig": {"maxOutputTokens": 160}}).encode("utf-8")
            url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent"
            req = urllib.request.Request(url, data=body, headers={
                "x-goog-api-key": KEYS[profile], "Content-Type": "application/json"}, method="POST")
            try:
                with urllib.request.urlopen(req, timeout=25) as resp:
                    raw = resp.read(16385)
                if len(raw) > 16384:
                    raise ValueError("upstream response too large")
                result = json.loads(raw)
                parts = result["candidates"][0]["content"]["parts"]
                message = "".join(p.get("text", "") for p in parts if isinstance(p, dict)).strip()[:500]
                if not message:
                    raise ValueError("empty Gemini response")
                self.reply(200, {"message": message})
            except urllib.error.HTTPError as exc:
                self.reply(429 if exc.code == 429 else 502, {"error": "upstream_unavailable"})
            except (OSError, ValueError, KeyError, IndexError, TypeError):
                self.reply(502, {"error": "upstream_unavailable"})
        finally:
            SLOTS.release()


def serve():
    validate_config()
    ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()


if __name__ == "__main__":
    serve()
