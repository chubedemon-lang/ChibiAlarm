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


## Focus timer stale-completion guard

- Each newly scheduled Focus completion carries its expected end timestamp.
  The receiver compares that value with the currently persisted session before
  awarding a completed session or posting a notification.
- A delayed broadcast from an earlier timer cannot complete a newer session.
  Missing timestamps are accepted only for **unchanged legacy sessions** created
  before this marker existed; starting or restoring a session enables strict
  matching for subsequent alarms.
- Focus counters remain in the existing `focus` SharedPreferences; this change
  does not reset historical sessions or change the scheduled request code.
- Tests cover old-session delivery, missing markers, legacy compatibility,
  and single-payment behavior. Android 16 device validation is still required.
- Focus preferences are **not yet isolated by authenticated user**. Do not
  present this as public multi-user readiness; the login/account switch flow
  must explicitly separate or migrate Focus, Fitness and alarm state before
  public release.

## Legacy routine corruption recovery (2026-11-02)

- The routine reader now skips individual malformed JSON entries instead of
  hiding the entire routine list when one row is damaged. Valid entries still
  sort by time; malformed SharedPreferences types and truncated JSON do not
  crash the screen. Reading never rewrites legacy data.
- If a user later saves routines while the original payload is damaged, the
  first damaged payload is retained locally under
  `chibi_life_v12/routines_corrupt_backup` before the editable list is replaced.
  This is a **recovery copy, not an automatic restoration or cloud sync**.
  Subsequent edits do not overwrite the first recovery copy.
- Robolectric regressions cover mixed valid/invalid rows, non-string values,
  read-only recovery, backup-on-save and no backup for valid records.
- The UI does not yet offer a recovery/import screen. A malformed entry may
  remain invisible until a future repair tool; keep real v1.3 upgrade testing
  and export/delete coverage on the release checklist.


## Focus preference and replacement safety (2026-11-02)

- Starting a replacement Focus session now persists the new end time and strict
  alarm marker **before** replacing the old AlarmManager entry. A failed
  preference write no longer cancels the previous timer first.
- Stopping a session cancels its alarm only after the end-time removal commits.
  A failed removal leaves the prior alarm available for retry/recovery.
- Boot restore persists the strict receiver marker before scheduling; malformed
  or non-positive legacy `endTime` values are ignored **without deleting them**.
- Malformed or negative historical session counters fail closed: the timer does
  not award another session or overwrite the damaged value. Users can recover
  after the underlying preference is repaired.
- Robolectric regressions cover replacement without extra rewards, rejection
  of the previous alarm, malformed end time, damaged total/day counters and
  successful retry after a repaired counter.
- These changes do not implement account-scoped Focus data, an authenticated
  backend, or real-device Android 16 validation. No SharedPreferences key,
  alarm request code or Room schema was changed.

## Routine editor durable-save ordering (2026-11-02)

- A routine list edit now uses a synchronous SharedPreferences commit before
  cancelling any removed alarms or installing replacement reminders. Previously
  the code cancelled reminders first and used asynchronous `apply()`, so a
  failed storage write could leave the user's old list with missing alarms.
- A failed commit raises a clear error without changing the previously
  scheduled alarms. The routine editor keeps the add/edit dialog open on a
  failed save and shows a retry message; template and delete actions also
  report failure instead of displaying false success.
- Coach-generated and offline template plans only claim success after their
  routine list has been saved. If the separate goal write fails, the UI
  explicitly reports that the routines may already have been saved.
- Robolectric exercises a deliberately failing preference editor, checking
  that the original JSON and scheduled alarm survive. Existing reminder
  add/delete and stale-broadcast tests remain in CI.
- This is a local durability fix, not a cross-file transaction, account-scoped
  alarm isolation, Android 16 physical timing validation or production readiness.
