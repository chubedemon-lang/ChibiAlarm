# R1 inventory durability — Room v1 → v2

## What changed
- RPG Room schema is now **version 2**, with `rpg_item_stack` (itemId primary key, quantity, updatedAt) and `rpg_item_receipt` (dedupeKey primary key, itemId, quantity, createdAt).
- Both the legacy guest database (`chibi_rpg.db`) and opaque, per-verified-account databases register `MIGRATION_1_2`. No destructive fallback is enabled.
- Existing character, XP ledger, quests, achievements, streak history and legacy SharedPreferences are not modified by this migration.
- On RPG dashboard initialization, a **single** starter `camp_map` is seeded per database via a durable receipt. Reopening, refreshing and retrying do not mint more.
- `RpgInventoryEngine.grant` atomically inserts a receipt and increments a stack; a duplicate receipt returns false without granting anything. `consume` atomically checks the balance and decrements only when sufficient.
- The backpack shows both existing ledger-derived keepsakes (display only) and the new durable item stacks. RPG-only JSON export now includes an additive `itemStacks` array, without grant receipts or account IDs.

## Scope and safety
This is a **local inventory foundation**, not an economy, shop, crafting system, anti-cheat service or paid entitlement. Only trusted local gameplay code should call `grant`. Item consumption is not exposed in the UI yet. The app does not provide authenticated login, verified server ownership or cross-device synchronization. Do not infer ownership from a user-entered nickname.

## Verification gates
- Robolectric tests cover idempotent starter grant, duplicate receipt rejection, concurrent grants, consumption underflow, invalid inputs, database reopen and v1 → v2 migration from a populated old schema.
- Account isolation tests ensure stacks do not leak across per-account/guest databases, and erasure is scoped.
- CI green is necessary but not sufficient. Before any public beta, perform **real installed v1.3 → R1 v2 upgrade tests** on populated and empty user data without uninstall, plus Android 16 alarm, Doze, lockscreen, backup/restore and process-death checks.
- Do not merge the draft PR or publish a release without separate user approval.
