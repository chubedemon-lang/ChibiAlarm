"""Offline concurrency regressions for the development proxy's durable quotas.

These tests intentionally use temporary SQLite files and never contact Gemini.
They verify that simultaneous workers cannot overspend the configured
request or estimated-cost reservation ceilings.
"""
import concurrent.futures
import os
import sqlite3
import tempfile
import unittest

from gemini_proxy import reserve_daily_attempt


class AtomicQuotaTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = os.path.join(self.temp.name, "quota.sqlite3")
        self.day = "2026-11-06"

    def reserve(self, profile=1):
        return reserve_daily_attempt(
            self.path, self.day, limit=100, profile=profile,
            profile_limit=100, cost_limit=350, cost_reserve=50,
        )

    def test_concurrent_cost_ceiling_is_atomic_and_persistent(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            outcomes = list(pool.map(lambda _: self.reserve(), range(48)))
        self.assertEqual(7, sum(outcomes))
        self.assertFalse(self.reserve())
        with sqlite3.connect(self.path) as conn:
            self.assertEqual(7, conn.execute(
                "SELECT attempts FROM quota WHERE day=?", (self.day,)
            ).fetchone()[0])
            self.assertEqual(350, conn.execute(
                "SELECT reserved_microusd FROM reserved_budget WHERE day=?", (self.day,)
            ).fetchone()[0])

    def test_profile_limit_does_not_consume_global_or_budget_on_rejection(self):
        for _ in range(2):
            self.assertTrue(reserve_daily_attempt(
                self.path, self.day, limit=10, profile=1,
                profile_limit=2, cost_limit=500, cost_reserve=50))
        self.assertFalse(reserve_daily_attempt(
            self.path, self.day, limit=10, profile=1,
            profile_limit=2, cost_limit=500, cost_reserve=50))
        self.assertTrue(reserve_daily_attempt(
            self.path, self.day, limit=10, profile=2,
            profile_limit=2, cost_limit=500, cost_reserve=50))
        with sqlite3.connect(self.path) as conn:
            self.assertEqual(3, conn.execute(
                "SELECT attempts FROM quota WHERE day=?", (self.day,)
            ).fetchone()[0])
            self.assertEqual(150, conn.execute(
                "SELECT reserved_microusd FROM reserved_budget WHERE day=?", (self.day,)
            ).fetchone()[0])

    def test_new_day_starts_fresh_without_resetting_previous_ledger(self):
        self.assertTrue(reserve_daily_attempt(
            self.path, self.day, limit=1, cost_limit=50, cost_reserve=50))
        self.assertFalse(reserve_daily_attempt(
            self.path, self.day, limit=1, cost_limit=50, cost_reserve=50))
        next_day = "2026-11-07"
        self.assertTrue(reserve_daily_attempt(
            self.path, next_day, limit=1, cost_limit=50, cost_reserve=50))
        with sqlite3.connect(self.path) as conn:
            self.assertEqual(2, conn.execute("SELECT COUNT(*) FROM quota").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
