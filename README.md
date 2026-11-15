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

You can also download `CouchJoust-1.0.0.apk` directly from [Releases](https://github.com/electblake/CouchJoust/releases). Android 6.0 / API 23 or newer is required. The APK contains no native architecture-specific code and requires no network permissions.

### Remote controls

| Control | Action |
|---|---|
| D-pad left / right | Move and face left / right |
| Center / Enter | Flap; release and press again for each flap |
| Back | Pause; return from menus |
| D-pad up / down, center | Navigate and select menus |

Holding center does not repeatedly flap. A remote must report direction and center presses independently to combine steering and flapping. Two players can join with two Android input devices; each presses center on the join screen. Gamepads also accept A for flap, Start for pause, and their D-pad or horizontal stick for movement.

Free play is enabled initially. Settings provide sound, screen size for overscan, and a credit mode. In credit mode, selecting a game with insufficient credits adds one credit. High scores and settings persist locally. Losing focus or disconnecting an assigned device pauses play.

## Game content

- One or two players, height-based jousting, ties, friendly fire, scores, extra lives and protected respawning.
- Bounders, Hunters, Shadow Lords, line tracking and pursuit, eggs, hatching, replacement mounts and egg chains.
- All 90 wave records, changing difficulty, survival/team/gladiator/egg/pterodactyl waves, and the final-ten-wave repeat.
- Destructible cliffs, burning bridges, lava trolls, wave and timeout pterodactyls.
- Original indexed graphics and fonts, animated marquee border, instructional attract sequence, initials and score tables.
- One prioritized sound channel using the source-built 48 kHz sound captures and original command schedules.

See [VALIDATION.md](VALIDATION.md) for test evidence and fidelity boundaries. This is a source-informed native recreation, not a claim of cycle-exact arcade hardware emulation.

## Build

Install a JDK 17 or newer and Android SDK platform 37. Set `sdk.dir` in an untracked `local.properties` file to your SDK directory, or set `ANDROID_HOME`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For an unsigned release APK, run `:app:assembleRelease`. `tools/sign_release.py --build-tools <directory> --keystore <file> --password-file <file>` signs it with an external keystore whose alias is `couchjoust`; signing credentials are not stored in this repository. Official release updates require the same signing key as the installed official APK.

The checked-in Gradle wrapper pins Gradle 9.6.1; Android Gradle Plugin 9.2.1 provides Kotlin compilation. `tools/generate_tables.py` regenerates the Kotlin tables and launcher images from the pack (Python 3 and Pillow required).

## Verification commands

```powershell
adb -s emulator-5582 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5582 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5582 shell am instrument -w com.couchjoust.test/com.couchjoust.GameplayInstrumentation
python tools/tv_smoke.py --adb /path/to/adb --serial emulator-5582
python joust-kotlin-primer/tools/verify_package.py --package-dir joust-kotlin-primer
```

The instrumentation runner reports each scenario and its assertions; check the final pass count, since Android's `am instrument` command can exit successfully even when its test process crashes. The smoke tool records real OS-dispatched inputs, pause/resume screenshots and an MP4 into `artifacts/tv-smoke`.

## Provenance

The reference is [synamaxmusic/joust](https://github.com/synamaxmusic/joust/tree/87e22aab945709fa107df609c0efcb839a17c1f9), pinned to `87e22aab945709fa107df609c0efcb839a17c1f9`. The original source and research are preserved in `joust-kotlin-primer/`; begin with its `START_HERE.md` and `OPEN_ME.html`. The uploaded ZIP is also retained. Joust and the original game artwork and sounds are Williams material; this project is an unofficial recreation.

The native code is in `app/src/main/java/com/couchjoust/`; `core/` contains the platform-independent rules, scheduler and tables. `tools/build_reference.py` and `tools/reference_trace.lua` support source-built reference comparisons using locally installed AS and MAME. External tools and generated ROMs are excluded from version control.
