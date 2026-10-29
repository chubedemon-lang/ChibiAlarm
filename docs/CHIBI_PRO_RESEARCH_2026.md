# Chibi Life — PRO upgrade research (29 Oct 2026)

## Objective
Build a reliable Android 16-friendly personal fitness and nutrition assistant, fully useful offline with no Gemini quota, plus an optional Vietnamese voice marketplace using authorized engines. Do not call a rule-based offline assistant an on-device LLM.

## Sources and verified capabilities
- WHO adults activity: 150–300 min moderate aerobic weekly (or 75–150 vigorous); strength 2+ days/week: https://www.ncbi.nlm.nih.gov/books/NBK566046/
- ACSM resistance training 2026 position stand, supports individualized accessible programs: https://pmc.ncbi.nlm.nih.gov/articles/PMC12965823/
- CapCut official TTS: Vietnamese among languages, >200 total AI voices, speed/pitch options, but no verified official public embeddable CapCut TTS API: https://www.capcut.com/tools/text-to-speech ; https://www.capcut.com/help/voice-options-missing
- FPT AI Vietnamese TTS API: banmai/lannhi/leminh/myan/thuminh/giahuy/linhsan. Asynchronous file generation: https://docs.fpt.ai/docs/vi/speech/api/text-to-speech.html ; published free tier (subject to change): https://docs.fpt.ai/docs/vi/speech/documentation/tts-pricing/
- Viettel AI TTS 16 documented regional voices, POST synthesis with token, WAV/MP3: https://viettelai.vn/en/tai-lieu
- Android local TextToSpeech engine, system-installed voice discovery: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- HayaiTTS: offline Android system TTS engine with voice downloads; verify vi-VN per installed model: https://github.com/HayaiApp/HayaiTTS
- Supertonic Android: offline on-device TTS alternative; verify Vietnamese per model: https://f-droid.org/en/packages/com.brahmadeo.supertonic.tts/

## Proposed user-facing feature tiers
### Tier A: Always-free offline
- Native Android TTS, filter available vi-VN voices, listen/stop, pitch/rate, choose default, and identify voices requiring network.
- 8 training tracks: beginner strength, bodyweight muscle building, low-impact cardio, mobility/yoga, shadow boxing, walking/endurance, chair-based accessible exercise, recovery.
- Structured workouts with warmup, technique cues, progressions, substitutions, rest timer, sets/reps, effort rating, and pain/stop warnings.
- Food journal: meals, water, dietary preferences, allergies, budget; editable food composition data with transparent source and portions. Do not promise exact calories for unlabeled meals.
- Habit reminders, 7/30/90-day plans, exercise diary, progressive weekly review; no network needed.
- FreeCoach can answer only covered topics; use transparent rule-based labels.

### Tier B: Optional cloud voice
- FPT.AI/Viettel AI connectors with user-owned credentials, voice catalog, sample generation, download-to-device and offline reuse. Credentials encrypted using Android Keystore; never ship a shared API key in APK.
- Provider error handling, rate limiting, consent before transmitting text, cost disclosure, per-provider quota status.
- CapCut-inspired UX for preview and filtering; do NOT scrape CapCut private endpoints, redistribute proprietary voice samples, or imply CapCut integration.

### Tier C: Optional real local model (later)
- Benchmark a legally distributable quantized multilingual on-device model on user's actual Android phone for latency/RAM/storage; use deterministic fitness knowledge retrieval, not free-form medical diagnosis.
- Only ship after licensing, model-size, Vietnamese quality, device heating, and battery tests.

## Safety and acceptance tests
- Personal plans only after explicit confirmation. Ask about fitness experience, time, available space, relevant movement limitations, dietary allergies, and goals; do not demand sensitive health information.
- Default to low-impact beginner intensity. No crash diets, prescriptive medical nutrition, or guaranteed body transformation.
- Validate no internet/offline operation; screen locked alarm, exact alarm permission, app killed, reboot, TTS missing, offline voice fallback, and Android 16 OEM battery restrictions.
- Keep health data local by default, clear export/delete controls; require opt-in before external provider use.

## Current status
Research and architecture proposal. The existing FitnessActivity is a basic 3-level no-equipment program and meal example. The advanced 8-track workouts, food composition database, FPT/Viettel API integrations and embedded on-device LLM are NOT yet implemented.
