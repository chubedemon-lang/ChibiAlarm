"""Guard release-facing Android configuration against bundled Gemini credentials.

This is a narrow static regression check, not a full secret scanner or a substitute
for reviewing the merged manifest and final APK.
"""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = ROOT / "app"

# Only inspect files that are directly packaged or configure Android builds.
CONFIG_FILES = (
    APP / "build.gradle",
    APP / "build.gradle.kts",
    APP / "src/main/AndroidManifest.xml",
    APP / "src/main/res/values/strings.xml",
    APP / "src/main/res/values/secrets.xml",
)

# Google API keys have a distinctive prefix. Avoid logging candidate secrets.
GOOGLE_KEY = re.compile(r"AIza[0-9A-Za-z_-]{30,}")
SECRET_ASSIGNMENT = re.compile(
    r"""(?ix)(?:gemini|google)[_\s.-]*(?:api[_\s.-]*)?key
    \s*[:=]\s*["'](?!\$|@|\{|<)[^"'\s]{12,}["']"""
)


class PackagedAiCredentialBoundaryTests(unittest.TestCase):
    def test_no_literal_google_api_keys_in_packaging_config(self):
        for path in CONFIG_FILES:
            if path.exists():
                with self.subTest(path=path.relative_to(ROOT)):
                    self.assertIsNone(
                        GOOGLE_KEY.search(path.read_text(encoding="utf-8")),
                        f"Possible bundled Google API key in {path.relative_to(ROOT)}",
                    )

    def test_no_literal_gemini_key_assignments_in_packaging_config(self):
        for path in CONFIG_FILES:
            if path.exists():
                with self.subTest(path=path.relative_to(ROOT)):
                    self.assertIsNone(
                        SECRET_ASSIGNMENT.search(path.read_text(encoding="utf-8")),
                        f"Possible hardcoded AI credential in {path.relative_to(ROOT)}",
                    )

    def test_manifest_does_not_embed_google_api_key_metadata(self):
        path = APP / "src/main/AndroidManifest.xml"
        root = ET.parse(path).getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        for node in root.iter("meta-data"):
            name = (node.get(android + "name") or "").lower()
            self.assertFalse(
                ("gemini" in name or "google" in name) and "key" in name,
                "AI credentials must not be distributed as manifest metadata",
            )


if __name__ == "__main__":
    unittest.main()
