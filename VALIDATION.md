# Verification and fidelity

Validation was performed on Windows with a clean Android TV API 36 x86_64 virtual device at 1920 × 1080. Physical-TV playtesting remains the next device check.

## Automated checks

- **22 JVM tests passed**, including three 301-frame comparisons against the original source running in MAME: running/flapping, sustained flight, and reversing/skidding. The comparisons assert position, vertical velocity, horizontal speed index and facing on all **903 frames**.
- The source was assembled locally from the pinned research pack. MAME's canonical `joust` ROM verification accepted every assembled ROM. The comparison CSVs are in `artifacts/reference/`; their replay inputs are defined in `tools/reference_trace.lua` and `CoreTest.kt`.
- Tests cover unsigned countdowns, live scheduler insertion, fixed-point movement, flap edges, collision silhouettes, death/extra-life ordering, egg budgets/chains, gladiator bounty, pterodactyl mouth bounds and death timing, protected respawn, sound priority and queued commands, score rollover/life cap, cooperative bonuses, and bridge flames.
- A deterministic 15,000-tick two-player run repeats identically. A separate controlled-victor test progresses through all 90 waves and verifies the 81–90 repeat. That progression test deliberately defeats opponents and collects eggs; it is not a human playthrough.
- **15 Android instrumentation scenarios passed against the signed 1.0.0 release APK**, using an instrumentation APK signed with the same certificate. All 90 wave configurations run for 900 simulated ticks each; all 14 instructional stages render. The suite also checks the marquee, input repeat behavior, OS-dispatched Back, lifecycle/focus pause, two input-device identities and reconnect, settings, score persistence, and game-over initials entry.
- Android lint completed with **zero errors**. Its warnings concern newer Gradle availability, landscape orientation, backup metadata, launcher icon shape, and optional KTX syntax.
- The research pack's original file hashes, indexed image data, WAV formats and extracted table counts pass its verification script.

`verification/` contains the final recorded results and selected screenshots. `tools/tv_smoke.py` separately sends direction/center combinations through Android's OS input dispatcher and records gameplay, pause and resume. This complements tests that directly dispatch input to the Activity.

## What the evidence does not prove

The native simulation uses a 16.64 ms logical tick, source arithmetic, original tables, and shared randomness. It is **not cycle-exact hardware emulation**:

- Four interrupt-time random perturbations are applied per logical tick. Their instruction-level interleaving with game tasks and the arcade CPU's secondary-task deadline/overrun behavior are not reproduced.
- The movement traces establish the tested player paths. They do not establish identical enemy trajectories, collisions, or random histories over every possible game.
- Audio uses original command captures, priorities, schedules and a single PCM channel. Commands retain the previous DAC level until their first write. Individual captures start from their recorded sound-program state, so arbitrary interruption/noise-generator histories are not bit-identical to a continuously running arcade sound CPU.
- Rendering composes decoded frames on Canvas. It does not emulate the original blitter's framebuffer write/erase history. The TV menu, settings and pause screen are native additions; title and high-score navigation differ from a coin-operated cabinet.
- The emulator exercises audio playback and interruption behavior, but its headless host audio output was not a listening test. Remote hardware rollover, television audio latency and physical-TV performance still need testing on the intended television.

These boundaries are retained explicitly so successful functional tests are not mistaken for proof of universal arcade equivalence.
