# Chibi Life — Gemini proxy (private development only)

**Not production-ready.** This Python 3.10+ standard-library proxy is for personal testing of Gemini-generated Vietnamese alarm text. It is **not** a multi-user identity service. Its shared bearer token **must never be embedded in a publicly distributed APK**. Legacy direct-client/BYOK Android flows require a separate migration.

Gemini Pro consumer subscriptions do not automatically include paid Gemini API access. Create keys and check billing at https://aistudio.google.com/apikey. Five optional profiles are manually selected; the server does **not** rotate keys to evade provider limits.

## Private configuration

Set environment variables on your own machine (never commit or paste secrets into chat):

- GEMINI_API_KEY_1 through GEMINI_API_KEY_5, or GEMINI_API_KEY as profile 1. Missing profile numbers are not silently remapped.
- APP_ACCESS_TOKEN: random private token at least 24 characters long. The proxy refuses to start without it.
- GEMINI_MODEL: optional, default gemini-2.5-flash; allowlisted gemini-2.5-flash or gemini-2.5-pro.
- GEMINI_DAILY_REQUEST_LIMIT: optional global attempt ceiling (default 50 per UTC day, 1–10000). Failed upstream calls also count.
- GEMINI_DAILY_PROFILE_LIMIT: optional per-configured-key-profile attempt ceiling (default 20 per UTC day, 1–10000). Global quota still applies across profiles. Profiles are **not users**.
- GEMINI_MAX_CONCURRENT: optional concurrent upstream call ceiling (default 2, 1–16).
- GEMINI_QUOTA_DB: optional persistent SQLite quota path; default backend/gemini_quota.sqlite3 (excluded from Git). Keep this file private.

For local Windows PowerShell testing:

~~~powershell
$env:GEMINI_API_KEY_1 = Read-Host "Gemini API key 1"
$env:APP_ACCESS_TOKEN = Read-Host "Private development access token (24+ chars)"
$env:GEMINI_DAILY_REQUEST_LIMIT = "50"
python backend/gemini_proxy.py
~~~

The server binds to 127.0.0.1:8765 only. Remote testing requires an HTTPS reverse proxy, network restrictions and secure credential handling. **Do not expose this private development proxy publicly.**

## API contract

POST /generate with Content-Type: application/json, Authorization: Bearer <APP_ACCESS_TOKEN>, and JSON {"topic":"...","profile":1}. Topic is at most 400 characters; profile must be a configured number 1–5. Success: {"message":"..."} (up to 500 characters). Errors return {"error":"..."} with HTTP error status. GET /healthz is an unauthenticated liveness check with no user data.

The server uses constant-time token comparison, fails closed without credentials, bounds JSON requests and Gemini responses, allowlists models, applies a 25-second upstream timeout, avoids prompt/token logging, limits concurrent requests, and atomically persists **global and per-profile request-attempt quotas** in SQLite across restarts. Rejected requests consume neither quota. HTTP 429 indicates a busy or exhausted proxy. This quota is **not** a monetary spend cap; configure billing safeguards and provider alerts separately.

## Offline tests

~~~bash
python -m unittest discover -s backend -p 'test_*.py' -v
~~~

Tests mock Gemini and require no network or real credentials. The Android APK workflow also runs these tests.

## Public release blockers

Replace the shared token with verified, expiring per-user authentication (signature, issuer, audience, expiry); add durable per-user quotas, token/spend ceilings, request IDs and deduplication, user-scoped storage/deletion, consent for sending personal information, secret management, and privacy-conscious monitoring. Do not embed a project-owned key or shared backend token in an APK. This proxy is a test harness, not a production backend.
