#!/usr/bin/env python3
"""Combine the included section sources and build the illustrated reading copy.
Usage: python tools/build_docs.py --package-dir .
Requires Pandoc on PATH for HTML output. All art and source links remain local.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path

ap = argparse.ArgumentParser(description=__doc__)
ap.add_argument('--package-dir', type=Path, required=True)
root = ap.parse_args().package_dir.resolve()
parts = []
for path in sorted((root/'docs/sections').glob('*.md')):
    s = path.read_text()
    if path.name.startswith('05_'):
        s = re.sub(r'^(#{1,5}) ',r'#\1 ',s,flags=re.M)
        s = s.replace('The `joust-assets` directory','The package’s `assets` directories')
        s = s.replace('### 1. Delivered asset data\n','### 1. Delivered asset data\n\n![Original source sprite and terrain atlas](../assets/graphics/sprite-atlas.png)\n')
        s = s.replace('### 6. Original text and title screen\n','### 6. Original text and title screen\n\n![Original font glyphs](../assets/graphics/font-atlas.png)\n\n![Source-decoded title logo](../assets/graphics/title-logo-4x.png)\n')
        s = s.replace('### 6. Original text and title screen\n','### 6. Original text and title screen\n\nThe package also supplies `data/text_catalog.json`: all 139 literal phrases, 30 text pages with 67 placed entries, font-call contexts, operator defaults, champion name and 41 default score records. Character codes and termination flags are preserved alongside readable text. Two pages use the small font in their observed calls (`BADJ` and `AT13`); the other 28 observed page contexts use the large font. Use the recorded context because codes 47 and 48 have different meanings in the two fonts. Preserve literal spelling and punctuation from the decoded character lists instead of correcting source comments.\n')
        s = s.replace('### 4. Arena and platform artwork\n','### 4. Arena and platform artwork\n\n![Decoded pterodactyl dissolve and cliff debris](../assets/graphics/special-effects-atlas.png)\n')
        s = s.replace('Important companion paths:\n','Paths in this section are relative to `assets/graphics/`, except `audio/` which means the sibling `assets/audio/`; extraction scripts are in `tools/`. The audio renderer also reads the identical shared event table at `assets/sound-events.json`.\n\nImportant companion paths:\n')
    if path.name.startswith('04_'):
        s = s.replace('### Wave configuration and progression\n','### Wave configuration and progression\n\n![Complete 90-entry wave overview](figures/wave-overview.png)\n')
        s = s.replace('### Egg life cycle and remounting\n','### Egg life cycle and remounting\n\n![Rider, egg and replacement-mount lifecycle](figures/egg-lifecycle.png)\n')
    parts.append(s.strip())

g = json.loads((root/'data/gameplay_tables.json').read_text())
rows = ['## Complete wave-table appendix','',
        'All rows below are extracted from `JOUSTRV4.ASM WAVTBL`. “B/H/S” are bounder/hunter/shadow-lord counts on ordinary rows. On an egg row they instead select hatch species and capacity; every egg wave begins with 12 eggs. “Pursuit” is the pursuing quota, not a new enemy count. “Early” enables pursuit during introduction. The displayed wave counter is separate from this table entry.','',
        '| Entry | B/H/S | Pursuit | Early | Wave mode | Wave pteros | Removed cliffs | Egg hatch/cap |',
        '|---:|---|---:|---|---|---:|---|---|']
for w in g['waves']:
    egg = f"{w['hatch_type']} / {w['concurrent_hatch_cap']}" if w['special']=='egg' else '—'
    rows.append(f"| {w['entry']} | {w['bounders']}/{w['hunters']}/{w['shadow_lords']} | {w['initial_pursuer_limit']} | {'yes' if w['immediate_pursuit'] else 'no'} | {w['special']} | {w['wave_pterodactyls']} | {', '.join(w['disabled_cliffs']) or '—'} | {egg} |")
rows += ['', 'After entry 90, return to entry 81. `data/waves.csv` and `data/gameplay_tables.json` preserve source lines, raw encoded values and all fields.']
parts.append('\n'.join(rows))

parts.append('''## Source-file navigation

The full pinned source is included under `reference/source/`. Original names and source text are preserved. Use the local snapshot for source work; the document's web links point at the same commit.

| File | Relevant game content |
|---|---|
| `JOUSTRV4.ASM` | Game flow, movement, actors, AI, collisions, spawning, wave/difficulty tables, score and sound events. |
| `JOUSTI.ASM` | Original images, frame anchors, terrain, silhouette profiles, compressed island and special effects. |
| `SYSTEM.ASM` | Game-task ordering, sound arbitration, random primitive, default palette and lava animation. |
| `ATT.ASM` | Title geometry, marquee motion, palette changes and transition to the instruction demo. |
| `MESSAGE.ASM` | Bitmap fonts, character metrics and phrase rendering. |
| `PHRASE.ASM`, `MESSEQU.ASM`, `MESSEQU2.ASM` | Message IDs, literal character lists and text-page layout. |
| `TB12REV3.ASM` | Factory settings, score tables, initials entry and persistent/session records. |
| `RAMDEF.ASM`, `EQU.ASM` | Names and meanings of source game-state fields and settings; storage layout is not a native-app requirement. |
| `VSNDRM4.ASM` | Original sound generators, used offline to produce the supplied PCM. |
| `joust_mods.ASM` | Optional source modifications; the reference target keeps the original disabled defaults. |

`PROVENANCE.json` records source and extraction inputs. `FILE_MANIFEST.json` hashes the archive's deliverable files. The complete original repository snapshot has no repository-level LICENSE file at this pin; original attribution is preserved and this package does not assign a new license to those original materials. The separately derived sound preparation helper carries its upstream BSD notice in its own folder.
''')

md = '\n\n'.join(parts)+'\n'
# Make compact evidence tags directly clickable without losing their index.
evidence={m[1]:m[2] for m in re.finditer(r'^- \[([AP]\d{2})\] \[[^\]]+\]\((https://[^)]+)\)',md,re.M)}
md=re.sub(r'\[([AP]\d{2})\](?!\()',lambda m:f'[{m[1]}]({evidence[m[1]]})' if m[1] in evidence else m[0],md)
target = root/'docs/JOUST_GAME_SPEC.md'
target.write_text(md)
subprocess.run(['pandoc',str(target),'-f','markdown','-t','html5','--standalone','--toc','--toc-depth=3',
                '--metadata','pagetitle=Joust V4 — Native Kotlin Game Specification',
                '--css','style.css','-o',str(root/'docs/JOUST_GAME_SPEC.html')],check=True)
html_path=root/'docs/JOUST_GAME_SPEC.html'
html=html_path.read_text()
html=re.sub(r'(<nav id="TOC".*?</nav>)',r'<details id="contents"><summary>Open table of contents</summary>\1</details>',html,count=1,flags=re.S)
html=html.replace('<body>','<body>\n<p class="document-nav"><a href="../OPEN_ME.html">Package home</a> · <a href="../assets/ASSET_CATALOG.html">Graphics and audio catalog</a> · <a href="JOUST_GAME_SPEC.md">Markdown source</a></p>',1)
html_path.write_text(html)
print(json.dumps({'words':len(md.split()),'markdown_bytes':target.stat().st_size,'html_bytes':(root/'docs/JOUST_GAME_SPEC.html').stat().st_size}))
