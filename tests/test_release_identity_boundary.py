"""Prevent accidental release of Android debug and cross-app identity privileges.

Source-manifest assertions only; review the merged release manifest separately.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


class ReleaseIdentityBoundaryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(MANIFEST).getroot()
        cls.app = cls.root.find("application")

    def test_application_cannot_force_debug_or_test_mode(self):
        self.assertIsNotNone(self.app)
        for attr in ("debuggable", "testOnly"):
            self.assertNotEqual(
                "true", (self.app.get(ANDROID + attr) or "").lower(),
                f"Source manifest must not force android:{attr}=true",
            )

    def test_application_does_not_share_linux_uid_with_other_apps(self):
        self.assertFalse(
            self.root.get(ANDROID + "sharedUserId"),
            "sharedUserId breaks per-app sandbox assumptions",
        )
        self.assertFalse(
            self.root.get(ANDROID + "sharedUserMaxSdkVersion"),
            "sharedUserMaxSdkVersion must not be used",
        )

    def test_production_manifest_does_not_enable_shell_profiling(self):
        self.assertIsNotNone(self.app)
        for node in self.app.findall("profileable"):
            self.assertNotEqual(
                "true", (node.get(ANDROID + "shell") or "").lower(),
                "Shell profiling must require a separate reviewed build variant",
            )


if __name__ == "__main__":
    unittest.main()
