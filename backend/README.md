# Gemini VIP PRO — 5 profiles

The five Google Gemini Pro consumer subscriptions do **not** automatically grant Gemini API paid access. Obtain separate API keys in https://aistudio.google.com/apikey and check the quota and billing for each Cloud project.

This project supports up to five manually selected API key profiles for separate authorized use cases. It does **not** automatically rotate accounts to evade 429 quotas. Gemini rate limits apply at the project level, and billing restrictions may be shared across projects.

## Configure backend

Use Python 3.10+ on your own server, with environment variables:

- GEMINI_API_KEY_1 through GEMINI_API_KEY_5 (only keys you have created; omit missing keys)
- APP_ACCESS_TOKEN (long random secret; mandatory when exposing server)
- GEMINI_MODEL (default gemini-2.5-flash)

Example Windows PowerShell (set your own secrets locally, do not commit them):

```powershell
$env:GEMINI_API_KEY_1 = Read-Host "Gemini API key 1"
$env:APP_ACCESS_TOKEN = Read-Host "Private app access token"
python backend/gemini_proxy.py
```

Backend listens only on 127.0.0.1:8765. Publish it via your own HTTPS reverse proxy with authentication, access controls and rate limiting. Never expose port 8765 directly or store API keys in the Android APK. The Android app sends `POST /generate` with `Authorization: Bearer <APP_ACCESS_TOKEN>` and JSON `{"topic":"...","profile":1}`. Successful response: `{"message":"..."}`.

In Chibi Alarm: Gemini AI Studio → HTTPS endpoint → app access token → Gemini profile 1–5 → idea → Generate. Save text before the alarm; ringing uses Android TTS offline where available. This implementation does not yet synthesize or cache Gemini TTS audio.

## Limitations
- API billing is separate from Gemini Pro subscriptions.
- The number of selectable profiles does not increase a project's quota.
- 429 and other Gemini API errors are returned; no automatic key rotation.
- The current app saves the backend access token in private SharedPreferences, which is not hardened credential storage. Use a limited-scope personal token, not the Gemini API key.
- Validate the app on an Android 16 device before relying on it as a primary alarm.
