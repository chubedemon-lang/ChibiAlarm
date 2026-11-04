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


    def test_launcher_category_and_widget_provider_contract(self):
        launcher = next(node for node in self.app.findall("activity")
                        if node.get(ANDROID + "name") == ".MainActivity")
        categories = {category.get(ANDROID + "name")
                      for filt in launcher.findall("intent-filter")
                      for category in filt.findall("category")}
        self.assertEqual({"android.intent.category.LAUNCHER"}, categories)
        widget = next(node for node in self.app.findall("receiver")
                      if node.get(ANDROID + "name") == ".ChibiWidget")
        metadata = [(node.get(ANDROID + "name"), node.get(ANDROID + "resource"))
                    for node in widget.findall("meta-data")]
        self.assertEqual([("android.appwidget.provider", "@xml/chibi_widget_info")], metadata)

    def test_system_alarm_receivers_remain_private(self):
        private_receivers = {
            ".AlarmReceiver", ".BootReceiver", ".FocusDoneReceiver",
            ".RoutineReminderReceiver", ".GrowthReminderReceiver",
        }
        receivers = {node.get(ANDROID + "name"): node
                     for node in self.app.findall("receiver")}
        for name in private_receivers:
            self.assertIn(name, receivers)
            self.assertEqual("false", receivers[name].get(ANDROID + "exported"),
                             f"{name} must not be externally callable")
        boot = receivers[".BootReceiver"]
        actions = {action.get(ANDROID + "name")
                   for filt in boot.findall("intent-filter")
                   for action in filt.findall("action")}
        self.assertEqual({
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
        }, actions)


if __name__ == "__main__":
    unittest.main()
