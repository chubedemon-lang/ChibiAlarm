"""Offline regression tests for Gemini upstream redirect credential isolation.

The API key is server-owned. A redirect to an arbitrary origin must never
create a follow-up request containing x-goog-api-key.
"""
import unittest
from unittest import mock
import urllib.request

import gemini_proxy as proxy


class UpstreamRedirectIsolationTests(unittest.TestCase):
    def test_redirect_handler_refuses_cross_origin_and_same_origin_redirects(self):
        handler = proxy.NoUpstreamRedirects()
        request = urllib.request.Request(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            data=b"{}",
            headers={"x-goog-api-key": "server-only-test-secret"},
            method="POST",
        )
        for target in (
            "https://attacker.example/collect",
            "http://generativelanguage.googleapis.com/collect",
            "https://generativelanguage.googleapis.com/another-path",
        ):
            with self.subTest(target=target):
                self.assertIsNone(handler.redirect_request(
                    request, None, 307, "Temporary Redirect", {}, target))

    def test_upstream_opener_installs_fail_closed_redirect_handler(self):
        class FakeOpener:
            def open(self, request, timeout):
                return (request, timeout)

        with mock.patch.object(urllib.request, "build_opener", return_value=FakeOpener()) as build:
            request = urllib.request.Request("https://generativelanguage.googleapis.com/")
            returned_request, timeout = proxy.open_gemini(request, timeout=3)
        self.assertIs(returned_request, request)
        self.assertEqual(timeout, 3)
        self.assertTrue(any(isinstance(handler, proxy.NoUpstreamRedirects)
                            for handler in build.call_args.args))


if __name__ == "__main__":
    unittest.main()
