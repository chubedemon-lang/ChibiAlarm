"""Ensure sensitive preferences never enter Android cloud backup or device transfer.

Run: python -m unittest discover -s tests -p 'test_*.py' -v
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
XML = ROOT / "app" / "src" / "main" / "res" / "xml"
SENSITIVE_PREFS = frozenset({
    "gemini_secure.xml",
    "gemini_vault.xml",
    "coach_history.xml",
    "settings.xml",
})


def excluded_preferences(element):
    return {
        node.get("path")
        for node in element.findall("exclude")
        if node.get("domain") == "sharedpref"
    }


class BackupPrivacyRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.legacy = ET.parse(XML / "backup_rules.xml").getroot()
        cls.modern = ET.parse(XML / "data_extraction_rules.xml").getroot()

    def test_sensitive_preferences_excluded_on_android_6_to_11(self):
        self.assertEqual("full-backup-content", self.legacy.tag)
        self.assertTrue(
            SENSITIVE_PREFS.issubset(excluded_preferences(self.legacy))
        )

    def test_sensitive_preferences_excluded_from_cloud_and_transfer(self):
        self.assertEqual("data-extraction-rules", self.modern.tag)
        for destination in ("cloud-backup", "device-transfer"):
            with self.subTest(destination=destination):
                rules = self.modern.find(destination)
                self.assertIsNotNone(rules)
                self.assertTrue(
                    SENSITIVE_PREFS.issubset(excluded_preferences(rules))
                )

    def test_no_sensitive_preference_explicitly_included(self):
        for rules in (
            self.legacy,
            self.modern.find("cloud-backup"),
            self.modern.find("device-transfer"),
        ):
            self.assertIsNotNone(rules)
            included = {
                node.get("path")
                for node in rules.findall("include")
                if node.get("domain") == "sharedpref"
            }
            self.assertFalse(SENSITIVE_PREFS.intersection(included))

    def test_sensitive_exclusion_policy_consistent_across_versions(self):
        legacy = excluded_preferences(self.legacy)
        cloud = excluded_preferences(self.modern.find("cloud-backup"))
        transfer = excluded_preferences(self.modern.find("device-transfer"))
        self.assertEqual(legacy, cloud)
        self.assertEqual(legacy, transfer)


if __name__ == "__main__":
    unittest.main()
