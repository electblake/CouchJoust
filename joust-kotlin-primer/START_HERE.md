# Build a faithful native Kotlin Joust for Android TV

**Read this first, then `docs/JOUST_GAME_SPEC.md`.** Open `OPEN_ME.html` for the illustrated document and browsable graphics/audio catalog.

## Goal

Create a fully playable native Kotlin Android TV / Google TV version of the original **Joust, final revision 4 (1982)**. Faithfulness is the first priority. Use the original decoded graphics and sound in this package. Implement the game rules directly in Kotlin. The source reference is `synamaxmusic/joust` commit `87e22aab945709fa107df609c0efcb839a17c1f9`.

This ZIP is the reverse-engineering primer, decoded assets and implementation references. It is not an APK or a finished clone. It contains the source snapshot and preparation tools needed to resolve the explicitly identified timing/comparison questions.

## Assets first

| Location | Contents |
|---|---|
| `assets/graphics/sprites/` | 101 indexed PNGs: birds, riders, eggs, troll, flames, pterodactyl, death effects, terrain, transporters and debris. |
| `assets/graphics/fonts/` | 99 original glyph PNGs; original font-index maps are in the manifest. |
| `assets/graphics/title-logo.png` | Source-decoded title logo; original paths/palette in `title-line-data.json`. |
| `assets/graphics/asset-manifest.json` | Palette, exact pixel grids, source offsets, 43 authored collision profiles and actor/effect descriptors. |
| `assets/graphics/special-effects.json` | Dissolve/debris pixels and intentional erase masks. |
| `assets/audio/commands/` | 39 source-program command captures: 38 audible sounds plus the silent reset/control probe. |
| `assets/audio/events/` | 37 original scheduled game-event captures. |
| `assets/audio/audio-manifest.json` | All 40 game events, including 3 control-only events; timing, priorities, command schedules, hashes and completion status. |
| `assets/audio/dac-traces/` | 76 original sample-level/timing traces for resampling and transition work. |
| `assets/sound-events.json` | Complete original event sequencing and priority table. |

WAV format: mono, 48 kHz, 16-bit PCM. Captures are rendered from the source-built original sound program; they are not generic replacements or video recordings. Read `assets/audio/README.md` before using whole-event clips: command holds and natural sound duration differ, and arbitrary live history can affect noise phase and interruption transitions.

## Game data

| File | Contents |
|---|---|
| `data/gameplay_tables.json` | All 90 wave records; 28 dynamic difficulty records; 69 egg positions; baiter delays; derived default-difficulty snapshots. |
| `data/waves.csv` | Complete readable wave inventory. Egg rows encode hatch species/capacity, not extra mounted enemies. |
| `data/physics_tables.json` | Exact landing/solid maps, ground states, flight speeds and movement constants. |
| `data/text_catalog.json` | Original phrase codes/text and page layouts; retain font-context distinctions and intentional source spelling. |
| `verification/math-vectors.json` | Source-derived arithmetic acceptance vectors. |

## Rules the implementation must preserve

- Three sampled held inputs per player: left, right, flap. Both directions together are neutral. Holding flap never auto-flaps.
- Ground motion uses 13 authored states and uneven footstep strides. Air motion uses 1/256-pixel arithmetic, nine horizontal speed choices and discrete flap impulses.
- Movement wraps across 303 reference positions. Collision uses separate authored row silhouettes; transparent sprite pixels do not define the universal hit test.
- Keep live actor/task ordering, same-pass insertion rules and immediate collision results. Player contacts precede player movement; normal initial player ordering is P2 then P1.
- Implement bounder, hunter and shadow-lord AI as the recovered state machines. Preserve target delays, pursuit quotas and the source's two-target selection behavior.
- An uncollected rider survives as an egg/standing rider. Waves finish when unresolved riders are gone, with the original cleanup delay. Egg chains, promotion, remounting and the four-defeat budget are required.
- Implement all 90 wave records, then repeat table entries 81–90 independently of the displayed wave number. Include team/survival, gladiator, egg and pterodactyl rules.
- Implement both pterodactyl categories, burning bridges, progressive cliff changes, lava and troll capture/escape.
- Preserve two-player friendly fire, exact award order, the victim's 50-point death award and extra-life checks that can change last-life outcomes.
- Use one logical sound channel with original priority/command sequencing. Keep indexed art for recoloring, original frame offsets, fonts and animations.

The full guide supplies the algorithms, tables, boundaries, state families, source anchors and targeted acceptance cases behind these requirements.

## Build sequence

1. Import the decoded assets and exact data; reproduce the original terrain and component placement.
2. Implement a deterministic native gameplay core, beginning with one controlled bird and the numeric acceptance vectors.
3. Add silhouettes, terrain response, jousting, life/death/respawn and score accounting.
4. Add enemy states, eggs, remounting, waves, difficulty, special enemies and hazards.
5. Complete original presentation, audio, two-player flow, initials, high scores and attract mode.
6. Connect Android TV/Xbox inputs, controller assignment, pause/reconnect and launcher behavior; produce an installable APK.
7. Run the guide's source-comparison scenarios and physical-TV checks before claiming equivalence.

Use `reference/kotlin/GameMath.kt`, `ControllerState.kt`, and `JoustRandom.kt` for the small recovered math/input primitives. They are reference components and have not been compiled as an Android project. Resolve exact branches through the pinned files in `reference/source/` when the guide calls for it. Do not replace a known quirk with an intuitive mechanic.

## Verification and known boundaries

Run `python tools/verify_package.py --package-dir .` from the extracted folder (Python plus Pillow). It checks file hashes, table counts, indexed images, PCM format and sound-capture completion. `REPRODUCE.md` gives all extraction commands.

The source analysis and asset/data extraction are complete for the supplied package. A playable Kotlin implementation and full-game differential replay have not been executed. Remaining comparison work is specific: secondary actor-service deferral, RNG time/draw interleaving, unusual target selection, exact AI continuation boundaries, live audio transition history and final TV presentation/input latency. These are documented in the guide so the build agent can validate them instead of guessing.

