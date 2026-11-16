"""Guard Android alarm/notification permissions against silent manifest regressions.

Source-manifest checks only; merged manifests and Android 16 runtime behavior
still require device verification before any public release.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

REQUIRED = {
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.USE_FULL_SCREEN_INTENT",
    "android.permission.VIBRATE",
}
PERMISSION_TAGS = ("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")


class AlarmPermissionContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(MANIFEST).getroot()

    def test_critical_alarm_permissions_are_present_once(self):
        declarations = [
            (node.get(ANDROID + "name"), node)
            for tag in PERMISSION_TAGS
            for node in self.root.findall(tag)
        ]
        for name in REQUIRED:
            matches = [node for declared, node in declarations if declared == name]
            self.assertEqual(1, len(matches), f"Missing or duplicate alarm permission: {name}")

    def test_alarm_permissions_not_limited_to_older_android_versions(self):
        for tag in PERMISSION_TAGS:
            for node in self.root.findall(tag):
                name = node.get(ANDROID + "name")
                if name in REQUIRED:
                    self.assertEqual("uses-permission", tag, f"{name} must apply across supported SDKs")
                    self.assertIsNone(node.get(ANDROID + "maxSdkVersion"),
                                      f"{name} unexpectedly restricted to older Android")
                    self.assertIsNone(node.get(ANDROID + "usesPermissionFlags"),
                                      f"{name} has unexpected permission flags")

    def test_permission_names_are_nonempty_and_unique(self):
        seen = set()
        for tag in PERMISSION_TAGS:
            for node in self.root.findall(tag):
                name = node.get(ANDROID + "name")
                self.assertTrue(name and name.strip(), "Permission missing android:name")
                self.assertNotIn(name, seen, f"Duplicate permission declaration: {name}")
                seen.add(name)


if __name__ == "__main__":
    unittest.main()
