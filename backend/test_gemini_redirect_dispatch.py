"""Offline regression tests for Gemini upstream redirect credential isolation.

The API key is server-owned. A redirect to an arbitrary origin must never
create a follow-up request containing x-goog-api-key.
"""
import unittest
from unittest import mock
import io
import urllib.response
import urllib.error
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
        for code in (301, 302, 303, 307, 308):
            for target in (
                "https://attacker.example/collect",
                "http://generativelanguage.googleapis.com/collect",
                "https://generativelanguage.googleapis.com/another-path",
            ):
                with self.subTest(code=code, target=target):
                    self.assertIsNone(handler.redirect_request(
                        request, None, code, "Redirect", {}, target))

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

    def test_real_opener_refuses_redirect_without_second_network_request(self):
        """Exercise urllib's actual redirect dispatch, not just our handler method."""
        class RedirectingHandler(urllib.request.BaseHandler):
            handler_order = 100

            def __init__(self):
                self.requests = []

            def https_open(self, request):
                self.requests.append(request)
                if len(self.requests) > 1:
                    raise AssertionError("credential-bearing redirect was followed")
                headers = {"Location": "https://attacker.example/collect"}
                response = urllib.response.addinfourl(
                    io.BytesIO(b""), headers, request.full_url, 302)
                response.msg = "Found"
                return response

        handler = RedirectingHandler()
        original_build = urllib.request.build_opener

        def build_with_fake(*handlers):
            return original_build(handler, *handlers)

        request = urllib.request.Request(
            "https://generativelanguage.googleapis.com/v1beta/models/example:generateContent",
            data=b"{}", headers={"x-goog-api-key": "server-only-test-secret"},
            method="POST")
        with mock.patch.object(urllib.request, "build_opener", side_effect=build_with_fake):
            with self.assertRaises(urllib.error.HTTPError) as caught:
                proxy.open_gemini(request, timeout=3)
        self.assertEqual(caught.exception.code, 302)
        self.assertEqual(len(handler.requests), 1)
        self.assertEqual(handler.requests[0].get_header("X-goog-api-key"),
                         "server-only-test-secret")


if __name__ == "__main__":
    unittest.main()
