"""Offline tests for Gemini text sanitization (no network or credentials)."""
import unittest

import gemini_proxy as proxy


class GeminiTextSafetyTests(unittest.TestCase):
    def candidate(self, parts):
        return {"candidates": [{"content": {"parts": parts}}]}

    def test_vietnamese_combining_marks_and_emoji_are_allowed(self):
        text = "Cha\u0300o buo\u0309i sa\u0301ng 🌞"
        self.assertTrue(proxy.safe_user_topic(text))
        self.assertEqual(proxy.extract_safe_message(self.candidate([{"text": text}])), text)

    def test_topic_rejects_invisible_and_directional_controls(self):
        for bad in ("\u202e", "\u2060", "\u061c", "\u2028", "\u0000", "\ud800"):
            with self.subTest(codepoint=repr(bad)):
                self.assertFalse(proxy.safe_user_topic("hello" + bad))

    def test_upstream_rejects_unsafe_text_before_truncation(self):
        # A forbidden code point after the 500-character response limit must
        # not be hidden by truncation.
        for bad in ("\u202e", "\u2060", "\u2028", "\ud800", "\u0000"):
            with self.subTest(codepoint=repr(bad)):
                with self.assertRaises(ValueError):
                    proxy.extract_safe_message(self.candidate([{"text": "a" * 501 + bad}]))

    def test_upstream_allows_newline_and_tab_but_not_carriage_return(self):
        self.assertEqual(
            proxy.extract_safe_message(self.candidate([{"text": "Chào\n\tbạn"}])),
            "Chào\n\tbạn")
        with self.assertRaises(ValueError):
            proxy.extract_safe_message(self.candidate([{"text": "Chào\rbạn"}]))

    def test_empty_and_malformed_parts_are_rejected(self):
        for parts in (None, {}, [], [{"text": 5}], [{"other": "ignored"}]):
            with self.subTest(parts=parts):
                with self.assertRaises(ValueError):
                    proxy.extract_safe_message(self.candidate(parts))

    def test_response_length_is_bounded(self):
        self.assertEqual(len(proxy.extract_safe_message(
            self.candidate([{"text": "a" * 800}]))), 500)


if __name__ == "__main__":
    unittest.main()
