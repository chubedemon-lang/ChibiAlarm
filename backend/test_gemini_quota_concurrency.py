"""Concurrency regressions for the SQLite Gemini quota reservation.

Run: python -m unittest discover -s backend -p 'test_*.py'
"""
import concurrent.futures
import sqlite3
import tempfile
import unittest
from pathlib import Path

try:
    from backend.gemini_proxy import reserve_daily_attempt
except ModuleNotFoundError:
    from gemini_proxy import reserve_daily_attempt


class ConcurrentQuotaTests(unittest.TestCase):
    def test_parallel_requests_never_exceed_global_profile_or_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            db = str(Path(directory) / "quota.sqlite3")
            def attempt(_):
                return reserve_daily_attempt(
                    db, "2026-11-06", 12, profile=1, profile_limit=9,
                    cost_limit=350, cost_reserve=50)
            with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
                results = list(pool.map(attempt, range(40)))
            self.assertEqual(sum(results), 7)
            with sqlite3.connect(db) as conn:
                self.assertEqual(conn.execute(
                    "SELECT attempts FROM quota WHERE day=?", ("2026-11-06",)
                ).fetchone()[0], 7)
                self.assertEqual(conn.execute(
                    "SELECT attempts FROM profile_quota WHERE day=? AND profile=?",
                    ("2026-11-06", 1)).fetchone()[0], 7)
                self.assertEqual(conn.execute(
                    "SELECT reserved_microusd FROM reserved_budget WHERE day=?",
                    ("2026-11-06",)).fetchone()[0], 350)

    def test_rejected_profile_does_not_consume_global_quota(self):
        with tempfile.TemporaryDirectory() as directory:
            db = str(Path(directory) / "quota.sqlite3")
            kwargs = dict(cost_limit=500, cost_reserve=50, profile_limit=1)
            self.assertTrue(reserve_daily_attempt(db, "2026-11-06", 5, profile=1, **kwargs))
            self.assertFalse(reserve_daily_attempt(db, "2026-11-06", 5, profile=1, **kwargs))
            self.assertTrue(reserve_daily_attempt(db, "2026-11-06", 5, profile=2, **kwargs))
            with sqlite3.connect(db) as conn:
                self.assertEqual(conn.execute("SELECT attempts FROM quota").fetchone()[0], 2)


if __name__ == "__main__":
    unittest.main()
