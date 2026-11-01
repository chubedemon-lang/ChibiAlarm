# R1 offline RPG account isolation — implementation and limits

Status: **storage foundation only, NOT multi-user release readiness**.

## Implemented

- `RpgDatabase.get(context)` keeps the original `chibi_rpg.db` guest database and all its existing data. No destructive Room migration and no automatic ownership reassignment.
- `RpgDatabase.forVerifiedAccount(context, verifiedSubject)` opens an independent Room database for each **trusted, authenticated subject**. Filenames contain SHA-256 of the exact subject, not the raw subject, display name or email. The subject MUST come from a backend-verified identity token, not from user input.
- The Room schema remains version 1 in each database. XP ledger, daily quests, character, streaks and achievements are scoped to the selected database instance.
- `RpgDatabase.deleteVerifiedAccount(context, verifiedSubject)` explicitly closes and removes only that account's local RPG database. Invoke only after all screens/jobs have stopped using the account DAO. This does **not** delete other app data.
- Robolectric tests check opaque filenames, invalid subjects, account isolation, preservation of the guest database, and erasure of only the selected account.

## Release blockers / privacy risks

1. **No account login or identity verifier exists yet.** Current app UI still uses the legacy guest database. Account-scoped storage cannot be treated as active user separation until every RPG entry point uses a verified subject and switches database on sign-in/sign-out.
2. Fitness, Focus, Alarm, Growth, voice clips and legacy preferences still use existing local storage. They are **not** account-scoped. Never claim that all personal data is isolated or erased.
3. **No guest-to-account migration is performed.** Require an explicit opt-in import with deduplication, rollback and tests; never silently assign legacy XP to the first logged-in account.
4. Android Auto Backup/device transfer may include the Room databases. Before public multi-user launch, define a deliberate encrypted, account-scoped backup and restore policy and test switching accounts, reinstall, device transfer and deletion.
5. A hashed filename reduces accidental disclosure but does **not** encrypt the database. OS sandboxing applies; encrypted storage and export/delete flows require a separate privacy review.
6. On logout, close/clear UI and pending workers, invalidate auth, and ensure widgets/alarms do not show the previous account's data. Account erasure must also cover independent preferences and media.
7. The private Gemini proxy still uses a shared development token and is not a production user identity provider. No project API key or shared backend secret may be embedded in the APK.

## Acceptance criteria before enabling multi-user UI

- Verify server-signed identity, issuer, audience, expiry, revocation and TLS before calling `forVerifiedAccount`.
- Exercise guest -> account A -> logout -> account B -> logout -> account A with separate XP/quests, alarms, fitness and AI history; no cross-account notifications or widgets.
- Verify offline re-entry for the same account, token expiry and secure local session state.
- Test explicit export and deletion of all categories, including cloud state and device backups.
- Test real Android 16 behavior, low memory, process death and interrupted migration.
