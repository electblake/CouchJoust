# Couch Joust

A native Kotlin recreation of Joust for Android TV. The game uses the original decoded artwork, fonts, collision profiles, wave tables and sound captures in the supplied research pack. It runs its own Kotlin simulation and Canvas renderer; no emulator or ROM is needed on the television.

## Install and play

### Install with Downloader by AFTVnews

**Downloader code: `9369099`**

**Install link: [aftv.news/9369099](http://aftv.news/9369099)**

1. Install **[Downloader by AFTVnews](https://play.google.com/store/apps/details?id=com.esaba.downloader)** from your TV's app store and open it.
2. Enter **`9369099`** in Downloader's URL / short-code field and select **Go**. You can also enter **`http://aftv.news/9369099`**.
3. Download the Couch Joust APK. When Android requests permission, open **Settings** and allow **Downloader** to **install unknown apps**.
4. Return to the APK installer, select **Install**, then open **Couch Joust** from the TV launcher.

These Downloader instructions install stable 1.0.0, which requires Android 6.0 / API 23 and has no network permissions or native libraries. You can also download it from [Releases](https://github.com/electblake/CouchJoust/releases).

For the two-TV connection prototype, install `CouchJoust-2.0.0-alpha.1.apk` from [the prerelease](https://github.com/electblake/CouchJoust/releases/tag/v2.0.0-alpha.1). It requires Android 7.1 / API 25 or newer, includes ARMv7/ARM64/x86/x86_64 native libraries, and requests Internet access. Android 17/API 37 also requests local-network permission when you choose Host or Join. Local Joust still works offline.

### Remote controls

| Control | Action |
|---|---|
| D-pad left / right | Move and face left / right |
| Center / Enter | Flap; release and press again for each flap |
| Back | Pause; return from menus |
| D-pad up / down, center | Navigate and select menus |

Holding center does not repeatedly flap. A remote must report direction and center presses independently to combine steering and flapping. Two players can join with two Android input devices; each presses center on the join screen. Gamepads also accept A for flap, Start for pause, and their D-pad or horizontal stick for movement.

Free play is enabled initially. Settings provide sound, screen size for overscan, and a credit mode. In credit mode, selecting a game with insufficient credits adds one credit. High scores and settings persist locally. Losing focus or disconnecting an assigned device pauses play.

## Two-TV connection prototype

This prerelease tests pairing and transport; **it does not synchronize Joust gameplay between TVs**.

1. Install the same prerelease on both TVs and select **TWO TV CONNECTION TEST** on each title menu.
2. Select **Host** on one TV. Enter its `number-word-word` code on the other TV and select **Join**. The code field supports the TV keyboard and backspace.
3. When connected, use the D-pad to move the yellow marker and Center/Enter/A to increment the flap counter. The blue marker and remote flap counter come from the other TV.
4. Inspect the selected direct/relay path, RTT, stream echo time, and sent/received datagram counts. Back leaves the test; the peer sees that the session ended. Launch the test again to pair a new session.

Magic Wormhole 1.0.1 delivers an encrypted invitation through its public rendezvous service. The invitation carries an iroh endpoint ticket and a fresh 256-bit admission secret. An AES-GCM proof inside the encrypted QUIC stream authenticates the guest. Each endpoint admits one guest; the invitation is not saved. iroh Android 1.2.0 carries ongoing traffic using direct/relay networking, with 20-byte marker/control datagrams at approximately 60 Hz and a separate reliable stream for echo and orderly leave. No account, IP entry, or port forwarding is required.

The app ends the test when it leaves the foreground. Unexpected pairing or transport failures propagate without application retries, recovery, or alternate transports. Physical-TV and separate-household testing remain unverified; see [VALIDATION.md](VALIDATION.md) for emulator evidence and Android compatibility findings.

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
