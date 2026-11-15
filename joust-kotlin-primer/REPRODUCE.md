# Reproduce and validate the extracted material

Run commands from the extracted package root. The Android application uses the finished PNG/JSON/WAV assets; these tools are offline preparation utilities.

## Package check

Install Pillow in the Python environment you use for extraction, then:

```sh
python tools/verify_package.py --package-dir .
```

`FILE_MANIFEST.json` hashes the package files except itself. Do not overwrite the delivered data before checking integrity. For regeneration, write to a new output directory and compare the result.

## Graphics and title/effects

```sh
python tools/extract_assets.py reference/source regenerated/graphics
python tools/extract_effects_title.py reference/source regenerated/graphics
```

Requires Pillow. Atlas-label font appearance is a presentation detail; use the optional `--label-font` argument to select a local TrueType font. The default uses Pillow's available fallback. Original sprite/font pixels, palette indices, descriptor data, title geometry and collision rows do not depend on the atlas-label font.

## Gameplay, physics and text

```sh
python tools/extract_gameplay_tables.py --source-dir reference/source --output-dir regenerated/data
python tools/extract_physics_tables.py --source-dir reference/source --output-dir regenerated/data --verify-against data/physics_tables.json
python tools/extract_text_catalog.py --source-dir reference/source --output-dir regenerated/data
```

These use Python's standard library. Preserve the original source file bytes. The gameplay extractor verifies the pinned file hash and rejects modified or newline-normalized input, avoiding false pinned-source citations.

## Audio

Keep `assets/audio/tools/offline6800/` together with its Python wrapper and `assets/audio/reference/` source-built input. Requires Python with NumPy and a C++17 compiler for the offline helper. Build from `assets/audio/tools/offline6800/`:

```sh
g++ -std=c++17 -O3 -fPIC -shared -Wall -Wextra -o libjoust6800.so m6800_offline.cpp
```

Then from the package root:

```sh
python assets/audio/tools/render_audio.py --events assets/sound-events.json --out regenerated/audio
```

All 39 probes and 37 audible event sequences are generated, plus the 3 control-only event records. The supplied original-program input already has the verified checksum. To rebuild that 4 KB input from the bundled source using separately available Macroassembler AS tools:

```sh
python assets/audio/tools/rebuild_sound_image.py reference/source --asl /absolute/path/to/asl --p2bin /absolute/path/to/p2bin
```

`assets/audio/README.md` documents gain, sample timing, hold intervals, command controls and the known extraction limits. Do not assign loop points to a capture or silently normalize each clip.

## Explanatory figures and document

The source-decoded art is independent of the annotated figures. To regenerate the latter, install matplotlib, NumPy and Pillow and make Graphviz `dot` available:

```sh
python tools/make_figures.py --package-dir .
```

To rebuild the combined Markdown and HTML from the included section sources, make Pandoc available:

```sh
python tools/build_docs.py --package-dir .
```

No compiler, assembler, Python installation, reference emulator or asset-decoding tool belongs in the Android runtime. The preparation helper's original instruction-level code is bundled only to make the delivered sound files auditable and regenerable.
