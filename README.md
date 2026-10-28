# 🐰 Chibi Life VIP PRO v1.2

Android **native Kotlin** personal alarm + daily routine + AI assistant. Build on GitHub Actions; **no VPS or PC needed**.

## Main modules

| Feature | v1.2 functionality |
|---|---|
| ⏰ Alarms | Set repeating alarms; ring screen with math challenge, snooze, ringtone chooser, fade-in, exact/full-screen permission setup |
| 🎙️ Voice Studio | Available Vietnamese Android TTS voices, one-tap preview/stop, voice selection, pitch and speed, install voice data |
| 🎧 Gemini AI Voice | Select from 30 Gemini studio voice names, create spoken WAV using user key, preview and save locally for **offline alarm playback** |
| 🗓️ Routines | Custom title, start time, duration, weekdays, daily reminders, check-in and daily progress |
| 🎯 Challenges | Multiple 30/90-day goals, daily check-ins, progress and XP |
| 🤖 Gemini Coach | Chat in Vietnamese and draft 30/90-day routines. **Explicit user approval** required to save AI proposals |
| 🍅 Focus | 15/25/45/60-minute Pomodoro with persistent finish reminder |
| 🏃 Workout | Manual workout diary, last-7-day chart and plain-text CSV export |
| 🌱 Habits | Daily habits checklist and 7-day history |
| 🧩 Home widget | Today progress and next uncompleted routine |
| 🔑 BYOK | Each user enters a Gemini API key locally, encrypted using Android Keystore |

**Version status:** v1.2 development release; successful GitHub build is **not** equivalent to real-world Android 16 acceptance testing. Confirm exact alarms/full-screen permissions and test actual ringing.

## Build APK (phone only)

1. Open [GitHub Actions](https://github.com/chubedemon-lang/ChibiAlarm/actions/workflows/build-apk.yml).
2. Select most recent green **Success** run for the latest commit.
3. Download artifact named `ChibiLife-v1.2-Full-APK`.
4. Unzip, then install `app-debug.apk` on Android. This is a debug-signed testing package.

## Getting started

1. Home → **Thiết lập báo thức** → enable notifications, exact alarms and full-screen when available; run test ring.
2. Open **Voice Studio** to install/preview Vietnamese TTS voices.
3. Open **Lịch trình thông minh** to add hourly routines, then mark completed activities.
4. Open **Mục tiêu** for 30/90-day challenges.
5. For Gemini: get a key at https://aistudio.google.com/apikey; Home → **Gemini AI Studio** → enter key. Gemini Life Coach only writes a proposed schedule after you tap **Đồng ý thêm lịch**.
6. For advanced cloud voice: open **Gemini Voice PRO**, choose a voice/style, generate and preview, then press **Dùng âm thanh này làm chuông**. AI file is stored on-device, so alarm playback does not require connectivity after generation.

## Important limits and privacy

- Gemini text and TTS availability, model access, usage limits and billing are **per Google API key/project**. A Gemini Pro consumer subscription alone does not automatically provide API access.
- Voice Studio uses local Android TTS; the 30 **Gemini voice names** require Google Gemini TTS API access and active internet for generation. This is not 30 installed offline voices.
- Generated AI voice file plays a fixed recorded phrase. If you edit the alarm text, regenerate its audio.
- Workout journal is manual. **Health Connect automatic data sync is not implemented** and should not be claimed as active.
- UI uses native Android controls with pastel styling and vector launcher/widget artwork; **not** a full Jetpack Compose Material 3 migration.
- No third-party backend, login or VPS is required. Requests go directly from phone to Google's API, so a user-controlled API key remains a valuable credential even though encrypted at rest.
- Local data has no cloud backup in this version. The app doesn't use or upload medical records.
- Android/OEM battery policies can delay non-exact reminders. Exact alarms, full-screen notifications and TTS availability depend on system permissions and installed speech engine.
- Test alarms under locked screen, Doze/battery saver, restart, and time-zone changes before relying on them for critical wakeups.

## Developer

Android Gradle plugin / Kotlin application; GitHub Actions builds with JDK 17, Android SDK 35 and Gradle 8.11.1. See `.github/workflows/build-apk.yml`.

Documentation:
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/reference/android/speech/tts/TextToSpeech
- https://developer.android.com/develop/ui/views/appwidgets
- https://ai.google.dev/gemini-api/docs
- https://ai.google.dev/gemini-api/docs/speech-generation

License: This repository is owned and maintained by the author. External services and Android tools are subject to their own licenses.
