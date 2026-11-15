# Joust V4 — native Kotlin game specification

## Purpose and reference version

Build a complete, faithful, local one- or two-player Joust for Android TV / Google TV in Kotlin. The game simulation must implement the original rules directly. The delivered graphics, fonts, effects, sound recordings, numeric tables and source references are the implementation material. This is a build primer and asset package, not a completed Android application.

**Reference:** `synamaxmusic/joust`, commit `87e22aab945709fa107df609c0efcb839a17c1f9`, reviewed 14 November 2026. This repository reconstructs the final revision 4 of **Joust (1982)**. It is not a 1984 sequel or a modern reinterpretation. The supplied source snapshot fixes the reference version even if the upstream repository later changes. [Repository README](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/README.md).

### Implementation contract

Preserve original movement, collision silhouettes, integer rounding, AI states, wave data, egg behavior, friendly fire, score ordering, respawn protection, art, animation and sound priorities. Retain quirks when they change a visible or playable result. Ground movement is an authored state machine; flying uses discrete impulses and speed choices. A generic physics engine or a continuously steering enemy will not reproduce this game.

Keep Kotlin gameplay code readable: `Bird`, `Egg`, `WaveState`, `PlayerState`, `TerrainState`, `SoundEvent` and explicit state machines are appropriate. The source labels in this document are evidence anchors and mappings to tables. They are not a requirement to reproduce the original language, storage layout, processor, graphics hardware or memory map in the Android app.

Three evidence levels are used throughout:

| Level | Meaning | How the build agent should use it |
|---|---|---|
| Extracted | Literal artwork, table entries, numbers and event records decoded from the pinned source. | Import directly; verify supplied hashes. |
| Reconstructed | Algorithms and transitions translated by reading source branches and arithmetic. | Implement precisely; validate with the named scenarios and source anchors. |
| Port choice | Android controls, lifecycle, display scaling, persistence and implementation structure. | Follow the stated defaults while keeping original game rules intact. |

No playable Kotlin clone, original-game differential replay, or physical-TV input-latency test was executed during this analysis. The verification record identifies what was actually checked. Specific remaining comparison work is listed at the end; the presence of exact data does not establish frame-for-frame equivalence of an implementation that has not yet been written.

### Files to read first

1. `START_HERE.md` — concise build-agent brief and completion requirements.
2. `docs/JOUST_GAME_SPEC.md` — this full specification; `docs/JOUST_GAME_SPEC.html` is the illustrated reading copy.
3. `assets/graphics/asset-manifest.json` — indexed pixels, palette, frame offsets, authored collision profiles, fonts and terrain.
4. `data/gameplay_tables.json`, `data/physics_tables.json`, `data/waves.csv` — exact game data.
5. `assets/audio/` — decoded sound recordings and their playback/coverage notes.
6. `reference/kotlin/` — small native math and input reference implementations; these are helpers, not a complete game engine.
7. `reference/source/` — pinned original reference files for resolving the cited branches and the remaining timing questions.

## Coordinate system, screen and presentation

Use source-pixel world coordinates. Positive x is right; positive y is down. Bird x is a left-side reference and bird y is a bottom reference, not a sprite center. Vertical position has a 1/256-pixel fraction. Different frames and species have different visual offsets from this reference; use the animation descriptors. A left-facing sprite cannot be assumed to be a centered mirror of the right-facing sprite. [Frame descriptors](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L759-L857).

| Item | Required value or interpretation |
|---|---|
| Source drawing canvas | 304 × 256 source pixels. |
| Visible reference crop | x=6…297 and y=7…246 inclusive: 292 × 240 source pixels. |
| Bird horizontal wrap | Reference x in −10…292 inclusive; period **303**, independently of image width. |
| Ceiling | Bird bottom y=32; exact clamp/reflect behavior is in the movement section. |
| Lava entry | Candidate bird bottom y≥230 enters sinking; the visible lava surface is separately animated. |
| Player starting references | P1 `(100,210)` facing right; P2 `(200,210)` facing left. |
| Presentation aspect | Use a 4:3 game viewport as the default TV presentation, centered with side bars on a 16:9 panel. Keep a square-source-pixel inspection mode for asset/debug comparisons. |

The crop comes from the maintained original-game reference configuration, not from guessing the bounds of the art: [MAME reference at the reviewed revision](https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams.cpp#L1556). The 4:3 fitting policy is a port presentation choice. It never changes simulation coordinates or collision geometry.

For a TV target rectangle of width `W` and height `H`, use `viewH=min(H,3*W/4)` and `viewW=4*viewH/3`, center it, then map the cropped image into it. Preserve the whole game image; do not fill a 16:9 screen by cutting away the top or bottom. Turn off bitmap filtering when drawing source art. Render all world objects on the source pixel grid while retaining their fractional simulation positions, and perform final scaling only after composing the frame. Decorative CRT shaders, scanlines and smoothing are optional display features; the default reference image is the decoded art.

### Arena geometry

![Static source terrain with coordinate annotations](figures/arena-annotated.png)

This figure is a reconstruction from the source artwork, not a captured gameplay frame. It shows the complete static ledge set and transporter locations. Bridges, burning fronts, lava animation, actors, text and wave-dependent cliff removal are dynamic. Use the runtime landing/solid maps from `physics_tables.json`; visible colored pixels are not a substitute for the game's collision data.

| Terrain | Art top-left x,y | Art size | Standing bird bottom y |
|---|---|---|---:|
| Top left `CLIF1L` | 2,69 | 34×7 | 68 |
| Top right `CLIF1R` | 252,69 | 48×7 | 68 |
| Upper middle `CLIF2` | 86,81 | 88×9 | 80 |
| Left middle `CLIF3L` | 2,138 | 64×8 | 137 |
| Upper right middle `CLIF3U` | 202,129 | 58×11 | 128 |
| Outer right middle `CLIF3R` | 254,138 | 48×7 | 137 |
| Lower middle `CLIF4` | 106,163 | 64×8 | 162 |
| Bottom island `CLIF5` | 54,211 | 186×2 top, plus decoded body | 210 |

The four transporter art origins are `(106,81)`, `(224,129)`, `(16,138)`, `(120,211)`; each is 28×3. Actor spawning uses the separate spawn reference coordinates and safety masks in the lifecycle section. [Terrain and transporter data](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L54-L752).

## Time, deterministic updates and random choices

### Fixed tick and timers

Run the simulation independently of the Android display refresh. Original source comments usually use 60 ticks as one second. The reference display cadence is `8,000,000/(512*260)` = **60.09615384615385 ticks/second**, with a tick duration of **16,640,000 ns**. Use that duration for the fidelity default; keep every source delay as an integer number of its specified ticks or AI calls. Do not rescale a delay of 61 into a guessed “one second,” and do not run at 120 simulation updates per second because the TV renders at 120 Hz. [Reference cadence](https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams.cpp#L1556).

A frame accumulator may run zero, one or several fixed steps before rendering. Keep simulation ticks when catching up. When the app leaves the foreground or a controller disconnects, pause deliberately and reset the host-clock accumulator on resume; do not catch up minutes of absent gameplay. This pause behavior is an Android port choice. It does not alter how an unpaused tick behaves.

Not every actor state advances every tick. A delay belongs to the state or actor that owns it. Early enemies, hatching checks, score displays, cliff effects and wave monitoring have different call periods. Preserve countdown decrement-before-test behavior, and use the original update sequence when an actor changes state or creates another actor. The ordering audit below gives the recovered sequencing contract. [Scheduler and waits](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L158-L255).

### Native data model

| Object | State the native implementation needs |
|---|---|
| Game | Active/attract/game-over state, credits/free-play setting, player count, fixed tick, ordered actor/tasks list, random state, current palette and sound sequencer. |
| Player | Controller assignment, score, reserve/total lives, next extra-life threshold, egg chain, survival deaths, player-kill/bounty flags, active bird and respawn request. |
| Bird | Species, ridden/riderless mode, facing, x integer and horizontal remainder, y in 1/256 pixels, vertical velocity, horizontal speed index, ground/air state, timers, bump displacements, wing/stride frame, collision flag/profile, visual offset and AI state. |
| Enemy rider | Intelligence level, remaining egg budget, pursuit-slot ownership, target slot, targeting timers, movement AI continuation and excursion counters. |
| Egg | Rider identity/intelligence/budget, x/y and velocity, first-bounce flag, resting/hatching/standing state, hatch timer, reserved replacement bird. |
| Wave | Display number, table entry, unresolved riders, active/reserved birds, current/requested pursuing count, introduction/drain state, special callback, baiter index/timer and dynamic difficulty values/countdowns. |
| Terrain | Active cliff flags, mutable landing and solid maps, bridge burn positions, lava display target/current position and troll state. |

Fields are conceptual. There is no need to preserve source storage offsets. There is a need to preserve distinctions such as unresolved riders versus currently mounted enemies, or reserve lives versus total lives including the active bird. [State definitions](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/RAMDEF.ASM), [wave controller](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1875-L2229).

### Random behavior

Keep one shared deterministic random stream. The source draw function has two unsigned byte state components `H` and `L`. One draw computes:

```text
a = (L >> 3) XOR L
returnedByte = a >> 1
feedback = a AND 1
newH = (feedback << 7) OR (H >> 1)
newL = ((H AND 1) << 7) OR (L >> 1)
returnedBit = L AND 1
```

The old `L` supplies the returned bit. Source callers use either the returned byte, its bits, or the final bit; those interfaces are not interchangeable. Separately, the original clock perturbs the shared state with `H=(H+1)&255`, `L=(L-1)&255` at its subframe service points. Visual effects also consume random draws. [Draw function](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L746-L754), [clock perturbation](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L576-L585).

`reference/kotlin/JoustRandom.kt` supplies the draw and perturb operations separately. The exact interleaving of perturbations, gameplay calls and cosmetic calls has not been replay-validated here. Log those events during source comparison, then fix their order in the native scheduler. Seeding `Random.Default` once, omitting visual draws, or swapping this algorithm for a modern generator gives different enemy/spawn outcomes. A deterministic native replay must store starting random state, input samples, update ordering and settings.

![Exact horizontal speed choices and flap impulse curve](figures/movement-math.png)

The following movement section defines how and when to use these values. The impulse curve is the exact integer function, not a fitted physical model.

