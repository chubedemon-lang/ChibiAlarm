# Chibi Life 3.0 — visual production brief (draft)

## Goal
Ship a visually cohesive **offline-first Android 2D life RPG**, not a text dashboard. The existing Android alarm/Focus/Fitness functions and Room data must remain functional during incremental replacement.

## Art direction
- Cozy hand-painted chibi, 3/4 top-down rooms and garden; large readable character, soft pastel lighting, expressive animations.
- Portrait-first phone HUD: top status (level, XP, coins), central interactive scene, bottom navigation (Home, Quests, Inventory, Focus, Fitness).
- Every actionable prop gets visible feedback, large touch targets, accessibility labels, and a no-animation option.
- Do not combine unrelated pixel and painterly packs in one final scene. Placeholder assets must be explicitly marked as placeholders.

## Vertical slice (acceptance criteria)
1. Home: character idle/breathe/blink/walk and tap response; bedroom with bed, desk, door, plant.
2. Tap desk opens Focus; finishing a verified session grants one durable quest progression, not arbitrary tapping XP.
3. Tap training mat opens Fitness; logged workout progress synchronizes once with RPG.
4. Tap bed opens alarm schedule; real alarm flow remains independent of scene rendering.
5. Claim quest produces short XP/coin animation; restart shows the same durable balance and claimed state.
6. Inventory UI with one cosmetic item earned via quest, persisted locally; avoid paid shop until entitlement security exists.
7. No network requirement for the scene or game progress. AI Coach uses authenticated backend only; no key in APK.
8. Android 16 test on a real device is a separate release gate, not implied by green CI.

## Candidate asset sources (verify licensing per file before import)
- Kenney Roguelike/RPG Pack (CC0, tiles and props): https://kenney.nl/assets/roguelike-rpg-pack
- Kenney UI Pack (CC0, scalable UI elements): https://kenney.nl/assets/ui-pack
- Original art commissioned/generated specifically for Chibi Life, with source, license, provenance and review.
- Avoid copying recognizable game characters, screens, maps, logos or proprietary assets.

## Technical rollout
- R1: Kotlin Canvas prototype, one scene and interaction hitboxes; no destructive Room migration.
- R2: replace prototype with consistent optimized PNG/WebP sprite atlases, separate gameplay state from renderer.
- R3: add room-to-garden navigation, animation state machine, cosmetic equipment, quests and accessibility.
- R4: user-scoped storage/auth, safe migration and recovery; test account switching before public release.
- R5: device profiling (frame pacing, memory, battery), alarm reliability, privacy review and production backend.

## CI checks
- Compile + Android tests + backend security tests.
- Resource license manifest and attribution/provenance.
- Unit tests for tap mapping, quest reward idempotence, persistence and account boundaries.
- Report APK artifact only after successful workflow; do not merge main or publish without explicit approval.
