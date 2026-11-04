"""Ensure sensitive preferences never enter Android cloud backup or device transfer.

Run: python -m unittest discover -s tests -p 'test_*.py' -v
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
XML = ROOT / "app" / "src" / "main" / "res" / "xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
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
        cls.manifest = ET.parse(ROOT / "app" / "src" / "main" / "AndroidManifest.xml").getroot()
        cls.legacy = ET.parse(XML / "backup_rules.xml").getroot()
        cls.modern = ET.parse(XML / "data_extraction_rules.xml").getroot()

    def test_manifest_actually_uses_both_audited_backup_rule_files(self):
        # Valid exclusion XML is useless if a future manifest edit stops
        # referencing it. Guard both legacy and Android 12+ entry points.
        app = self.manifest.find("application")
        self.assertIsNotNone(app)
        self.assertEqual("@xml/backup_rules", app.get(ANDROID_NS + "fullBackupContent"))
        self.assertEqual("@xml/data_extraction_rules", app.get(ANDROID_NS + "dataExtractionRules"))

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

    def test_sensitive_exclusions_are_unconditional(self):
        # A conditional exclusion may silently allow secrets to transfer on
        # devices that do not meet its flags. The privacy contract is absolute.
        for destination, rules in (
            ("legacy", self.legacy),
            ("cloud", self.modern.find("cloud-backup")),
            ("transfer", self.modern.find("device-transfer")),
        ):
            with self.subTest(destination=destination):
                self.assertIsNotNone(rules)
                for node in rules.findall("exclude"):
                    if (node.get("domain") == "sharedpref"
                            and node.get("path") in SENSITIVE_PREFS):
                        self.assertIsNone(node.get("requireFlags"))

    def test_no_broad_sharedpref_include_can_override_privacy_intent(self):
        # Explicit allow-all sharedpref rules are dangerous during future
        # refactors; the sensitive exclusion list must remain authoritative.
        for destination, rules in (
            ("legacy", self.legacy),
            ("cloud", self.modern.find("cloud-backup")),
            ("transfer", self.modern.find("device-transfer")),
        ):
            with self.subTest(destination=destination):
                self.assertIsNotNone(rules)
                for node in rules.findall("include"):
                    if node.get("domain") == "sharedpref":
                        self.assertNotIn(node.get("path"), (".", "./", "", "*"))

    def test_sensitive_exclusion_policy_consistent_across_versions(self):
        legacy = excluded_preferences(self.legacy)
        cloud = excluded_preferences(self.modern.find("cloud-backup"))
        transfer = excluded_preferences(self.modern.find("device-transfer"))
        self.assertEqual(legacy, cloud)
        self.assertEqual(legacy, transfer)


if __name__ == "__main__":
    unittest.main()
