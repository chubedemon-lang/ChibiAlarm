# Chibi Life 3.0 — R1 Foundation

R1 adds an **isolated** Room database `chibi_rpg.db`, without changing the existing alarm and routine SharedPreferences. This is deliberately additive: no migration of legacy alarms or habits is performed yet.

## Implemented
- Character persistence, quests, XP ledger, achievements and streak-day records.
- Deterministic level thresholds and streak rules.
- Transactional quest reward claim with unique ledger key.
- JVM unit tests for level thresholds and streaks.
- CI test execution before APK assembly.

## Not yet implemented
- UI integration, quest creation/check-in UI, character customization, stat progression, AI campaign generation, achievement unlock logic, inventory.
- Legacy GrowthActivity import and migration tests; no legacy data is deleted.
- On-device Android 16 smoke test, Doze and reboot acceptance tests.
- Database schema migration for future RPG versions (database currently version 1).

## Important
Do not use destructive migration. Introduce explicit Room migrations for all future schema changes. Do not claim the RPG features are visible in the APK until a UI integration PR is completed.
