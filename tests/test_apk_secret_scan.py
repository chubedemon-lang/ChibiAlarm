"""Offline inspection of packaged APK text entries for literal Google API keys.

Usage: python -m unittest tests.test_apk_secret_scan
       python tests/test_apk_secret_scan.py path/to/app-debug.apk
This is defense in depth: literal keys in DEX are inspected, but encoded,\nobfuscated, split or dynamically reconstructed keys are NOT covered.
"""
import re
import sys
import unittest
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

KEY = re.compile(rb"AIza[0-9A-Za-z_-]{30,}")
SUFFIXES = (".xml", ".json", ".txt", ".html", ".js", ".properties",
            ".yaml", ".yml", ".toml", ".cfg", ".ini", ".env")
MAX_ENTRY = 4_000_000
MAX_TOTAL = 32_000_000
MAX_RATIO = 200


def scan_apk(apk_path):
    """Return offending entry names; never return or print credentials."""
    offenders = []
    total = 0
    seen = set()
    with zipfile.ZipFile(apk_path) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            # Avoid zip-slip-style names and ambiguous duplicate entries.
            if name.startswith("/") or ".." in Path(name).parts or "\\" in name:
                raise ValueError("Unsafe APK entry path")
            if name in seen:
                raise ValueError("Duplicate APK entry")
            seen.add(name)
            is_dex = re.fullmatch(r"classes(?:[0-9]+)?\.dex", Path(name).name) is not None and "/" not in name
            if not (name.lower().endswith(SUFFIXES) or is_dex):
                continue
            if entry.file_size > MAX_ENTRY:
                raise ValueError("Oversized APK text entry")
            if entry.file_size > MAX_RATIO * max(1, entry.compress_size):
                raise ValueError("Excessive APK entry compression ratio")
            total += entry.file_size
            if total > MAX_TOTAL:
                raise ValueError("APK text inspection budget exceeded")
            with archive.open(entry) as handle:
                data = handle.read(MAX_ENTRY + 1)
            if len(data) > MAX_ENTRY:
                raise ValueError("APK text entry exceeds limit")
            if KEY.search(data):
                offenders.append(name)
    return offenders


class ApkSecretScanTests(unittest.TestCase):
    def test_finds_key_without_returning_secret(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/config.json", "AIza" + "A" * 35)
                archive.writestr("assets/normal.txt", "safe")
            self.assertEqual(["assets/config.json"], scan_apk(apk))

    def test_literal_key_in_dex_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", "AIza" + "B" * 35)
            self.assertEqual(["classes.dex"], scan_apk(apk))

    def test_duplicate_entry_fails_closed(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/normal.txt", "first")
                archive.writestr("assets/normal.txt", "second")
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                scan_apk(apk)

    def test_compression_bomb_fails_closed(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("assets/repetitive.txt", "x" * 100_000)
            with self.assertRaisesRegex(ValueError, "compression ratio"):
                scan_apk(apk)

    def test_oversized_entry_fails_closed(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/huge.txt", "x" * (MAX_ENTRY + 1))
            with self.assertRaisesRegex(ValueError, "Oversized"):
                scan_apk(apk)

    def test_rejects_traversal(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("../config.txt", "safe")
            with self.assertRaisesRegex(ValueError, "Unsafe"):
                scan_apk(apk)


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1].endswith(".apk"):
        path = sys.argv.pop()
        try:
            matches = scan_apk(path)
        except (OSError, ValueError, zipfile.BadZipFile) as exc:
            print(f"APK inspection failed: {type(exc).__name__}", file=sys.stderr)
            sys.exit(2)
        if matches:
            print("Potential Google API key in APK entries: " + ", ".join(matches), file=sys.stderr)
            sys.exit(1)
        print("No literal Google API key found in inspected APK text entries (literal DEX bytes included; encoded keys not covered)")
        sys.exit(0)
    unittest.main()
