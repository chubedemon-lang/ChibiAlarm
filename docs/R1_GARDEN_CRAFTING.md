# Chibi Life R1 — Offline garden and crafting (experimental)

This is a small, non-monetized gameplay loop built on the existing Room v2 inventory.
It does **not** modify the alarm, Fitness, Focus or legacy preference schemas.

## Player flow

1. Open the RPG dashboard, enter the garden and water a planter (or use the accessible "Tưới cây" button).
2. The first successful watering each **local calendar date** grants one item: either a herb or a star fragment. The type is deterministic by epoch-day parity, not random.
3. The inventory receipt `garden:forage:YYYY-MM-DD` makes repeated taps, app restarts and concurrent calls idempotent.
4. When the backpack contains at least one of each ingredient, the "Chế tạo Bùa khu vườn" button crafts one garden charm. Crafting costs one herb and one star fragment.
5. All ingredient debits, craft receipt and charm credit happen inside **one SQLite transaction**. Failed crafts consume nothing. Repeated requests with the same receipt cannot duplicate a charm.
6. The player can consume one crafted charm per local day to gain +20 EXP and +1 coin. A second attempt that day is rejected without consuming an additional charm.

## Boundaries

- The charm is a consumable local gameplay item, not a paid entitlement. Players may activate **one charm per local calendar day** for +20 EXP and +1 in-game coin. The transaction atomically debits one charm, inserts a unique ledger receipt and credits the character; retries and competing taps cannot double-award. Failed or overflowing activations roll back all changes. No purchases, ads, network calls or server entitlements are issued.
- All changes live in the currently selected Room database. The current dashboard is still guest-only; authenticated account login and isolation across **all** app modules are **not** implemented.
- Offline device clock manipulation can influence available local days. This is acceptable for the prototype but **not** sufficient for a server-authoritative economy.
- Craft receipts are generated for each user action. The UI disables the craft button until refresh; the Room transaction remains the authority.
- Existing inventory export includes charm stacks automatically. Import/restore is not yet implemented.
- Garden rewards are not account sync, and the local debug APK is not approved for public release.

## Verification

- Robolectric Room tests cover daily duplicate prevention, alternating drops, missing ingredients, atomic crafting, repeated receipts, concurrent competing crafts, invalid receipt keys, and atomic daily charm activation including overflow and concurrent calls.
- Robolectric view test checks accessible watering is only available in the garden and shares the same action as tapping the planter.
- CI should run backend security tests, Android unit/Robolectric tests and `assembleDebug` before using any APK artifact.
- Still required: real installed v1.3 upgrade, Android 16 hardware alarms/Doze/reboot, full export/delete, authenticated multi-user isolation, privacy review and production Gemini backend with user quotas and cost limits.

**Release gate:** Keep PR #1 draft. Do not merge or publish without explicit user approval.
