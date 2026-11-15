# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-11-14

### Added

- Added an Updates menu showing the installed version, checks against GitHub releases, and changelogs for newer versions.
- Added APK download progress and an Android installer handoff, including per-app unknown-source installation permission on Android 8 and newer.
- Added semantic release ordering and stable/prerelease channel selection based on the installed version.

## [2.0.0-alpha.1] - 2026-11-14

### Added

- Added a two-TV connection prototype with remote-friendly Host/Join controls and encrypted Magic Wormhole phrase pairing.
- Added iroh QUIC datagrams for live marker and flap tests, reliable stream echo, direct/relay diagnostics, and coordinated session departure.
- Added per-session admission secrets authenticated with AES-GCM inside the encrypted connection.
- Added the Android 17 local-network permission request before Host/Join connects.
- Added real two-device instrumentation covering pairing, remote input, stream echo and session exit.

### Changed

- Raised the minimum Android version from API 23 to API 25: the iroh AAR requires API 24, and the stock API 24 TV trust store cannot authenticate the public Wormhole server.
- Updated Kotlin compilation to 2.4.20 for Wormhole 1.0.1 and bundled iroh Android 1.2.0 with JNA 5.19.1.
- Added the Internet permission and Android native libraries; kept local gameplay available offline.
- Made the release signing tool accept an explicit version.

This is a connection-prototype prerelease. Networked Joust simulation, shared match menus, prediction, and reconnect are not implemented.

## [1.0.0] - 2026-11-14

### Added

- Added native Kotlin gameplay with jousting, three enemy families, eggs, remounting, protected respawns, scoring and two players.
- Added all 90 original waves, difficulty progression, special waves, pterodactyls, lava trolls, burning bridges and destructible cliffs.
- Added original indexed artwork, fonts, animated marquee stripes, instructional demonstrations and prioritized sound captures.
- Added TV remote D-pad and center/Enter controls, controller joining, pause/resume, disconnect handling, overscan and sound settings.
- Added persistent high scores, remote initials entry, free play and credit mode.
- Added source movement comparisons, deterministic simulation tests, Android TV instrumentation and OS-input recording tools.
- Added build instructions, source provenance and documented fidelity boundaries.

[Unreleased]: https://github.com/electblake/CouchJoust/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/electblake/CouchJoust/compare/v2.0.0-alpha.1...v2.0.0
[2.0.0-alpha.1]: https://github.com/electblake/CouchJoust/compare/v1.0.0...v2.0.0-alpha.1
[1.0.0]: https://github.com/electblake/CouchJoust/releases/tag/v1.0.0
