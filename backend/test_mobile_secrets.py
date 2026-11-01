"""Release guard: fail CI if a provider credential is embedded in Android sources.

This is a deliberately narrow source-level scan, not a substitute for APK
reverse-engineering, secret management or a security review. BYOK runtime user
input is not a bundled project key.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MOBILE = ROOT / "app"
SOURCES = MOBILE / "src" / "main"
PATTERNS = {
    "google_api_key": re.compile(r"AIza[0-9A-Za-z_-]{35}"),
    "private_key_pem": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "openai_secret": re.compile(r"sk-(?:proj-)?[A-Za-z0-9_-]{32,}"),
}


class MobileSecretGuardTests(unittest.TestCase):
    def test_no_embedded_provider_secrets_in_android_sources(self):
        candidates = list(SOURCES.rglob("*.kt")) + list(SOURCES.rglob("*.java"))
        candidates += list(SOURCES.rglob("*.xml"))
        candidates.append(MOBILE / "build.gradle.kts")
        for path in candidates:
            if not path.is_file():
                continue
            source = path.read_text(encoding="utf-8")
            for label, pattern in PATTERNS.items():
                with self.subTest(file=str(path.relative_to(ROOT)), pattern=label):
                    self.assertIsNone(pattern.search(source),
                        "Potential bundled secret: " + label + " in " + str(path.relative_to(ROOT)))

    def test_no_android_google_services_credentials_file(self):
        self.assertFalse((MOBILE / "google-services.json").exists(),
            "Audit google-services.json before packaging; never bundle server Gemini keys")
        self.assertFalse((MOBILE / "src" / "main" / "assets" / ".env").exists())


if __name__ == "__main__":
    unittest.main()
