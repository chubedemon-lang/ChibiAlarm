# Chibi Life R1 — Offline keepsake backpack

This beta introduces a **read-only keepsake backpack**, not a full RPG inventory,
economy, crafting system or equipment ownership table.

## Data contract

- `Sổ hành trình`: one starter journal, always present.
- `Vé bình minh`: one per unique committed `daily_login:` reward ledger entry.
- `Trái tim tình bạn`: one per unique committed `pet_game:` entry.
- `Huy hiệu nhiệm vụ`: one per committed `quest:` entry.
- Counts are derived from existing Room v1 ledger keys and cannot be spent.
- Unclaimed quests and legacy XP imports do **not** create keepsakes.
- No new tables, destructive migration, permissions, network requests or external assets.
- The optional `keepsakes` JSON export field is derived; older exports without
  it remain valid. The existing ledger is still the authoritative record.
- This is guest-local storage in the current UI; it does not establish login,
  per-account isolation for Fitness/Focus/Alarm or cross-device sync.

## Regression gates

- Pure JVM: starter inventory, zero claims, corrupt negative counts, long counts.
- Robolectric Room: claim idempotency and aggregate queries for login, pet and quests.
- JSON: derived counts, no private Gemini key/chat.
- CI: backend security tests, Android unit/Robolectric, debug APK assembly.

## Before public release

Real Android 16 device verification, v1.3 in-place upgrade, authenticated
multi-user isolation, full data export/erasure, Gemini backend identity and
quota enforcement, privacy review, signed build and explicit release approval
remain mandatory. Do not advertise this backpack as consumable items or a shop.
