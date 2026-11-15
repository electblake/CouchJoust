# Original Joust audio assets

These WAV files were rendered from the original sound program rebuilt from the pinned Joust source, not recorded from a video and not approximated with substitute instruments. The Android TV Kotlin game consumes the WAVs or timestamped sample data. All extraction code is an offline preparation tool.

## What is included

- `commands/`: 39 command probes, `command_01.wav` through `command_27.wav`. There are 38 audible commands; `1f` is the sound-program reset/control case and has no varying signal.
- `events/`: 37 audible game-event sequences. The three control-only events (`SNPTREF`, `SNEFAL`, `SNPFAL`) are represented in metadata instead of pretending they are sounds.
- `dac-traces/`: 76 gzip-compressed JSON traces of source-produced sample levels and integer-cycle times. Each contains its command schedule and original unsigned 8-bit levels.
- `audio-manifest.json`: per-file SHA256, exact schedule, priority, rate, duration, first/last write times, signal range, completion status and event-to-command mapping.
- `reference/video_sound_rom_4_std_780.ic12`: the 4096-byte source-built input used for extraction, verified against the official MAME file hash. No ROM download was used.
- `tools/`: source renderer, source-rebuild helper and self-contained offline execution helper, with its license and verification results. No compiler or MAME checkout is required in the archive. To regenerate, use an available C++17 compiler and Python with NumPy.

The WAV format is 48,000 Hz, mono, signed 16-bit PCM. Every audible routine reached its natural ending; no file is a cutoff pretending to be a loop. Command `04` has a natural duration near 17.12 seconds and is included even though it is not referenced by normal game events. The complete `SNHIGH` sequence is about 49.92 seconds. A single event WAV includes its source hold intervals, so its file duration can exceed the audible waveform. Use command assets plus the event scheduler for gameplay, and event WAVs as reference captures or whole-event playback when appropriate.

## Playback semantics

The original game has one sound voice. Higher or equal priority accepted requests replace the pending/current event. A lower priority request is ignored until the active hold timer expires. Priorities 0–127 are suppressed outside active gameplay. The priority timer controls arbitration and command scheduling; it is not an instruction to cut the waveform when a hold ends.

Semantic command `00` does not start or interrupt anything; it extends an event's timing. Semantic command `3f` interrupts the current generator and holds its last sample, ending the changing sound. Do not play either as a new effect. Semantic `1f` resets the sound program and is not used by the normal event table.

All isolated command and event captures begin from a deterministic reset state. State is retained between commands within an event capture. Live sound history can change random-noise phase and the DAC level held before the first write of a replacement sound. The raw traces and `first_dac_write_seconds` let a native sample player preserve the previous held level during that startup interval. Do not repeatedly add a silent/zero prefix to a preempting effect. The source noise state can also be reproduced natively if exact variation across arbitrary histories is required.

Keep natural waveform lengths separate from hold ticks. For example, `SNGS` reserves only one game tick but its original sound lasts about 1.84 seconds; later accepted events may interrupt it. The pterodactyl death event retriggers the death command at successive 15, 15, 7 and 7 tick intervals, then holds the final command for 90 ticks. `SNPCR1` and `SNPCR2` preserve their separate 30/43 tick introductions and different continuation commands.

## Precision and limits

The source-built sound image has CRC32 `f1835bdd`, SHA1 `af7c066d2949d36b87ea8c425ca7d12f82b5c653`, and SHA256 `16e6fe7d3f0bd84b2a23d97387ae1cca15bd8cb9ce0471a668cdbcca4619ce4f`.

Timing uses the reference sound clock divided by four: 894,886.25 execution cycles per second. Game-event holds use the exact nominal game tick, 16.64 ms (60.09615384615385 Hz). The extraction helper follows MAME's original 6800 instruction semantics and instruction cycle table, including interrupt entry, flags, indexed addressing and interrupt deferral. All 40 command/control cases and targeted flag/preemption checks passed; see the included verification JSON. This is not an independent recording comparison against a physical cabinet.

DAC writes are timestamped at instruction completion, rather than modeled at the individual within-instruction bus phase. That leaves a small residual uncertainty in the location of sample edges while preserving instruction-loop durations. WAV conversion integrates the held sample levels across each 48 kHz sample interval. It uses the reference renderer's 0.25 gain, with no per-clip normalization, invented sound, equalization, fade or speaker/analogue-circuit coloration. Raw sample traces preserve the extraction result if a different resampler or analog model is desired. These files should be described as source-derived reference audio, not certified bit-identical cabinet recordings.

## Regeneration

From `audio/tools/offline6800`, build the portable helper:

```sh
g++ -std=c++17 -O3 -fPIC -shared -Wall -Wextra -o libjoust6800.so m6800_offline.cpp
```

From the asset directory, after installing NumPy in the chosen Python environment:

```sh
python3 audio/tools/render_audio.py
```

The included source-built sound image is already sufficient input. To rebuild that image from a source checkout using Macroassembler AS:

```sh
python3 audio/tools/rebuild_sound_image.py /path/to/joust-source --asl /path/to/asl --p2bin /path/to/p2bin
```

The compiler and assembler are build-time tools only. The native Kotlin game does not use them.

## Primary references

- [Joust source sound program](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/VSNDRM4.ASM)
- [Game sound event table](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L8053-L8133)
- [Priority arbitration](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L754-L773)
- [Event dispatch and holds](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L174-L188)
- [Official MAME sound clock and reference output](https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams.cpp#L1531-L1583)
- [Official MAME sound-file checksum](https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams.cpp#L3007-L3024)
