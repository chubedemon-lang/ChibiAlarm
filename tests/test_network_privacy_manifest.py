"""Static regression checks for the public-release network boundary.

These checks do not replace runtime TLS validation, backend authentication,
or a review of merged build variants.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

# Explicitly disallow privileges unrelated to the offline-first product.
FORBIDDEN_PERMISSIONS = {
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
}


class NetworkPrivacyManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(MANIFEST).getroot()
        cls.app = cls.root.find("application")

    def test_cleartext_http_is_explicitly_disabled(self):
        self.assertIsNotNone(self.app)
        self.assertEqual("false", self.app.get(ANDROID + "usesCleartextTraffic"))

    def test_no_unreviewed_high_risk_permissions(self):
        declared = {
            item.get(ANDROID + "name")
            for tag in ("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")
            for item in self.root.findall(tag)
        }
        self.assertFalse(
            declared & FORBIDDEN_PERMISSIONS,
            f"Unexpected privileged permissions: {declared & FORBIDDEN_PERMISSIONS}",
        )

    def test_no_network_security_config_enables_cleartext(self):
        config = self.app.get(ANDROID + "networkSecurityConfig")
        if config is None:
            return
        self.assertTrue(config.startswith("@xml/"))
        xml = ROOT / "app/src/main/res/xml" / (config.split("/", 1)[1] + ".xml")
        self.assertTrue(xml.is_file(), f"Missing network security config: {xml}")
        security = ET.parse(xml).getroot()
        for node in security.iter():
            if node.tag in ("base-config", "domain-config"):
                self.assertNotEqual(
                    "true", node.get("cleartextTrafficPermitted"),
                    "A network security config may not opt back into plaintext HTTP",
                )


if __name__ == "__main__":
    unittest.main()
