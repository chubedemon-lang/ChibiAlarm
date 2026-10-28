# Chibi Alarm VIP PRO — implementation roadmap

Research references:
- https://github.com/pepperonas/brutus (MIT): scheduling reliability, challenges, widget, Compose UI
- https://github.com/jay739/wakeharder (MIT): tests, alarm architecture
- https://f-droid.org/en/packages/com.qralarm.app/ : QR/math UX

Do not copy third-party code or assets without checking and retaining license notices.

## P0 — Safety and reliability
- Test and repair exact alarm permission and full-screen intent on Android 16.
- Separate daily alarm IDs from snooze IDs; avoid pending intent collisions.
- Fix one-shot and weekday schedule behavior; handle empty weekday selections.
- Persist all alarm configuration with Room; migrate SharedPreferences.
- Re-register on boot, timezone change, manual time change, and app update.
- Foreground service for playback, audio focus, restore original volume, device-specific battery guidance.
- Automated unit tests for scheduling, recurrence, DST and reboot.

## P1 — Chibi visual system
- Jetpack Compose Material 3, pastel theme, light/dark, responsive layouts.
- Original chibi mascot artwork and expression states (no unlicensed characters).
- Alarm list cards, alarm edit screen, ringing animation, bottom navigation.

## P2 — Alarm functions
- Multiple independent alarms with weekday repeat, enable/disable, copy/edit/delete.
- Per-alarm label, sound, vibration, gradual volume, snooze limit and duration.
- Math, shake and QR challenges, optional combinations, test alarm mode.
- Stopwatch, timer, world clock, bedtime reminder, sleep habit dashboard, home widget.

## P3 — Voice Studio
- Enumerate installed Android TTS voices, language, network requirement, preview.
- Adjustable speed, pitch, volume and wake-up script presets.
- Per-alarm voice, fallback when selected voice is missing or offline.
- Optional licensed cloud voice provider behind user-supplied API key; transparent online/privacy/cost notice.
- Do not claim to provide proprietary CapCut voice library.

## Release gates
- CI compile + unit tests.
- Physical Android 16 validation: screen locked, Doze, reboot, silent/DND, permission denial, overlapping alarms.
- Signed release builds separate from debug; versioned APK in GitHub Releases.
