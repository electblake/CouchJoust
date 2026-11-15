# Couch Joust

A native Kotlin recreation of Joust for Android TV. The game uses the original decoded artwork, fonts, collision profiles, wave tables and sound captures in the supplied research pack. It runs its own Kotlin simulation and Canvas renderer; no emulator or ROM is needed on the television.

## Install and play

### Install with Downloader by AFTVnews

**Downloader code: `7081442`**

**Install link: [aftv.news/7081442](http://aftv.news/7081442)**

1. Install **[Downloader by AFTVnews](https://play.google.com/store/apps/details?id=com.esaba.downloader)** from your TV's app store and open it.
2. Enter **`7081442`** in Downloader's URL / short-code field and select **Go**. You can also enter **`http://aftv.news/7081442`**.
3. Download the Couch Joust APK. When Android requests permission, open **Settings** and allow **Downloader** to **install unknown apps**.
4. Return to the APK installer, select **Install**, then open **Couch Joust** from the TV launcher.

After installation, use **UPDATES** on the title menu to download future versions. You can also download APKs directly from [Releases](https://github.com/electblake/CouchJoust/releases).

For two-TV network play, install `CouchJoust-2.1.0.apk` from [the 2.1.0 release](https://github.com/electblake/CouchJoust/releases/tag/v2.1.0), or use **UPDATES** in version 2.0.0. It requires Android 7.1 / API 25 or newer, includes ARMv7/ARM64/x86/x86_64 native libraries, and requests Internet access. Android 17/API 37 also requests local-network permission when you choose Host or Join. Local Joust still works offline.

### In-app updates

Builds from `2.0.0` include **UPDATES** on the title menu. Install this updater-enabled APK once using your existing sideloading method; older builds cannot update themselves.

1. Open **UPDATES** to see the installed version and select **Check for updates**.
2. Read the changelogs for newer GitHub releases. **Download and install** becomes available when a newer version exists.
3. Select it to download the APK. Allow **Couch Joust** to install unknown apps when Android asks, then confirm **Update** in Android's installer.

Signed updates retain settings and scores. Stable builds check stable releases; prerelease builds also check prereleases. Checks are manual and use the public `electblake/CouchJoust` GitHub releases feed. Android still requires installation confirmation. Unexpected network or release-format failures propagate without retries under the project's fail-fast policy.

Release publishers must increase Android `versionCode`, use semantic version tags (`vX.Y.Z` or `vX.Y.Z-alpha.N`), include release notes and attach exactly one `CouchJoust-VERSION.apk` signed with the existing release key. The updater orders releases by version, not publication date.

### Remote controls

| Control | Action |
|---|---|
| D-pad left / right | Move and face left / right |
| Center / Enter | Flap; release and press again for each flap |
| Back | Pause; return from menus |
| D-pad up / down, center | Navigate and select menus |

Holding center does not repeatedly flap. A remote must report direction and center presses independently to combine steering and flapping. Two players can join with two Android input devices; each presses center on the join screen. Gamepads also accept A for flap, Start for pause, and their D-pad or horizontal stick for movement.

Free play is enabled initially. Settings provide sound, screen size for overscan, and a credit mode. In credit mode, selecting a game with insufficient credits adds one credit. High scores and settings persist locally. Losing focus or disconnecting an assigned device pauses play.

## Two-TV network play

Version 2.1.0 supports a shared two-player Joust match across two TVs, with one player controlling each TV. **TWO PLAYERS** remains the local, same-TV mode.

1. Install the same version on both TVs and select **TWO TV NETWORK PLAY**.
2. Select **Host match** on player 1's TV. Enter its `number-word-word` code on player 2's TV and select **Join match**.
3. Both players select **Ready**. The host selects **Start match** once both are ready.
4. Use left/right to move and Center/Enter/A to flap. Both TVs show both players and the same enemies, terrain, collisions, lives, scores, and wave progression.
5. Back/Start pauses both TVs. Select **Resume match** to continue or **Leave / return** to end the session. Losing focus or disconnecting the active controller pauses the match; leaving the app ends the session for both players.
6. The shared results show both final scores. Both players choose **Ready for rematch**, then the host selects **Start rematch**. Network match scores are displayed in the session; local initials/high-score entry remains part of local play.

Magic Wormhole delivers the encrypted invitation and a fresh admission secret. iroh carries the game over an encrypted, reliable QUIC stream using direct or relayed connectivity. No account, IP entry, or port forwarding is required. The host orders both players' inputs into simulation ticks; both TVs execute the same deterministic game from the same starting state. Reliable input transitions preserve flap press/release edges. A twelve-tick acknowledgement window bounds how far the host can run ahead. Network latency delays the guest's input and display; slower links can reduce the simulation rate. There is no prediction or rollback.

Unexpected pairing or transport failures propagate without application retries, recovery, reconnection, or host migration. Physical-TV and separate-household testing remain unverified; see [VALIDATION.md](VALIDATION.md) for two-process game-state comparison and Android compatibility evidence.

## Game content

- One or two players, height-based jousting, ties, friendly fire, scores, extra lives and protected respawning.
- Bounders, Hunters, Shadow Lords, line tracking and pursuit, eggs, hatching, replacement mounts and egg chains.
- All 90 wave records, changing difficulty, survival/team/gladiator/egg/pterodactyl waves, and the final-ten-wave repeat.
- Destructible cliffs, burning bridges, lava trolls, wave and timeout pterodactyls.
- Original indexed graphics and fonts, animated marquee border, instructional attract sequence, initials and score tables.
- One prioritized sound channel using the source-built 48 kHz sound captures and original command schedules.

See [VALIDATION.md](VALIDATION.md) for test evidence and fidelity boundaries. This is a source-informed native recreation, not a claim of cycle-exact arcade hardware emulation.

## Build

Install JDK 21 and Android SDK platform 37. Set `sdk.dir` in an untracked `local.properties` file to your SDK directory, or set `ANDROID_HOME`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For an unsigned release APK, run `:app:assembleRelease`. `python tools/sign_release.py --version 2.0.0-alpha.1 --build-tools <directory> --keystore <file> --password-file <file>` signs it with an external keystore whose alias is `couchjoust`; signing credentials are not stored in this repository. Official release updates require the same signing key as the installed official APK.

The checked-in Gradle wrapper pins Gradle 9.6.1; Android Gradle Plugin 9.2.1 provides Kotlin compilation with Kotlin Gradle Plugin 2.4.20, required by Wormhole's published metadata. JNA 5.19.1 supplies Android native dispatch with 16 KB alignment. Desktop native libraries from the transitive iroh JVM artifact are excluded from the APK. `tools/generate_tables.py` regenerates the Kotlin tables and launcher images from the pack (Python 3 and Pillow required).

## Verification commands

```powershell
adb -s emulator-5582 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5582 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5582 shell am instrument -w com.couchjoust.test/com.couchjoust.GameplayInstrumentation
python tools/tv_smoke.py --adb /path/to/adb --serial emulator-5582
python joust-kotlin-primer/tools/verify_package.py --package-dir joust-kotlin-primer
python tools/connection_smoke.py --adb /path/to/adb --host emulator-5586 --guest emulator-5588 --output artifacts/connection-smoke
```

The instrumentation runner reports each scenario and its assertions; check the final pass count, since Android's `am instrument` command can exit successfully even when its test process crashes. The smoke tool records real OS-dispatched inputs, pause/resume screenshots and an MP4 into `artifacts/tv-smoke`.

The connection smoke test requires the app and instrumentation APK installed on two dedicated Android TV emulators/devices with matching signing certificates. It uses actual public Wormhole rendezvous and iroh transport, drives Host/Join controls, sends OS-dispatched movement/flap inputs, asserts traffic and echo results on both sides, verifies graceful leave, and saves screenshots and reports. Pairing codes are removed from completed reports.

## Provenance

The reference is [synamaxmusic/joust](https://github.com/synamaxmusic/joust/tree/87e22aab945709fa107df609c0efcb839a17c1f9), pinned to `87e22aab945709fa107df609c0efcb839a17c1f9`. The original source and research are preserved in `joust-kotlin-primer/`; begin with its `START_HERE.md` and `OPEN_ME.html`. The uploaded ZIP is also retained. Joust and the original game artwork and sounds are Williams material; this project is an unofficial recreation.

The native code is in `app/src/main/java/com/couchjoust/`; `core/` contains the platform-independent rules, scheduler and tables. `tools/build_reference.py` and `tools/reference_trace.lua` support source-built reference comparisons using locally installed AS and MAME. External tools and generated ROMs are excluded from version control.
