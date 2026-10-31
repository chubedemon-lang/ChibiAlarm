# Chibi Life 3.0 — R1 implementation and public-release gates

## Shipped on the R1 feature branch (not merged)
- Room database stores local character, daily quests, reward ledger, achievements and streak history.
- Offline RPG screen is reachable from Home. Daily routine quest requires an actual legacy routine check-in; Focus quests use persisted daily completed-session counters; Fitness quests use local workout journal entries (which users may enter manually); Growth quests use completed habit check-ins.
- XP rewards are claimed separately and are protected by a unique ledger key and a Room transaction.
- Daily Chibi check-in awards **15 permanent RPG EXP and 3 coins** once per local calendar date. The reward ledger rejects duplicate claims, including rapid double-taps. This is an offline personal incentive, **not an anti-cheat or server-authoritative economy**; users can change device time.
- Expanded achievements include 7-day habit streak, 10 distinct claimed quests and level 10. Existing ledger/EXP history backfills new milestone progress on dashboard initialization without re-awarding EXP or resetting unlocked achievements.
- On first RPG entry, a snapshot of the existing 90-day routine XP is copied into RPG EXP once. Existing SharedPreferences are never cleared or rewritten by this migration.
- Growth check-ins are imported without modifying legacy preferences and re-synchronized on each RPG dashboard refresh, including unchecked entries. This avoids stale streaks after a user unchecks a habit. The migration ledger marker remains idempotent.
- Fitness quest progress is now reversible before reward claim: deleting a manually logged workout removes unpaid completion. A reward already claimed remains immutable; no server-side verification of workouts exists. A Room regression test covers this behavior.
- Growth check-in state is synchronized when the RPG dashboard opens/resumes, not continuously in the background. Legacy XP on Home is still a rolling 90-day metric, whereas RPG EXP is a persistent separate balance; they must be labeled distinctly.
- Imported XP immediately unlocks the level-5 achievement if the threshold is met. The RPG dashboard displays permanent XP progress to the next level and the current Growth streak.
- Android Auto Backup and Android 12+ cloud/device-transfer rules exclude both encrypted key stores (`gemini_secure` and `gemini_vault`), `coach_history` and `settings` SharedPreferences. This protects encrypted user API keys and local coach chat from backup/transfer, while leaving other existing data eligible for Android backup. Users must re-enter their personal Gemini key after moving devices.
- JVM unit tests cover deterministic level/streak/progress rules. Robolectric/Room integration tests now exercise one-time XP import, atomic claim idempotence and Growth uncheck synchronization. These still do not replace on-device alarm and migration testing. GitHub Actions compile and unit tests do not prove alarm reliability or production readiness.

## R1 reliability and history scalability (latest CI must be rechecked after edits)
- RPG dashboard queries only the current local-date quest period and checks daily login by unique ledger key. Historical quests and reward ledger remain stored and exportable; the dashboard no longer loads every historical quest and ledger entry on each refresh.
- Achievement quest-claim counts use an aggregate SQLite query rather than reading the entire ledger. Robolectric tests cover period filtering and exclusion of legacy imports/daily check-ins from claimed-quest totals.
- Alarm and Focus scheduling now catch a race where Android revokes exact-alarm permission between the permission check and scheduling call. They fall back to inexact scheduling rather than crashing. This fallback **can fire late** and does not establish Android 16 device reliability.
- The existing Room schema remains version 1; these are DAO-only queries, not a database schema migration. Do not change the entity schema without an explicit migration.
- Latest edits require a fresh green CI run before distributing their APK. Hardware testing remains outstanding.

## Multi-user architecture (planned, NOT shipped)
1. Offline-first: keep personal alarm/routine/fitness data in local app storage, one Android application sandbox per installation.
2. Account sync is optional and opt-in; when implemented, require authenticated user IDs and server-side authorization for every record. Never accept a user ID alone as authorization.
3. Gemini: use a backend gateway, not an embedded project key in the APK. Authenticate requests, validate prompts and input size, apply per-user rate limits, global budgets, model allowlists and spend alerts. Never log API keys, voice samples, private journals or full prompts by default.
4. Support an offline fallback when network/AI quotas fail. Make cloud AI features clearly optional.
5. Publish a privacy policy and data deletion/export mechanism; implement data retention, consent for voice/video uploads, and age-appropriate controls before public release.
6. Sign release APK/AAB with a protected release key. Debug APKs are test builds, not Play Store releases.

## Release verification checklist
- [ ] CI passes on latest commit (tests + assembleDebug).
- [ ] Device tests: Android 16 locked-screen alarms, exact alarm permission, Doze, reboot, time-zone changes, notification permission, OEM battery restrictions.
- [ ] Verify migration from actual v1.3 installs with populated and empty Growth/Goals/Routine data; uninstall must not be required.
- [x] Add Room/SQLite integration tests for core R1 transactions (pending CI verification).
- [ ] Add Room schema migration tests before any schema version bump. Do not use destructive migrations.
- [x] Integrate Focus daily completion counts and Fitness workout journal entries with quests (local-only).
- [x] Synchronize Growth check/uncheck state on RPG refresh, expose the streak in the UI and add a daily Growth quest.
- [ ] Validate workouts beyond self-reported journal entries, if necessary for future competitive rewards.
- [x] Distinguish legacy rolling XP from permanent RPG EXP in the RPG UI; one-time import uses a unique ledger key.
- [ ] Audit cross-screen XP wording and migrate any future live XP sources without double counting.
- [ ] Add account and secure backend before claiming cross-device sync or shared-user service.
- [x] Exclude Gemini BYOK ciphertext, coach chat history and settings from Android backup and device transfer.
- [x] Add Robolectric test checking both encrypted Gemini key stores and private chat/settings exclusions for legacy backup, cloud backup and device transfer.
- [ ] Complete security/privacy review for existing direct Gemini BYOK and voice recording features; production public release must use a backend gateway for any project-owned key.
- [ ] Accessibility, localization, offline UX, battery usage, crash reporting with privacy controls.
- [ ] Beta testing and signed release artifact.

This document records implementation truth, not a claim of 100% completion.


### RPG data portability (R1)

The RPG screen now offers a **user-initiated JSON export** through Android's system document picker. It includes character, quests, achievements, streak days and XP ledger, but excludes legacy alarm preferences, Gemini credentials and coach conversations. Exported JSON can contain sensitive habit information; users choose where to save it. Import, account sync, full data deletion and production privacy policy remain separate review gates. Both encrypted Gemini key preference stores (`gemini_secure` and `gemini_vault`) are excluded from Android backup and device transfer.
