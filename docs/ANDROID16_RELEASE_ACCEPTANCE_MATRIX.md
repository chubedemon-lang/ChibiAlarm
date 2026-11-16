# Chibi Life 3.0 — Android 16 and legacy upgrade acceptance matrix

Status: manual verification plan, **not evidence that tests passed**. Run on a physical Android 16 phone and keep screenshots/logcat with secrets and personal content redacted. Never uninstall the existing v1.3 app before upgrade testing.

## Data preservation and account boundaries

| Case | Setup / action | Required outcome |
| --- | --- | --- |
| U01 | Install v1.3; create alarms, routines, Growth check-ins, Focus sessions and Fitness logs; update in place to R1 | Existing data remains readable and alarms are scheduled; no destructive migration or duplicate XP grant |
| U02 | Repeat U01 with an empty v1.3 profile | No crash or unexpected starter reward duplication |
| U03 | Force-stop, reboot and reopen after upgrade | RPG inventory, quest ledger and preferences persist |
| U04 | Claim one daily quest rapidly twice and reopen | Exactly one reward receipt, no duplicate EXP/coins |
| U05 | Toggle an unclaimed Growth check-in off, then reopen | Unpaid quest credit is revoked; past paid rewards remain immutable |
| U06 | If verified account switching is ever enabled: create data in account A, switch to B, then back to A | No A data visible in B across RPG, Alarm, Growth, Focus, Fitness, Coach, backups or exports |
| U07 | Export RPG JSON via system picker | Only expected RPG fields; no API key, chat, alarm or private account credential |

**Blocker:** U06 cannot pass as a production multi-user acceptance test until all modules are scoped to authenticated identities. A guest-only local installation is not proof of account isolation.

## Alarm, Focus, fitness and UI

| Case | Setup / action | Required outcome |
| --- | --- | --- |
| A01 | Grant exact-alarm and notification permissions; schedule a near-term alarm | Alarm fires with correct notification and user-visible controls |
| A02 | Revoke exact-alarm permission between scheduling and firing | No crash; UI clearly communicates potential delay for inexact fallback |
| A03 | Lock screen and enter Doze; test both permission states | Observe actual timing and document OEM restrictions; no unverified exactness claims |
| A04 | Reboot with a pending alarm | Expected alarm recovery after boot and user unlock |
| A05 | Change time zone and DST-relevant dates | No duplicate or silently lost alarms; correct local display |
| F01 | Complete Focus session with rapid repeated completion events | Exactly one completed session and quest credit |
| F02 | Create then delete a Fitness journal entry before claiming | Unpaid quest progress returns to incomplete |
| UI01 | Walk between scenes, rotate screen, background and restore process | Character location/scene recover; no duplicated rewards |
| UI02 | Enable large font, TalkBack and reduced motion | Buttons are reachable, labels meaningful and reduced motion respected |
| UI03 | Run offline, with network errors and limited battery | Core RPG/alarms remain usable without Gemini |

## Gemini and public-release blockers

| Case | Required outcome |
| --- | --- |
| G01 | No project-owned Gemini secret, shared backend bearer token, signing key or credential in APK |
| G02 | Authenticated backend checks server-side user identity on every request; client-provided user ID is never authority |
| G03 | Per-user quota, global concurrency, model allowlist and durable budget reservations survive restarts |
| G04 | HTTP 429, timeout, upstream failure and offline mode have safe user-facing fallback |
| G05 | Consent, retention, export/delete and privacy policy reviewed before public rollout |

## Evidence to record

For each case record build SHA, APK artifact ID, device model, Android build, permission state, steps, expected/actual result, pass/fail, sanitized logcat and tester initials. Keep sensitive journal/voice content and credentials out of issue attachments.

**Release rule:** a green GitHub Actions build is necessary but not sufficient. Keep PR Draft and do not merge, sign a public release, or advertise multi-user safety until all relevant gates pass and the owner explicitly approves.
