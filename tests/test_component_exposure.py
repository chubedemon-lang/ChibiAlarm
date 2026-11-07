"""Guard against accidental exposure of sensitive Android components.

Run: python -m unittest discover -s tests -p 'test_*.py'
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class ComponentExposureRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.application = ET.parse(
            ROOT / "app/src/main/AndroidManifest.xml"
        ).getroot().find("application")

    def test_only_launcher_activity_is_exported(self):
        activities = self.application.findall("activity")
        exported = {
            activity.get(ANDROID + "name")
            for activity in activities
            if activity.get(ANDROID + "exported") == "true"
        }
        self.assertEqual({".MainActivity"}, exported)
        for activity in activities:
            if activity.get(ANDROID + "name") != ".MainActivity":
                self.assertEqual(
                    "false", activity.get(ANDROID + "exported"),
                    activity.get(ANDROID + "name"),
                )

    def test_only_widget_receiver_is_exported(self):
        receivers = self.application.findall("receiver")
        exported = {
            receiver.get(ANDROID + "name")
            for receiver in receivers
            if receiver.get(ANDROID + "exported") == "true"
        }
        self.assertEqual({".ChibiWidget"}, exported)
        for receiver in receivers:
            if receiver.get(ANDROID + "name") != ".ChibiWidget":
                self.assertEqual(
                    "false", receiver.get(ANDROID + "exported"),
                    receiver.get(ANDROID + "name"),
                )

    def test_every_component_declares_exported_explicitly(self):
        # Missing android:exported can silently change exposure on older
        # Android versions when an intent-filter is introduced.
        for tag in ("activity", "activity-alias", "receiver", "service", "provider"):
            for component in self.application.findall(tag):
                with self.subTest(tag=tag, name=component.get(ANDROID + "name")):
                    self.assertIn(
                        component.get(ANDROID + "exported"), ("true", "false")
                    )

    def test_only_approved_components_have_intent_filters(self):
        approved = {("activity", ".MainActivity"),
                    ("receiver", ".ChibiWidget"),
                    ("receiver", ".BootReceiver")}
        for tag in ("activity", "activity-alias", "receiver", "service", "provider"):
            for component in self.application.findall(tag):
                if component.findall("intent-filter"):
                    self.assertIn((tag, component.get(ANDROID + "name")), approved)

    def test_no_activity_alias_bypasses_launcher_policy(self):
        self.assertEqual([], self.application.findall("activity-alias"))

    def test_no_unexpected_exported_services_or_providers(self):
        for tag in ("service", "provider"):
            for component in self.application.findall(tag):
                self.assertNotEqual(
                    "true", component.get(ANDROID + "exported"),
                    component.get(ANDROID + "name"),
                )


if __name__ == "__main__":
    unittest.main()
