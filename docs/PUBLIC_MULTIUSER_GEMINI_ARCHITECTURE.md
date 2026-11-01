# Chibi Life 3.0 — public multi-user and Gemini architecture (design only)

> Status: **PUBLIC MULTI-USER BACKEND NOT IMPLEMENTED**. R1 remains a single-device
> offline beta. A separate, private development-only Gemini text proxy exists in
> backend/gemini_proxy.py, but it uses one shared bearer token and is NOT suitable
> for public users. No account identity, cloud sync or production Gemini backend exists.

## Identity and local data

- Keep alarm, Focus, Growth and RPG usable without signing in or a network connection.
- The current Room character ID `local-player` is a **local legacy key**, never a cloud
  account identifier. Preserve it until a tested additive Room migration is available.
- An authenticated cloud account must use the **verified token subject** as its user ID.
  Never accept a user ID from request JSON, query parameters, or exported RPG JSON.
- If supporting multiple signed-in profiles on one device, use an account-scoped encrypted
  data store / distinct Room database per account, with a separate guest store. Switching
  accounts must close prior database handles and cancel observers before opening the next.
- Do not silently upload pre-existing guest habits, coach conversations, workout videos,
  alarms, or private data. Show an explicit migration/consent screen; allow remaining local.
- Preserve old SharedPreferences and `chibi_rpg.db` on upgrades. Add migration tests
  before increasing Room's schema version. Never use destructive migration.

## Sync contract (future)

- The server owns account records. Every row has `owner_uid` and queries enforce
  `owner_uid = authenticated_subject` in the database, not just in UI or API code.
- Mutations carry a unique `clientMutationId`, an entity ID, `updatedAt` and
  `baseVersion`; the server deduplicates mutation IDs within the user's account.
- Maintain a durable local outbox. Acknowledged mutations are removed only after a
  successful server response. Exponential backoff with jitter and connectivity checks.
- Do not sync or overwrite device-specific alarm schedules without an explicit user choice.
- Resolve checkable habits per habit/date; never use last-write-wins for reward ledgers.
  Offline XP and coins are provisional/cosmetic until server verification; the server
  must not trust user-supplied balances or self-reported quest completion for purchases.
- On logout, invalidate tokens and stop background sync; choose whether to retain or
  remove account-scoped local data with clear user consent.

## Gemini backend (future)

```text
Android app --TLS + short-lived auth token--> authenticated API
    --> verify issuer/audience/signature/expiry, apply per-user quotas
    --> redact/minimize prompt data, enforce size & safety limits
    --> server-side Gemini API key in secret manager
    --> Gemini API
```

- **Never** ship a project-owned Gemini key or service-account secret in the APK.
- Apply per-user and global request/minute, token/day and spend/month ceilings. Enforce
  hard cutoffs and bounded response sizes/timeouts; return a graceful offline fallback.
- Protect against replay and duplicate charges with request IDs; add abuse detection and
  authenticated usage accounting. Do not log tokens, raw credentials or sensitive chats.
- Consent before sending personal fitness, health or habit information to a model.
  Offer data export/deletion and clear retention rules.
- Legacy direct-client BYOK functionality is **not** an acceptable shared-key backend.
  Deprecation/replacement requires an explicit migration and real-device regression tests.

## Public release gates

1. Account authentication and verified row-level isolation, including cross-account
   negative tests and local account-switch isolation tests.
2. Additive migration from a populated installed v1.3 app; full export and deletion
   across modules, not only RPG export.
3. Android 16 real-device checks: notification/exact-alarm permissions, Doze,
   lockscreen, reboot, timezone changes, background restrictions and battery settings.
4. Gemini proxy deployed with secret management, abuse quotas, cost controls and
   privacy/consent flows; staging security tests and operational monitoring.
5. Accessibility review, privacy policy, signed release APK/AAB and staged rollout.

No production merge, public release, account rollout or paid Gemini calls are authorized
by this document.

## Private Gemini proxy hardening (2026-11-01)

- The existing private development proxy now rejects missing/weak access tokens at startup and checks every request with a constant-time comparison. It never permits anonymous generation.
- It validates JSON fields, preserves numbered key profiles, bounds payload/output sizes, allowlists models and uses a persistent SQLite **global request-attempt** ceiling plus concurrency limit. Quota files are ignored by Git; nine offline Python regression tests run in the Android CI workflow.
- The private proxy also has an atomic SQLite per-key-profile daily attempt ceiling (default 20), independent of the global ceiling (default 50). Rejected attempts do not burn the other quota; failed upstream attempts count. Profiles are manually configured API keys, **not verified user identities**. This reduces accidental overuse of one profile but does not guarantee a monetary spend cap.
- This is **not** per-user authentication or a hard monetary cost cap. Never distribute the shared token in a public APK, and never treat the private proxy as the production Gemini service.
- Production remains blocked on authenticated per-user quotas, issuer/audience/expiry verification, spend caps, consent and account data isolation.
