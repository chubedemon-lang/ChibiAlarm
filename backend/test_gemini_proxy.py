"""Offline Gemini proxy regression tests; no API keys or network required."""
import concurrent.futures
import http.client
import json
import os
import tempfile
import threading
import unittest
from unittest.mock import patch

import gemini_proxy as proxy


class FakeResponse:
    def __init__(self, data):
        self.data = data

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self, limit):
        return self.data[:limit]


class ProxyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.token = "unit-test-secret-token-long-enough"
        self.patchers = [
            patch.object(proxy, "TOKEN", self.token),
            patch.object(proxy, "KEYS", {1: "fake-google-key", 2: "", 3: "", 4: "", 5: ""}),
            patch.object(proxy, "QUOTA_DB", os.path.join(self.tmp.name, "quota.sqlite3")),
            patch.object(proxy, "DAILY_LIMIT", 2),
            patch.object(proxy, "SLOTS", threading.BoundedSemaphore(2)),
        ]
        for p in self.patchers:
            p.start()
            self.addCleanup(p.stop)
        self.server = proxy.ThreadingHTTPServer(("127.0.0.1", 0), proxy.Handler)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def request(self, body=None, token=True, path="/generate", content_type="application/json"):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        headers = {"Content-Type": content_type}
        if token:
            headers["Authorization"] = "Bearer " + self.token
        conn.request("POST", path, body=json.dumps(body if body is not None else {"topic": "morning"}), headers=headers)
        response = conn.getresponse()
        result = (response.status, json.loads(response.read()))
        conn.close()
        return result

    def test_missing_auth_rejected_before_upstream(self):
        with patch.object(proxy.urllib.request, "urlopen") as upstream:
            self.assertEqual(self.request(token=False)[0], 401)
            upstream.assert_not_called()

    def test_fail_closed_if_server_has_no_token(self):
        with patch.object(proxy, "TOKEN", ""):
            self.assertEqual(self.request()[0], 503)
            with self.assertRaises(ValueError):
                proxy.validate_config()

    def test_missing_profile_rejected(self):
        self.assertEqual(self.request({"topic": "hello", "profile": 2})[0], 400)

    def test_malformed_inputs_rejected(self):
        self.assertEqual(self.request(["not", "object"])[0], 400)
        self.assertEqual(self.request({"topic": ["not string"]})[0], 400)
        self.assertEqual(self.request({"topic": "a" * 401})[0], 400)
        self.assertEqual(self.request({"topic": "ok", "profile": True})[0], 400)
        self.assertEqual(self.request(content_type="text/plain")[0], 415)

    def test_success_response_is_bounded_and_no_secret(self):
        fake = FakeResponse(json.dumps({"candidates": [{"content": {"parts": [{"text": "Xin chào " * 100}]}}]}).encode())
        with patch.object(proxy.urllib.request, "urlopen", return_value=fake) as upstream:
            status, data = self.request({"topic": "dậy sớm", "profile": 1})
        self.assertEqual(status, 200)
        self.assertLessEqual(len(data["message"]), 500)
        self.assertNotIn("fake-google-key", json.dumps(data))
        self.assertEqual(upstream.call_args.args[0].headers["X-goog-api-key"], "fake-google-key")

    def test_durable_global_quota_across_requests(self):
        fake = FakeResponse(b'{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}')
        with patch.object(proxy.urllib.request, "urlopen", return_value=fake) as upstream:
            self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 429)
            self.assertEqual(upstream.call_count, 2)

    def test_upstream_failure_consumes_attempt(self):
        with patch.object(proxy.urllib.request, "urlopen", side_effect=OSError("offline")):
            self.assertEqual(self.request()[0], 502)
            self.assertEqual(self.request()[0], 502)
            self.assertEqual(self.request()[0], 429)

    def test_bad_model_rejected_at_startup(self):
        with patch.object(proxy, "MODEL", "../other-model"):
            with self.assertRaises(ValueError):
                proxy.validate_config()

    def test_concurrent_sqlite_quota_never_overbooks(self):
        db = os.path.join(self.tmp.name, "concurrent.sqlite3")
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            values = list(executor.map(lambda _: proxy.reserve_daily_attempt(db, "2026-11-01", 3), range(8)))
        self.assertEqual(sum(values), 3)


if __name__ == "__main__":
    unittest.main()
