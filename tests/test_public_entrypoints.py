"""Fail closed when new externally reachable Android components are added.

This is a source-manifest guard; inspect merged manifests for each build variant
and perform device testing before a public release.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

# The launcher and Android widget host are the only intentional public entrypoints.
PUBLIC_COMPONENTS = {
    ("activity", ".MainActivity"),
    ("receiver", ".ChibiWidget"),
}
PUBLIC_ACTIONS = {
    ("activity", ".MainActivity"): {"android.intent.action.MAIN"},
    ("receiver", ".ChibiWidget"): {"android.appwidget.action.APPWIDGET_UPDATE"},
}


class PublicEntrypointRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = ET.parse(MANIFEST).getroot().find("application")

    def test_only_reviewed_components_are_exported(self):
        exported = set()
        for tag in ("activity", "activity-alias", "receiver", "service", "provider"):
            for node in self.app.findall(tag):
                name = node.get(ANDROID + "name")
                self.assertIsNotNone(name, f"{tag} missing android:name")
                explicit = node.get(ANDROID + "exported")
                self.assertIn(explicit, ("true", "false"),
                              f"{tag} {name} must declare android:exported")
                if explicit == "true":
                    exported.add((tag, name))
        self.assertEqual(PUBLIC_COMPONENTS, exported)

    def test_public_entrypoints_have_only_expected_actions(self):
        for tag, name in PUBLIC_COMPONENTS:
            matching = [node for node in self.app.findall(tag)
                        if node.get(ANDROID + "name") == name]
            self.assertEqual(1, len(matching), f"Missing/duplicate {tag} {name}")
            node = matching[0]
            actions = {action.get(ANDROID + "name")
                       for filt in node.findall("intent-filter")
                       for action in filt.findall("action")}
            self.assertEqual(PUBLIC_ACTIONS[(tag, name)], actions)

    def test_no_public_data_grants_or_uri_patterns(self):
        for tag in ("activity", "activity-alias", "receiver", "service", "provider"):
            for node in self.app.findall(tag):
                if node.get(ANDROID + "exported") != "true":
                    continue
                self.assertNotEqual("true", node.get(ANDROID + "grantUriPermissions"))
                self.assertEqual([], node.findall("grant-uri-permission"))
                for filt in node.findall("intent-filter"):
                    self.assertEqual([], filt.findall("data"),
                                     "Public entrypoints must not accept arbitrary data URIs")


if __name__ == "__main__":
    unittest.main()
