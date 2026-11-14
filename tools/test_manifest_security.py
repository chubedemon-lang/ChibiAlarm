"""Regression checks for Android transport and component exposure policy."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

class ManifestSecurityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()

    def test_cleartext_network_disabled(self):
        application = self.root.find("application")
        self.assertIsNotNone(application)
        self.assertEqual("false", application.get(ANDROID + "usesCleartextTraffic"))

    def test_sensitive_activities_not_exported(self):
        application = self.root.find("application")
        sensitive = {".CoachActivity", ".RpgActivity", ".FitnessActivity",
                     ".FocusActivity", ".AIVoiceActivity", ".VoiceStudioActivity"}
        activities = {a.get(ANDROID + "name"): a for a in application.findall("activity")}
        for name in sensitive:
            with self.subTest(name=name):
                self.assertIn(name, activities)
                self.assertEqual("false", activities[name].get(ANDROID + "exported"))

    def test_private_backup_rules_explicit(self):
        application = self.root.find("application")
        self.assertEqual("@xml/backup_rules", application.get(ANDROID + "fullBackupContent"))
        self.assertEqual("@xml/data_extraction_rules", application.get(ANDROID + "dataExtractionRules"))

if __name__ == "__main__":
    unittest.main()
