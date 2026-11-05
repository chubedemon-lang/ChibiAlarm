"""Fail closed when a Google API key is embedded in Android-shipped text assets.

This static guard cannot inspect generated build outputs or the compiled APK.
Only relative file paths are reported; candidate credentials are never logged.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main"
GOOGLE_API_KEY = re.compile(r"AIza[0-9A-Za-z_-]{30,}")
TEXT_SUFFIXES = {
    ".kt", ".java", ".xml", ".json", ".properties", ".html", ".js",
    ".txt", ".yaml", ".yml", ".toml", ".cfg", ".ini", ".env",
}
MAX_FILE_BYTES = 2_000_000


def find_packaged_google_keys(source_root=SOURCE):
    """Return only relative offending paths, not secrets."""
    if not source_root.is_dir():
        raise FileNotFoundError("Android source directory missing")
    offenders = []
    for path in sorted(source_root.rglob("*")):
        if path.is_symlink():
            # Do not read linked files outside the checked-out source tree.
            continue
        if not path.is_file():
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES and path.name != ".env":
            continue
        relative = path.relative_to(source_root).as_posix()
        if path.stat().st_size > MAX_FILE_BYTES:
            raise ValueError(f"Source file too large to scan safely: {relative}")
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeError as exc:
            raise ValueError(f"Text source is not valid UTF-8: {relative}") from exc
        if GOOGLE_API_KEY.search(content):
            offenders.append(relative)
    return offenders


class PackagedGoogleKeySourceTests(unittest.TestCase):
    def test_android_main_sources_have_no_literal_google_keys(self):
        self.assertEqual(
            [], find_packaged_google_keys(),
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

    def test_nested_assets_and_env_files_are_scanned(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / "assets"
            assets.mkdir()
            (assets / "config.yaml").write_text("key: AIza" + "B" * 35, encoding="utf-8")
            (root / ".env").write_text("KEY=AIza" + "C" * 35, encoding="utf-8")
            self.assertEqual([".env", "assets/config.yaml"], find_packaged_google_keys(root))

    def test_oversized_text_file_fails_closed_without_leaking_contents(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "oversize.json").write_bytes(b"x" * (MAX_FILE_BYTES + 1))
            with self.assertRaisesRegex(ValueError, "oversize.json"):
                find_packaged_google_keys(root)

    def test_symlink_is_not_followed(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as directory:
            root = Path(directory)
            outside = root.parent / (root.name + "-outside.txt")
            try:
                outside.write_text("AIza" + "D" * 35, encoding="utf-8")
                (root / "linked.txt").symlink_to(outside)
                self.assertEqual([], find_packaged_google_keys(root))
            except (OSError, NotImplementedError):
                self.skipTest("Symlinks unavailable on this filesystem")
            finally:
                outside.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
