# Verification and fidelity

## 2.1.0 two-TV network play

- The signed release (build 6) ran as two independent Android processes on Android TV API 36 x86_64 and API 25 x86, using real public Wormhole rendezvous and iroh transport. No mocked network or substituted pairing provider was used.
- The final build produced identical game-state snapshots for **9,394 shared ticks across two matches**. Comparisons include actor positions/velocities/collision state, RNG state, player scores/lives/reserves/chains, terrain collision-map hashes, lava/bridge state, transporter occupancy, wave count, and game-over timing. Both players' direction and flap inputs were observed on both simulations.
- Both TVs reached game over at tick **9,261**, showing P1 **2,250** and P2 **1,000**, then completed readiness/start for a fresh rematch with reset scores and wave 1. Guest departure ended the host's session through the leave handshake.
- A shared pause lasted over **32 seconds**, exceeding the transport's idle interval; the heartbeat kept the session connected and both TVs resumed. The test accelerated the host pump to 1 ms only after the first 600 gameplay ticks to reach natural game over sooner; it did not inject kills, scores, or game-over flags.
- Before the heartbeat addition, swapped host/guest roles also passed on the same two device images, comparing **3,104 shared ticks** with both-player input and terrain checks. The earlier initial run compared **5,035 shared ticks**. Final-build evidence is separate from the reversed-role evidence.
- **29 JVM tests passed**, including 15,000 ticks replayed with different delivery batches, semantic update ordering, and the existing game simulation tests (including all 90 waves and final-ten-wave progression). Android lint reported **zero errors and 30 warnings**. Release signature and 16 KB ZIP alignment verification passed.
- All **17 local gameplay/menu instrumentation scenarios passed on each device** against the final signed release, including reaching the new network menu and the existing Updates menu.

Run `tools/network_smoke.py --adb PATH --host SERIAL --guest SERIAL --output DIRECTORY` with the signed release and same-key instrumentation APK installed on both devices. This exercises pairing UI, readiness, start, OS-dispatched controls, shared pause/resume, natural game over, results, rematch, and peer exit. Reports, screenshots, and compressed final-run tick records are in `verification/network-play/`. The protocol is described in [docs/network-play.md](docs/network-play.md).

The two-device games exercised wave 1 and a fresh rematch. All-wave progression is covered by JVM/local instrumentation, not a two-TV playthrough of all 90 waves. Physical TVs, ARM runtime performance, separate-household routing, and adverse-network latency/loss remain unverified. Network delay affects guest input/display and can slow the simulation; no prediction, reconnect, or host migration is implemented.

## 2.0.0 release

The stable release uses version `2.0.0`, Android build 5, and the existing signing key. It contains the updater implementation tested below; the final release build reran all 28 JVM tests and Android lint. Stable installations check stable GitHub releases. Build 5 also supersedes the build-4 installer test fixture.

## In-app updater (2.0.0-alpha.2)

- All 28 JVM tests passed, including semantic version precedence and stable/prerelease update selection. Android lint completed with zero errors and 26 warnings; release and instrumentation APKs built successfully.
- All 17 gameplay/menu instrumentation scenarios passed on API 25 and Android TV API 36, including opening Updates from the title menu.
- On both devices, updater instrumentation fetched the real public GitHub releases feed, confirmed the current-version state, checked newer-version changelog display and button enablement with controlled metadata, downloaded a real published APK, and verified the scoped content URI passed to the installer.
- Both devices installed a locally built, same-key, higher-version APK through Android's native installer. The package advanced from build 3 to build 4, and a saved preference survived installation. This fixture was not published. On API 36 the app opened per-app install-source Settings; automation granted the permission and returned to the app before confirming the installer. API 25's global unknown-source permission was enabled in test setup.
- The signed distributable is version `2.0.0-alpha.2`, build 3. Signature verification and 16 KB ZIP alignment checks passed. Installer success terminates the running app and its instrumentation, so the separate post-install check verifies the new package and retained data.

Run the normal instrumentation suite for menu/gameplay checks, or add `-e updates check` for the live-feed/download scenario. `-e updates install` requires a same-key build-4 `2.0.0-alpha.3` fixture at the app's external-files `update-fixture.apk` path and external confirmation of Android's installer; afterward `-e updates verify-install` checks the upgrade and saved preference. Test fixtures do not alter the production feed or download URLs. Reports and installer screenshots are in `verification/updates/`.

Physical Android/Fire TVs and their vendor-specific installation settings remain untested. Older installed releases need a one-time sideload of an updater-enabled build.

## 2.0.0-alpha.1 connection prototype

The connection prototype was verified with the release signing certificate on Android TV API 25 (x86) and API 36 (x86_64) emulators, plus a generic AOSP API 37 x86_64 emulator configured at TV resolution. Compile and target SDK remain 37. This verifies independent Android processes using actual public rendezvous and relay services; it does not establish physical-TV, ARM runtime, direct-path or separate-household performance.

- The published `computer.iroh:iroh-android:1.2.0` AAR declares minimum API 24, so the former API 23 minimum is incompatible. A stock API 24 TV image then failed Wormhole's TLS handshake with `CertPathValidatorException: Trust anchor for certification path not found`. The same primary connection path succeeded on stock API 25. The prerelease therefore requires API 25, without disabling certificate verification or adding compatibility providers.
- Wormhole 1.0.1 publishes Kotlin 2.4 metadata. The build uses Kotlin Gradle Plugin 2.4.20 through AGP's supported buildscript dependency configuration.
- Android 17 requires `ACCESS_LOCAL_NETWORK` for apps targeting API 37. Host/Join requests it before creating an endpoint, and the API 37 test clicked the actual system permission prompt, confirmed the grant, and connected to the API 25 TV. The permission dialog can temporarily cover the TV activity without ending the pending connection. See [Android's local-network permission requirements](https://developer.android.com/privacy-and-security/local-network-permission).
- Host and guest both passed actual Wormhole code exchange, iroh ticket connection, AES-GCM admission, more than 300 received control-test datagrams, OS-dispatched movement and flap input, at least three reliable stream echoes, and the leave/acknowledge exchange. Both API 25 and API 36 were exercised as host. The measured relay/echo values were approximately 63–79 ms in these runs, not a latency guarantee.
- All 25 JVM tests passed: the existing 22 core tests plus datagram round-trip, admission proof and wrong-secret rejection tests.
- All 16 gameplay instrumentation scenarios passed on API 25 and API 36, including the added title-menu connection-test entry. The same 16 scenarios passed on API 37 with no active default network and Ethernet/Wi-Fi disabled. Existing movement, all-wave simulation/rendering, audio, pause, controller assignment, settings, score and initials scenarios remained green.
- Debug lint reported zero errors and 23 warnings. JNA was updated to 5.19.1 to remove the older dependency's 16 KB native alignment warnings. Both iroh and JNA ARM64/x86_64 ELF load segments have 16,384-byte alignment. The signed APK passes `zipalign -c -P 16 4` and `apksigner verify`.
- APK ABIs are restricted to ARMv7, ARM64, x86 and x86_64, with both iroh and JNA present for each. Desktop native resources are excluded. Dependency license notices are included under `assets/licenses`.

Reproduce with the commands in README and `tools/connection_smoke.py`. Reports and screenshots are under `verification/connection-prototype/`. The release APK and SHA-256 checksum are attached to the GitHub prerelease.

This is pairing and transport infrastructure, not multiplayer Joust. No network game simulation, prediction, shared match lobby/results, reconnect or host migration is implemented. Unexpected failures intentionally propagate under the project's fail-fast policy. Existing local gameplay does not create a network endpoint.

## 1.0.0 baseline

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
