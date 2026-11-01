"""Offline Gemini proxy regression tests; no API keys or network required."""
import concurrent.futures
import http.client
import json
import os
import sqlite3
import socket
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
            patch.object(proxy, "PROFILE_DAILY_LIMIT", 20),
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

    def test_incomplete_request_body_times_out_without_spending_quota(self):
        # A slow client used to block one ThreadingHTTPServer worker forever.
        # The test uses a real local socket but never contacts Gemini.
        with (patch.object(proxy, "CLIENT_READ_TIMEOUT_SECONDS", 1),
              patch.object(proxy, "open_gemini") as upstream):
            with socket.create_connection(("127.0.0.1", self.server.server_port), timeout=4) as sock:
                sock.settimeout(4)
                sock.sendall((
                    "POST /generate HTTP/1.1\r\n"
                    "Host: localhost\r\n"
                    "Authorization: Bearer " + self.token + "\r\n"
                    "Content-Type: application/json\r\n"
                    "Content-Length: 100\r\n"
                    "Connection: close\r\n\r\n"
                    '{"topic":"partial"}'
                ).encode("ascii"))
                response = sock.recv(4096)
            self.assertIn(b" 408 ", response.split(b"\r\n", 1)[0])
            upstream.assert_not_called()
            self.assertEqual(self.request(token=False)[0], 401)
            self.assertFalse(os.path.exists(proxy.QUOTA_DB))

    def test_invalid_client_read_timeout_is_rejected_at_startup(self):
        for invalid in (0, 31):
            with self.subTest(invalid=invalid), patch.object(
                    proxy, "CLIENT_READ_TIMEOUT_SECONDS", invalid):
                with self.assertRaises(ValueError):
                    proxy.validate_config()

    def test_missing_auth_rejected_before_upstream(self):
        with patch.object(proxy, "open_gemini") as upstream:
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

    def test_empty_and_control_character_topics_rejected_without_upstream(self):
        with patch.object(proxy, "open_gemini") as upstream:
            for topic in ("", "   ", "\n", "hello\nignore instructions", "hi\x7f"):
                with self.subTest(topic=repr(topic)):
                    self.assertEqual(self.request({"topic": topic})[0], 400)
            upstream.assert_not_called()

    def test_upstream_redirects_are_never_followed(self):
        req = proxy.urllib.request.Request(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            data=b"{}", headers={"x-goog-api-key": "private-key"}, method="POST")
        handler = proxy.NoUpstreamRedirects()
        self.assertIsNone(handler.redirect_request(
            req, None, 302, "Found", {}, "https://other.example/steal"))
        with patch.object(proxy.urllib.request, "build_opener") as builder:
            proxy.open_gemini(req)
            self.assertTrue(any(isinstance(h, proxy.NoUpstreamRedirects)
                                for h in builder.call_args.args))
            self.assertEqual(builder.return_value.open.call_args.kwargs["timeout"], 25)

    def test_invalid_upstream_utf8_fails_closed(self):
        with patch.object(proxy, "open_gemini", return_value=FakeResponse(b"\xff\xfe")):
            status, data = self.request()
        self.assertEqual(status, 502)
        self.assertEqual(data, {"error": "upstream_unavailable"})

    def test_success_response_is_bounded_and_no_secret(self):
        fake = FakeResponse(json.dumps({"candidates": [{"content": {"parts": [{"text": "Xin chào " * 100}]}}]}).encode())
        with patch.object(proxy, "open_gemini", return_value=fake) as upstream:
            status, data = self.request({"topic": "dậy sớm", "profile": 1})
        self.assertEqual(status, 200)
        self.assertLessEqual(len(data["message"]), 500)
        self.assertNotIn("fake-google-key", json.dumps(data))
        self.assertEqual(upstream.call_args.args[0].headers["X-goog-api-key"], "fake-google-key")

    def test_durable_global_quota_across_requests(self):
        fake = FakeResponse(b'{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}')
        with patch.object(proxy, "open_gemini", return_value=fake) as upstream:
            self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 429)
            self.assertEqual(upstream.call_count, 2)

    def test_upstream_failure_consumes_attempt(self):
        with patch.object(proxy, "open_gemini", side_effect=OSError("offline")):
            self.assertEqual(self.request()[0], 502)
            self.assertEqual(self.request()[0], 502)
            self.assertEqual(self.request()[0], 429)

    def test_bad_model_rejected_at_startup(self):
        with patch.object(proxy, "MODEL", "../other-model"):
            with self.assertRaises(ValueError):
                proxy.validate_config()

    def test_duplicate_api_key_profiles_rejected_at_startup(self):
        with patch.object(proxy, "KEYS", {1: "same-key", 2: "same-key"}):
            with self.assertRaises(ValueError):
                proxy.validate_config()

    def test_bad_profile_daily_limit_rejected_at_startup(self):
        for invalid in (0, 10001):
            with self.subTest(invalid=invalid), patch.object(proxy, "PROFILE_DAILY_LIMIT", invalid):
                with self.assertRaises(ValueError):
                    proxy.validate_config()

    def test_concurrent_sqlite_quota_never_overbooks(self):
        db = os.path.join(self.tmp.name, "concurrent.sqlite3")
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            values = list(executor.map(lambda _: proxy.reserve_daily_attempt(db, "2026-11-01", 3), range(8)))
        self.assertEqual(sum(values), 3)

    def test_profile_quota_is_atomic_and_does_not_burn_global_quota_on_rejection(self):
        db = os.path.join(self.tmp.name, "profile.sqlite3")
        day = "2026-11-01"
        self.assertTrue(proxy.reserve_daily_attempt(db, day, 5, 1, 2))
        self.assertTrue(proxy.reserve_daily_attempt(db, day, 5, 1, 2))
        self.assertFalse(proxy.reserve_daily_attempt(db, day, 5, 1, 2))
        self.assertTrue(proxy.reserve_daily_attempt(db, day, 5, 2, 2))
        self.assertTrue(proxy.reserve_daily_attempt(db, day, 5, 2, 2))
        self.assertTrue(proxy.reserve_daily_attempt(db, day, 5, 3, 2))
        self.assertFalse(proxy.reserve_daily_attempt(db, day, 5, 3, 2))
        with sqlite3.connect(db) as conn:
            self.assertEqual(conn.execute("SELECT attempts FROM quota WHERE day=?", (day,)).fetchone()[0], 5)
            self.assertEqual(conn.execute(
                "SELECT profile, attempts FROM profile_quota WHERE day=? ORDER BY profile",
                (day,)).fetchall(), [(1, 2), (2, 2), (3, 1)])

    def test_profile_quota_persists_across_connections_and_resets_on_new_day(self):
        db = os.path.join(self.tmp.name, "persistent.sqlite3")
        self.assertTrue(proxy.reserve_daily_attempt(db, "2026-11-01", 10, 1, 1))
        self.assertFalse(proxy.reserve_daily_attempt(db, "2026-11-01", 10, 1, 1))
        self.assertTrue(proxy.reserve_daily_attempt(db, "2026-11-02", 10, 1, 1))

    def test_concurrent_global_and_profile_quotas_cannot_overbook(self):
        db = os.path.join(self.tmp.name, "parallel.sqlite3")
        def attempt(profile):
            return profile, proxy.reserve_daily_attempt(db, "2026-11-01", 5, profile, 3)
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
            results = list(executor.map(attempt, [1, 2] * 8))
        self.assertEqual(sum(ok for _, ok in results), 5)
        for profile in (1, 2):
            self.assertLessEqual(sum(ok for p, ok in results if p == profile), 3)

    def test_http_profile_ceiling_rejects_before_upstream_call(self):
        fake = FakeResponse(b'{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}')
        with (patch.object(proxy, "PROFILE_DAILY_LIMIT", 1),
              patch.object(proxy, "DAILY_LIMIT", 3),
              patch.object(proxy, "KEYS", {1: "key1", 2: "key2"}),
              patch.object(proxy, "open_gemini", return_value=fake) as upstream):
            self.assertEqual(self.request({"topic": "hello", "profile": 1})[0], 200)
            self.assertEqual(self.request({"topic": "hello", "profile": 1})[0], 429)
            self.assertEqual(self.request({"topic": "hello", "profile": 2})[0], 200)
            self.assertEqual(upstream.call_count, 2)


    def test_budget_reservation_is_atomic_across_profiles_and_threads(self):
        db = os.path.join(self.tmp.name, "budget.sqlite3")
        def reserve(profile):
            return proxy.reserve_daily_attempt(db, "2026-11-01", 50, profile, 50,
                                               cost_limit=150_000, cost_reserve=50_000)
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
            accepted = list(executor.map(reserve, [1, 2] * 10))
        self.assertEqual(sum(accepted), 3)
        with sqlite3.connect(db) as conn:
            self.assertEqual(conn.execute("SELECT attempts FROM quota").fetchone()[0], 3)
            self.assertEqual(conn.execute(
                "SELECT reserved_microusd FROM reserved_budget").fetchone()[0], 150_000)
            self.assertEqual(conn.execute(
                "SELECT SUM(attempts) FROM profile_quota").fetchone()[0], 3)

    def test_budget_persists_and_resets_by_utc_day(self):
        db = os.path.join(self.tmp.name, "budget-persist.sqlite3")
        args = (db, "2026-11-01", 20, 1, 20, 60_000, 30_000)
        self.assertTrue(proxy.reserve_daily_attempt(*args))
        self.assertTrue(proxy.reserve_daily_attempt(*args))
        self.assertFalse(proxy.reserve_daily_attempt(*args))
        self.assertTrue(proxy.reserve_daily_attempt(
            db, "2026-11-02", 20, 1, 20, 60_000, 30_000))

    def test_rejected_profile_quota_does_not_spend_budget(self):
        db = os.path.join(self.tmp.name, "budget-profile.sqlite3")
        self.assertTrue(proxy.reserve_daily_attempt(db, "2026-11-01", 10, 1, 1, 100_000, 50_000))
        self.assertFalse(proxy.reserve_daily_attempt(db, "2026-11-01", 10, 1, 1, 100_000, 50_000))
        self.assertTrue(proxy.reserve_daily_attempt(db, "2026-11-01", 10, 2, 1, 100_000, 50_000))
        with sqlite3.connect(db) as conn:
            self.assertEqual(conn.execute(
                "SELECT reserved_microusd FROM reserved_budget").fetchone()[0], 100_000)

    def test_invalid_budget_configuration_fails_closed(self):
        for reserve, budget in ((0, 100), (101, 100), (1, 0), (1, 1_000_000_001)):
            with (self.subTest(reserve=reserve, budget=budget),
                  patch.object(proxy, "REQUEST_RESERVE_MICROUSD", reserve),
                  patch.object(proxy, "DAILY_BUDGET_MICROUSD", budget)):
                with self.assertRaises(ValueError):
                    proxy.validate_config()
        with self.assertRaises(ValueError):
            proxy.reserve_daily_attempt("unused.db", "2026-11-01", 5,
                                        cost_limit=100, cost_reserve=None)

    def test_http_budget_ceiling_blocks_upstream_and_keeps_quota_consistent(self):
        fake = FakeResponse(b'{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}')
        with (patch.object(proxy, "DAILY_LIMIT", 10),
              patch.object(proxy, "DAILY_BUDGET_MICROUSD", 50_000),
              patch.object(proxy, "REQUEST_RESERVE_MICROUSD", 50_000),
              patch.object(proxy, "open_gemini", return_value=fake) as upstream):
            self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 429)
            self.assertEqual(upstream.call_count, 1)
            with sqlite3.connect(proxy.QUOTA_DB) as conn:
                self.assertEqual(conn.execute("SELECT attempts FROM quota").fetchone()[0], 1)
                self.assertEqual(conn.execute(
                    "SELECT reserved_microusd FROM reserved_budget").fetchone()[0], 50_000)


if __name__ == "__main__":
    unittest.main()
