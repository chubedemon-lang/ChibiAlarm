"""Offline inspection of packaged APK text entries for literal Google API keys.

Usage: python -m unittest tests.test_apk_secret_scan
       python tests/test_apk_secret_scan.py path/to/app-debug.apk
This is defense in depth: literal keys in DEX and resources.arsc are inspected, but encoded,
obfuscated, split or dynamically reconstructed keys are NOT covered.
"""
import re
import stat
import sys
import unittest
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

KEY = re.compile(rb"AIza[0-9A-Za-z_-]{30,}")
# DEX/string resources may contain UTF-16LE literals; match without decoding
# arbitrary binary blobs or exposing matched credentials.
KEY_UTF16LE = re.compile(rb"A\x00I\x00z\x00a\x00(?:[0-9A-Za-z_-]\x00){30,}")
SUFFIXES = (".xml", ".json", ".txt", ".html", ".js", ".properties",
            ".yaml", ".yml", ".toml", ".cfg", ".ini", ".env",
            ".svg", ".css", ".mjs", ".webmanifest", ".map")
FORBIDDEN_ASSET_SUFFIXES = (".pem", ".p12", ".pfx", ".jks", ".keystore", ".key")
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
            name = entry.filename
            # Validate the path before entry type so unsafe directory names fail
            # consistently; one trailing slash is legitimate for a ZIP directory.
            parts = name[:-1].split("/") if name.endswith("/") else name.split("/")
            if (not name or name.startswith("/") or "\\" in name
                    or re.match(r"^[A-Za-z]:", name)
                    or any(part in ("", ".", "..") for part in parts)):
                raise ValueError("Unsafe APK entry path")
            # Directory entries are valid ZIP members, but symlinks and other
            # special file types are never accepted.
            mode = (entry.external_attr >> 16) & 0xFFFF
            kind = stat.S_IFMT(mode)
            allowed = (0, stat.S_IFDIR) if entry.is_dir() else (0, stat.S_IFREG)
            if entry.create_system == 3 and kind not in allowed:
                raise ValueError("Unsafe APK entry type")
            if name in seen:
                raise ValueError("Duplicate APK entry")
            seen.add(name)
            if entry.is_dir():
                continue
            # Credential containers have no legitimate place in a public APK, even
            # when encrypted or encoded beyond the literal-key detector.
            if name.startswith("assets/") and name.lower().endswith(FORBIDDEN_ASSET_SUFFIXES):
                raise ValueError("Packaged credential container")
            is_dex = re.fullmatch(r"classes(?:[0-9]+)?\.dex", Path(name).name) is not None and "/" not in name
            is_resource_table = name == "resources.arsc"
            # Native libraries and WebAssembly can carry hardcoded credentials too.
            is_native_binary = (name.startswith("lib/") and name.endswith(".so")) or name.lower().endswith(".wasm")
            # Small opaque assets can also contain embedded plaintext credentials.
            # Keep the existing inspection budget for all scanned entries.
            is_opaque_asset = name.startswith("assets/") and name.lower().endswith((".bin", ".dat", ".db", ".sqlite"))
            if not (name.lower().endswith(SUFFIXES) or is_dex or is_resource_table or is_opaque_asset or is_native_binary):
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
            if KEY.search(data) or KEY_UTF16LE.search(data):
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

    def test_literal_key_in_web_assets_is_detected(self):
        for suffix in (".svg", ".css", ".mjs", ".webmanifest", ".map"):
            with self.subTest(suffix=suffix), TemporaryDirectory() as directory:
                apk = Path(directory) / "sample.apk"
                name = "assets/ui/theme" + suffix
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr(name, "AIza" + "Z" * 35)
                self.assertEqual([name], scan_apk(apk))

    def test_utf16le_key_in_web_asset_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/ui/theme.svg", ("AIza" + "Q" * 35).encode("utf-16le"))
            self.assertEqual(["assets/ui/theme.svg"], scan_apk(apk))

    def test_literal_key_in_dex_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", "AIza" + "B" * 35)
            self.assertEqual(["classes.dex"], scan_apk(apk))

    def test_utf16le_key_in_dex_is_detected_without_exposing_secret(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            secret = "AIza" + "C" * 35
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes2.dex", secret.encode("utf-16le"))
            self.assertEqual(["classes2.dex"], scan_apk(apk))

    def test_utf16le_key_in_resource_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/config.txt", ("AIza" + "D" * 35).encode("utf-16le"))
            self.assertEqual(["assets/config.txt"], scan_apk(apk))

    def test_literal_key_in_compiled_resource_table_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("resources.arsc", b"binary-prefix" + ("AIza" + "E" * 35).encode())
            self.assertEqual(["resources.arsc"], scan_apk(apk))

    def test_utf16le_key_in_compiled_resource_table_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("resources.arsc", ("AIza" + "F" * 35).encode("utf-16le"))
            self.assertEqual(["resources.arsc"], scan_apk(apk))

    def test_literal_key_in_binary_asset_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/config.dat", b"\\x00\\x01" + ("AIza" + "G" * 35).encode())
            self.assertEqual(["assets/config.dat"], scan_apk(apk))

    def test_utf16le_key_in_database_asset_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/cache.db", ("AIza" + "H" * 35).encode("utf-16le"))
            self.assertEqual(["assets/cache.db"], scan_apk(apk))

    def test_literal_key_in_native_library_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libcoach.so", b"ELF" + ("AIza" + "J" * 35).encode())
            self.assertEqual(["lib/arm64-v8a/libcoach.so"], scan_apk(apk))

    def test_utf16le_key_in_wasm_is_detected(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/coach.wasm", ("AIza" + "K" * 35).encode("utf-16le"))
            self.assertEqual(["assets/coach.wasm"], scan_apk(apk))

    def test_packaged_private_key_container_fails_closed(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/service-account.p12", b"opaque-key-material")
            with self.assertRaisesRegex(ValueError, "credential container"):
                scan_apk(apk)

    def test_packaged_keystore_fails_closed_case_insensitive(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/Release.KEYSTORE", b"opaque-key-material")
            with self.assertRaisesRegex(ValueError, "credential container"):
                scan_apk(apk)

    def test_symlink_entry_fails_closed(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                entry = zipfile.ZipInfo("assets/config.txt")
                entry.create_system = 3
                entry.external_attr = (stat.S_IFLNK | 0o777) << 16
                archive.writestr(entry, "target.txt")
            with self.assertRaisesRegex(ValueError, "entry type"):
                scan_apk(apk)

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

    def test_rejects_ambiguous_asset_paths(self):
        for unsafe in ("assets//config.txt", "assets/./config.txt", "assets/../config.txt", "./assets/config.txt"):
            with self.subTest(path=unsafe), TemporaryDirectory() as directory:
                apk = Path(directory) / "sample.apk"
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr(unsafe, "safe")
                with self.assertRaisesRegex(ValueError, "Unsafe"):
                    scan_apk(apk)

    def test_rejects_unsafe_directory_entries(self):
        for unsafe in ("../assets/", "assets/../", "assets//"):
            with self.subTest(path=unsafe), TemporaryDirectory() as directory:
                apk = Path(directory) / "sample.apk"
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr(unsafe, "")
                with self.assertRaisesRegex(ValueError, "Unsafe APK entry path"):
                    scan_apk(apk)

    def test_duplicate_directory_entry_fails_closed(self):
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "sample.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/", "")
                archive.writestr("assets/", "")
            with self.assertRaisesRegex(ValueError, "Duplicate APK entry"):
                scan_apk(apk)

    def test_rejects_windows_drive_paths(self):
        for unsafe in ("C:/assets/config.txt", "D:assets/config.txt"):
            with self.subTest(path=unsafe), TemporaryDirectory() as directory:
                apk = Path(directory) / "sample.apk"
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr(unsafe, "safe")
                with self.assertRaisesRegex(ValueError, "Unsafe APK entry path"):
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
