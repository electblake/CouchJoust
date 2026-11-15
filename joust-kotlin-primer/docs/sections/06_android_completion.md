## Attract mode, start flow, HUD and high scores

The original presentation is part of a complete port. Use the supplied fonts and phrase data for the arcade screen. Android menus can surround the game, but the game image should retain its own score, wave, credit and life displays. Source labels and layout coordinates are in `PHRASE.ASM`, `MESSAGE.ASM`, `ATT.ASM` and `TB12REV3.ASM`.

### Start and session rules

In attract mode, the original one-player start requires one credit and two-player start requires two, unless free play is configured. The native host may expose both a direct Play menu and the original credit controls; use the original credit/start path for the faithful default. All factory gameplay settings are specified in the enemy/wave section. Preserve all per-game resets, including gladiator flags, score, egg chain, difficulty counters, bridge state, lava level and respawn protection. [Start conditions and reset](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L825-L1005).

P1 and P2 are simultaneous players. There is no alternating-turn mode to invent. Ordinary friendly fire is active, and the two player avatars share movement/collision behavior despite different ostrich/stork art. A two-player game is still active while one player retains lives. Use per-player game-over messaging and retain the last player's ongoing game. [Player death and game-over](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4669-L4806).

### Presentation timing inventory

| Stage | Exact timing or behavior | Source |
|---|---|---|
| Game-over message before score qualification | 11×8 = 88 ticks | `GOVWAT` |
| Further message hold when nobody enters initials | 109×8 = 872 ticks; the nearby 88-tick comment is stale | `ATTRC2` |
| High-score attract page | 15×60 = 900 ticks; palette cycles while displayed | `ATTRCT` |
| Marquee/title hold after drawing | 111×10 = 1,110 ticks; stripe/flash processes run | `ATT.ASM MARQUE` |
| Initials update | Service every 3 ticks | `TB12REV3.ASM SETP1` |

Sources: [game-over and attract](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L682-L744), [marquee](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/ATT.ASM#L50-L138), [initials](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/TB12REV3.ASM#L1894-L1925).

The instruction demonstration is scripted gameplay rather than a prerecorded movie. Its steps use the following base waits, each unit meaning 60 ticks. The source additionally performs eight ticks of erase/setup on each side of its stage transition. Implement the script through the same actors and effects so the demo shows the actual game behavior.

| Script stage | Base units |
|---|---:|
| Introduction | 2 |
| Flying | 9 |
| Joust | 7 |
| Eggs | 5 |
| Enemy heading | 2 |
| Bounder | 2 |
| Hunter | 2 |
| Shadow lord | 1 |
| Erase heading | 1 |
| Erase shadow lord | 5 |
| Spawn protection | 6 |
| Enemies leave | 2 |
| Pterodactyl | 10 |
| Cleanup | 5 |

Source: [demo sequence and dispatcher](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L299-L365). The extracted title PNG is the decoded logo; border motion, credits, messages and palette cycling still need the timed presentation described by `ATT.ASM`.

### High-score entry and persistence

Use left/right to cycle the allowed character set and flap to accept. The set is space, A–Z, and a back-arrow; the arrow moves back one position when possible. Ordinary entries have three initials. The featured record's name length comes from the settings and defaults to three. Preserve the initial confirm latch so a flap held at game end does not instantly enter a character. Ties do not displace an existing score: the ranking comparison requires a strictly greater score. Both players can qualify; preserve the source's score ordering and independent entry state. [Character handling](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/TB12REV3.ASM#L1183-L1272), [comparison](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/TB12REV3.ASM#L1855-L1869), [initial latch](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/TB12REV3.ASM#L1610-L1616).

The source has six “today” scores and a persistent all-time/featured score structure. “Today” is initialized at program startup; it is not a real calendar-midnight reset. For the Android port, store all-time scores/settings persistently and reset the session table on a deliberate new application session. Do not reset it merely because Android recreates a view during the same session. Preserve source default initials and ranking tables instead of substituting invented leaderboard content. [Definitions](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/EQU.ASM#L103-L120), [startup table initialization](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/TB12REV3.ASM#L529-L553).

## Android TV and Xbox controller integration

The Android input layer translates devices into the game's three held inputs. The game does not need a custom Bluetooth protocol. Pairing belongs to the TV's system settings; the app receives gamepad events through Android's input APIs. Use `KeyEvent` for buttons, `MotionEvent` for axes, and `InputDevice` to identify each controller. [Official controller input documentation](https://developer.android.com/games/sdk/game-controller/controller-input).

### Default mapping — deliberate port choice

| Physical control | Android input | In gameplay | Attract/menu/initials |
|---|---|---|---|
| D-pad left/right | `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT`; also hat x if supplied | Left/right held state | Navigate; cycle initials |
| Left stick horizontal | `AXIS_X` | Digital left/right after dead zone | Menu navigation if enabled |
| A | `KEYCODE_BUTTON_A` | Flap held state | Confirm; confirm an initial |
| View / Select | `KEYCODE_BUTTON_SELECT` | No movement action | Insert one credit |
| Menu / Start | `KEYCODE_BUTTON_START` | Pause/resume | Start one player |
| Y | `KEYCODE_BUTTON_Y` | No movement action | Start two players from attract |
| B or TV Back | `KEYCODE_BUTTON_B`, Android back navigation | Open pause menu | Back/cancel |

Route each controller's movement/flap events to its assigned player. Device ID is the current event-routing key; keep the persistent descriptor for reconnect matching. When a controller disconnects, clear all its held inputs and pause; reconnect by descriptor or an explicit controller-selection screen rather than giving another device control silently. The user should be able to select P1/P2 without an Android keyboard or touch screen. [Official multiple-controller guidance](https://developer.android.com/games/sdk/game-controller/multiple-controllers).

Read both `SOURCE_GAMEPAD` and `SOURCE_JOYSTICK` capabilities and support d-pad axes as well as keys. `MotionEvent.AXIS_HAT_X` must not be confused with the analog stick. The proposed default stick threshold is `max(reportedFlat,0.5)`; 0.5 is a host choice, not an original Joust constant. The same source button held across repeated `ACTION_DOWN` callbacks must remain one held input. The game generates its flap press only on a sampled released-to-held transition. Preserve simultaneous left+right as neutral. Clear held states on focus loss and do not replay an old press when resuming.

`reference/kotlin/ControllerState.kt` implements the pure input reduction only. Connect it to the actual Android callbacks, apply assignment/pause rules, and keep simulation sampling on the game thread. Test Xbox controllers connected over both USB and Bluetooth when those transports are available; event handling should be transport-independent.

Declare a TV launcher activity with `android.intent.category.LEANBACK_LAUNCHER`, mark touchscreen as not required, and provide TV banner/icon resources. Keep all surrounding menus navigable by d-pad/remote and controller. A rendering `SurfaceView` or another pixel-accurate surface is an implementation choice; the gameplay core should not depend on UI composition callbacks. [Official TV setup](https://developer.android.com/training/tv/get-started/create).

### Audio integration

Load the supplied WAV material without changing playback pitch or speed. Map the original sound events and their priority/interrupt rules to one logical game sound channel. A convenience player that mixes every request simultaneously changes the original behavior. See the audio section and the audio manifest for exact clip coverage, command durations, resets and sustained effects. Use a small PCM playback layer if exact stopping and command timing are awkward in a high-level sound-effects API. [Android AudioTrack reference](https://developer.android.com/reference/android/media/AudioTrack).

## Native build plan and acceptance criteria

### Implementation order

| Milestone | Build | Completion evidence |
|---|---|---|
| 1. Data and display | Load palette/indexed art, original fonts, terrain, frame offsets and cropped viewport. | Pixel-grid screenshot matches supplied static reconstruction and atlas; no bilinear blur or incorrect mirroring. |
| 2. One controlled bird | Input sampling, all ground states, footfall strides, flap transitions, integer flight, wrap, ceiling and lava. | Deterministic movement vectors and boundary cases in this document pass. |
| 3. Contact and life cycle | Source silhouettes, terrain response, joust height/tie rules, death, score, extra lives, riderless departure and protected respawn. | Collision offsets and last-life/extra-life ordering verified. |
| 4. Riders and eggs | All three AI state families, pursuit quotas, target selection, falling/bouncing eggs, hatching, replacement birds and four-defeat budget. | Fixed-seed scenarios and rider-count invariants pass. |
| 5. Complete waves | 90 entries, difficulty arithmetic, specials, bridges, trolls, both pterodactyl categories and 81–90 repeat. | Wave table assertions and targeted late-wave scenarios pass. |
| 6. Full presentation | Score/life/wave HUD, credits, attract demonstration, title, original sound events, initials and persistence. | Visual comparisons and event-sequence audio tests pass. |
| 7. TV completion | Two controllers, pause/resume, disconnect/reconnect, launcher, controller-only navigation and installable APK. | Physical Sony Google TV play-through and device lifecycle checks. |

Use the decoded assets immediately in milestone 1; placeholder birds and replacement sounds cannot establish visual/audio fidelity. Do not defer eggs, two-player friendly fire or pterodactyl differences as optional features: they are central rules.

### What to log for differential comparison

Each simulation tick should be able to emit: tick index, controller bits, RNG state/draws and time-perturbation markers, ordered actor IDs and service counts, secondary resume position, actor state labels, x and fractional remainder, y/velocity, horizontal speed index, collision profile and enabled flag, collision pairs/results, score/lives/egg chains, wave entry/display number, unresolved rider/active bird counts, difficulty counters, and accepted/rejected sound events. Keep logs optional in normal play. A replay stores settings and initial state, then the sampled input bits per player. Record any source-comparison secondary-service schedule and RNG time-event ordering as well; a seed and controller bits alone do not establish the same original random sequence. Wall-clock event timestamps alone are insufficient.

Use the source-derived checks embedded in the movement and AI sections. At minimum include:

- Early single-player game from start through first egg wave, with both rapid and slow flapping.
- Both players at exactly equal lance height, plus a simultaneous third-actor contact.
- Last life with score less than 50 below an extra-life threshold.
- A resting egg losing its supporting bridge; a fourth-defeat egg auto-award with a victor; and a fourth defeat in lava with no victor.
- Pterodactyl mouth entry at every threshold boundary for both facing directions.
- Troll escape at exactly −384 and −385 velocity units, plus range-triggered escape with its second position integration.
- Transition through wave 90 into table entry 81 and displayed wave 91.
- Two controllers generating opposite d-pad inputs, key repeats, disconnection and focus loss.
- A high-priority sound interrupting a low-priority one, and an equal-priority replacement.

### Specific remaining fidelity checks

The package supplies extracted assets and exhaustive core tables plus detailed reconstructed mechanics. The following are comparison tasks for the build agent, not substitutes to guess:

1. Confirm all state-entry/counter boundaries against a source-run trace, especially secondary enemy/effect scheduling, the first active tick of a new actor, and inter-wave drain timing.
2. Confirm the unusual two-player target selector across seam and equality cases. The literal reconstruction is supplied; intuitive nearest-player targeting is different.
3. Resolve RNG perturbation/call interleaving with gameplay and visual effects before claiming input-for-input replay identity.
4. Verify replacement-bird remount behavior around stacked ledges and simultaneous player/egg/enemy contacts.
5. Use the audio coverage report to distinguish isolated/pre-rendered sound output from state-dependent cross-event transitions. Add transition fixtures where required before claiming every interruption is sample-identical.
6. Compare animated lava, progressive bridge/ledge effects, materialization, palettes and attract layout with source output. The static arena image alone does not validate these animations.
7. Verify real TV presentation, audible latency, controller mappings and pause/reconnect behavior on the user's hardware. This package contains no physical-TV validation.

### Definition of finished

A finished implementation launches from Google TV, runs one- and two-player games through the full repeating wave sequence, uses the original decoded art and sound, accepts ordinary Xbox controller inputs, handles all rider/egg/pterodactyl/troll/respawn cases, preserves score and bonus ordering, includes attract/high-score presentation, and passes the concrete fidelity checks. The Android app runs native Kotlin game rules. Any remaining difference from the source is recorded specifically, with the affected input sequence or screenshot, instead of hidden behind a claim of “faithful enough.”

