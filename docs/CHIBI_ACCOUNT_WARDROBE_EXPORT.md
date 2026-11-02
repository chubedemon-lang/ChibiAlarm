# Chibi Life 3.0 — wardrobe isolation and export (R1 beta)

## Delivered in this branch
- Guest wardrobe remains in `chibi_wardrobe_guest`; no guest data is moved or reset.
- `ChibiWardrobeStore.forVerifiedAccount(context, verifiedSubject)` isolates cosmetic selection into a SHA-256-derived preference namespace using the **same verified subject validation** as the separate Room account database.
- `deleteVerifiedAccount` for wardrobe clears only the named verified account's cosmetic preferences. `RpgDatabase.deleteVerifiedAccount` separately clears only that account's RPG database. Neither erases Fitness, Focus, alarms, AI history or the guest profile.
- A wardrobe dialog lists all currently defined cosmetics, clearly marks locked levels and rejects equip attempts below the unlock level. Entitlements are always recomputed from character level; preference values cannot grant items.
- Explicit RPG JSON export now includes the current, resolved guest `cosmetics.equippedOutfit` as an additive optional field; legacy `RpgDataExport.create(db)` remains valid. Export still does **not** contain alarm data, Gemini keys, coach chats or the rest of legacy preferences.
- No schema version bump, destructive Room migration, external assets, server credentials or new network permissions.
- Verified identity namespaces now reject malformed UTF-16 (lone surrogate code units) before UTF-8 hashing, as well as invisible Unicode FORMAT/bidi controls. This prevents two distinct malformed identity strings from silently aliasing one account database or wardrobe namespace. Valid supplementary Unicode pairs remain supported; existing well-formed account filenames remain unchanged.

## Important boundaries
This is a **storage primitive**, not production multi-user support. The current `RpgActivity` remains a guest dashboard and does not accept an untrusted user identifier. A future authentication layer must provide a verified issuer-qualified subject and route *every* module (RPG, cosmetics, Fitness, Focus, alarms, coaching and export/delete) through the correct identity. Do not infer identity from a display name, email typed in the app or unsigned JWT.

The JSON export is **not** a restorable backup: import, account switching, cloud sync and complete user data erasure are still unimplemented. Existing exports are not retroactively changed. The optional outfit field is a snapshot of the guest's currently eligible cosmetic; raw preferences are never exported.

## Verification
- Robolectric tests cover guest/verified-account separation, independent selection, persistence, tamper/level checks and scoped erasure.
- Existing Room tests cover RPG account data isolation, and export tests cover the optional cosmetic field plus private-data exclusions.
- Android 16 real-device smoke tests, installed v1.3 upgrade with populated data, production backend, account login, privacy policy and signed release remain blockers.
- Keep PR #1 draft; do not merge to main or publish without explicit approval.
