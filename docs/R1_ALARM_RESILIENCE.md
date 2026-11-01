# Alarm restore hardening (R1 beta)

This patch does not change the stored alarm preference schema, alarm IDs, ringtone,
notification channel or exact-alarm permission behavior.

- Existing `hour,minute,weekdayMask` records are parsed with explicit hour
  (0–23), minute (0–59) and mask (0–127) bounds.
- Older two-field `hour,minute` records continue to use all seven days.
- A weekday mask of zero disables the alarm and **cancels its pending broadcast**,
  including an existing snooze. Previously scheduling returned early without
  canceling an already registered alarm.
- Boot/timezone restore skips and cancels invalid records rather than throwing
  `NumberFormatException` and aborting restoration of other alarms.
- Stale broadcasts for deleted/disabled/corrupt alarms do not display a new
  notification. The current stored schedule wins over stale caller arguments.

These checks are local correctness guards, not proof that an Android 16 device
will deliver an exact alarm on time. Physical tests are still required for
Doze, battery restrictions, reboot, timezone changes, notification permission
and full-screen-intent behavior. Keep PR #1 draft until approved.
