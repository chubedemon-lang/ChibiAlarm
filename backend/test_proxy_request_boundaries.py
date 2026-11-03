"""Boundary regression tests for the development Gemini proxy (offline only)."""
import http.client
import json
import os
import tempfile
import threading
import unittest
from unittest.mock import patch

import gemini_proxy as proxy


class RequestBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.token = "boundary-test-private-token-123456"
        for name, value in (
            ("TOKEN", self.token),
            ("KEYS", {1: "fake-test-key", 2: "", 3: "", 4: "", 5: ""}),
            ("QUOTA_DB", os.path.join(self.temp.name, "quota.sqlite3")),
        ):
            p = patch.object(proxy, name, value)
            p.start()
            self.addCleanup(p.stop)
        self.server = proxy.ThreadingHTTPServer(("127.0.0.1", 0), proxy.Handler)
        worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        worker.start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def post(self, raw):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=4)
        try:
            conn.request("POST", "/generate", body=raw, headers={
                "Authorization": "Bearer " + self.token,
                "Content-Type": "application/json",
            })
            response = conn.getresponse()
            return response.status, json.loads(response.read())
        finally:
            conn.close()

    def test_malformed_utf8_and_non_object_json_fail_before_quota(self):
        cases = (
            b'{"topic":"\xff"}',   # invalid UTF-8 byte
            b'null', b'[]', b'"morning"', b'123', b'true',
            b'{"topic":"wake","profile":true}',
            b'{"topic":"wake","profile":1.0}',
        )
        with patch.object(proxy, "open_gemini") as upstream:
            for raw in cases:
                with self.subTest(raw=raw):
                    self.assertEqual((400, {"error": "invalid_request"}), self.post(raw))
            upstream.assert_not_called()
        self.assertFalse(os.path.exists(proxy.QUOTA_DB))

    def test_unconfigured_profile_returns_profile_unavailable_without_quota(self):
        with patch.object(proxy, "open_gemini") as upstream:
            self.assertEqual((400, {"error": "profile_unavailable"}),
                             self.post(b'{"topic":"wake","profile":0}'))
            upstream.assert_not_called()
        self.assertFalse(os.path.exists(proxy.QUOTA_DB))

    def test_health_check_does_not_create_quota_or_call_upstream(self):
        with patch.object(proxy, "open_gemini") as upstream:
            conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=4)
            try:
                conn.request("GET", "/healthz")
                response = conn.getresponse()
                self.assertEqual(200, response.status)
                self.assertEqual({"status": "ok"}, json.loads(response.read()))
            finally:
                conn.close()
            upstream.assert_not_called()
        self.assertFalse(os.path.exists(proxy.QUOTA_DB))


if __name__ == "__main__":
    unittest.main()
