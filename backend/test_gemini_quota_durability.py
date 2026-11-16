"""Durability and UTC-day boundary regressions for the offline SQLite quota ledger.

Run with: python -m unittest discover -s backend -p 'test_*.py'
"""
import sqlite3
import tempfile
import unittest
from pathlib import Path

try:
    from backend.gemini_proxy import reserve_daily_attempt
except ModuleNotFoundError:
    from gemini_proxy import reserve_daily_attempt


class QuotaDurabilityTests(unittest.TestCase):
    def test_restart_does_not_reset_global_profile_or_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            db = str(Path(directory) / "quota.sqlite3")
            opts = dict(profile=2, profile_limit=2, cost_limit=120, cost_reserve=40)
            self.assertTrue(reserve_daily_attempt(db, "2026-11-06", 10, **opts))
            # Each call opens a fresh SQLite connection, as a restarted process does.
            self.assertTrue(reserve_daily_attempt(db, "2026-11-06", 10, **opts))
            self.assertFalse(reserve_daily_attempt(db, "2026-11-06", 10, **opts))
            with sqlite3.connect(db) as conn:
                self.assertEqual(conn.execute("SELECT attempts FROM quota").fetchone()[0], 2)
                self.assertEqual(conn.execute("SELECT reserved_microusd FROM reserved_budget").fetchone()[0], 80)

    def test_new_utc_day_has_independent_quota_and_old_day_is_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            db = str(Path(directory) / "quota.sqlite3")
            opts = dict(profile=1, profile_limit=1, cost_limit=30, cost_reserve=30)
            self.assertTrue(reserve_daily_attempt(db, "2026-11-06", 1, **opts))
            self.assertFalse(reserve_daily_attempt(db, "2026-11-06", 1, **opts))
            self.assertTrue(reserve_daily_attempt(db, "2026-11-07", 1, **opts))
            self.assertFalse(reserve_daily_attempt(db, "2026-11-07", 1, **opts))
            with sqlite3.connect(db) as conn:
                for table, column, expected in (
                    ("quota", "attempts", 1),
                    ("profile_quota", "attempts", 1),
                    ("reserved_budget", "reserved_microusd", 30),
                ):
                    values = conn.execute(
                        f"SELECT day, {column} FROM {table} ORDER BY day"
                    ).fetchall()
                    self.assertEqual(values, [("2026-11-06", expected), ("2026-11-07", expected)])


if __name__ == "__main__":
    unittest.main()
