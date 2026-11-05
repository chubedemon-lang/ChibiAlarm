"""Scan shipped Android source and resources for literal Google API credentials.

This complements narrow Gradle/manifest checks. It deliberately avoids scanning
build outputs, tests, and user-provided runtime credentials.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main"
GOOGLE_API_KEY = re.compile(r"AIza[0-9A-Za-z_-]{30,}")
TEXT_SUFFIXES = {".kt", ".java", ".xml", ".json", ".properties", ".html", ".js"}
MAX_FILE_BYTES = 2_000_000


def find_packaged_google_keys(source_root=SOURCE):
    """Return only file paths, never matching secrets."""
    if not source_root.is_dir():
        raise FileNotFoundError(f"Android source directory missing: {source_root}")
    offenders = []
    for path in sorted(source_root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        if path.stat().st_size > MAX_FILE_BYTES:
            raise ValueError(f"Source file too large to scan safely: {path}")
        content = path.read_text(encoding="utf-8")
        if GOOGLE_API_KEY.search(content):
            offenders.append(path.relative_to(source_root).as_posix())
    return offenders


class PackagedGoogleKeySourceTests(unittest.TestCase):
    def test_android_main_sources_have_no_literal_google_keys(self):
        self.assertEqual(
            [],
            find_packaged_google_keys(),
            "Potential Google API key in shipped source; inspect paths privately",
        )

    def test_scanner_detects_key_without_echoing_it(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "safe.kt").write_text('val token = "runtime-only"', encoding="utf-8")
            self.assertEqual([], find_packaged_google_keys(root))
            (root / "bad.xml").write_text("AIza" + "A" * 35, encoding="utf-8")
            self.assertEqual(["bad.xml"], find_packaged_google_keys(root))


if __name__ == "__main__":
    unittest.main()
