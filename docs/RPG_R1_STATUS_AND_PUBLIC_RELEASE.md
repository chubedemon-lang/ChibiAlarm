# Chibi Life 3.0 — R1 implementation and public-release gates

## Shipped on the R1 feature branch (not merged)
- Room database stores local character, daily quests, reward ledger, achievements and streak history.
- Offline RPG screen is reachable from Home. Daily routine quest requires an actual legacy routine check-in; Focus/Fitness quests currently accept self-reported completion.
- XP rewards are claimed separately and are protected by a unique ledger key and a Room transaction.
- On first RPG entry, a snapshot of the existing 90-day routine XP is copied into RPG EXP once. Existing SharedPreferences are never cleared or rewritten by this migration.
- Historical completed Growth check-ins are imported once to RPG streak history; Growth preferences remain intact.
- The migration is **not** live bidirectional sync: subsequent Growth changes are not yet mirrored, and legacy XP on Home is still a rolling 90-day metric. RPG EXP is a persistent separate balance.
- Android unit tests cover deterministic level/streak/progress rules. GitHub Actions compile and unit tests do not prove alarm reliability or production readiness.

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
- [ ] Add Room migration tests before any schema version bump. Do not use destructive migrations.
- [ ] Integrate actual Focus and Fitness completion events with quests; remove manual trust where possible.
- [ ] Avoid XP double counting and reconcile legacy rolling XP vs RPG permanent EXP in the UI.
- [ ] Add account and secure backend before claiming cross-device sync or shared-user service.
- [ ] Security/privacy review for existing direct Gemini BYOK and voice recording features.
- [ ] Accessibility, localization, offline UX, battery usage, crash reporting with privacy controls.
- [ ] Beta testing and signed release artifact.

This document records implementation truth, not a claim of 100% completion.
