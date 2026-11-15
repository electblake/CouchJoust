#!/usr/bin/env python3
"""Generate a local asset browser and entry page. Python standard library only."""
import argparse
import html
import json
from pathlib import Path

ap=argparse.ArgumentParser(description=__doc__)
ap.add_argument('--package-dir',type=Path,required=True)
r=ap.parse_args().package_dir.resolve()
manifest=json.loads((r/'assets/graphics/asset-manifest.json').read_text())
audio=json.loads((r/'assets/audio/audio-manifest.json').read_text())
events=json.loads((r/'assets/sound-events.json').read_text())
style='''<style>
*{box-sizing:border-box}body{max-width:1220px;margin:auto;padding:36px 32px;font:16px/1.55 system-ui,sans-serif;background:#f2f4f7;color:#192b3b}h1{font-size:2.6rem;line-height:1.12;letter-spacing:-.035em}h2{margin-top:2.5rem;border-top:2px solid #cbd4df;padding-top:1rem}a{color:#176d92}nav{display:flex;flex-wrap:wrap;gap:14px;margin:20px 0}nav a,.tile{background:white;border:1px solid #d4dde5;border-radius:6px;padding:16px}nav a{display:block;text-decoration:none;font-weight:650}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:12px}.glyphs{grid-template-columns:repeat(auto-fill,minmax(95px,1fr))}.tile{min-height:145px;display:flex;flex-direction:column;align-items:center;justify-content:space-between;gap:10px}.pixel{image-rendering:pixelated;object-fit:contain;max-width:100%;max-height:160px;background:#05080b}.label{font:12px/1.4 ui-monospace,monospace;overflow-wrap:anywhere}.muted{color:#536574}.audio{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:12px}.sound{background:white;border:1px solid #d4dde5;padding:16px;border-radius:6px}.sound h3{margin:0 0 4px;font-size:1rem}audio{width:100%;height:36px;margin-top:10px}input{width:100%;padding:13px;font:inherit;border:1px solid #9eafbf;border-radius:6px;position:sticky;top:0;z-index:5;background:white}.hero{width:100%;max-height:330px;object-fit:contain;background:black;image-rendering:pixelated;border-radius:6px}.lead{max-width:850px}.badge{display:inline-block;padding:5px 10px;background:#e8d6b6;color:#64420c;border-radius:3px;font-size:.85rem}table{border-collapse:collapse;width:100%;background:white}td,th{border:1px solid #d4dde5;padding:10px;text-align:left}th{background:#203b50;color:white}@media(max-width:600px){body{padding:20px 14px}h1{font-size:2rem}}
</style>'''
head='<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
intro=f'''{head}<title>Joust V4 — build package</title>{style}<body>
<span class="badge">Original assets · native Kotlin specification · revision 4</span>
<h1>Joust, ready for the build agent.</h1>
<p class="lead">Decoded original graphics and sound, exact game tables, a detailed source-backed gameplay specification and reference code for an Android TV implementation. Source pinned to <code>87e22aab945709fa107df609c0efcb839a17c1f9</code>.</p>
<nav><a href="docs/JOUST_GAME_SPEC.html">Read illustrated specification</a><a href="assets/ASSET_CATALOG.html">Browse graphics &amp; play sounds</a><a href="START_HERE.md">Build-agent brief</a><a href="REPRODUCE.md">Regenerate and verify</a></nav>
<img class="hero" src="assets/graphics/title-logo-4x.png" alt="Title decoded from original source">
<h2>Included</h2><table><tr><th>Material</th><th>Contents</th></tr>
<tr><td>Original art</td><td>101 graphics/effect PNGs, 99 glyph PNGs, title raster/geometry, palette/index grids, frame offsets and 43 collision profiles.</td></tr>
<tr><td>Original-program sound</td><td>76 mono 48 kHz PCM WAVs: 39 command probes and 37 audible event captures. All 40 game events are mapped; 3 are control-only. Raw sample traces and generation tools included.</td></tr>
<tr><td>Game data</td><td>90 wave entries, 28 dynamic difficulty records, 69 legal egg slots, movement/terrain maps and original text/page layouts.</td></tr>
<tr><td>Game specification</td><td>Controls, movement math, collision, all enemy state families, eggs/remounts, waves, scoring, two players, trolls, pterodactyls, visuals, audio and Android TV integration.</td></tr>
<tr><td>Implementation references</td><td>Native Kotlin math/input/random helpers, pinned source snapshot, regeneration scripts and verification results.</td></tr></table>
<p class="muted">This is the primer and asset bundle, not a completed APK. Source analysis and asset checks are recorded; full-game replay and physical-TV validation remain implementation tasks. Audio is source-derived rendering, with precise extraction limitations in its README.</p>
</body></html>'''
(r/'OPEN_ME.html').write_text(intro)

sections=[f'{head}<title>Joust original asset catalog</title>{style}<body><nav><a href="../OPEN_ME.html">Package home</a><a href="../docs/JOUST_GAME_SPEC.html">Game specification</a><a href="audio/README.md">Audio semantics</a></nav><h1>Original asset catalog</h1><p>Pixel art is shown enlarged with nearest-neighbor sampling. Sound players use the delivered WAVs. Search matches source labels and descriptions.</p><input id="search" type="search" placeholder="Filter assets by name, for example OFLY, EGG, SNPLWD…" aria-label="Filter assets">']
for heading,folder,cls in [('Sprites, terrain and effects','sprites',''),('Original font glyphs','fonts',' glyphs')]:
    sections.append(f'<h2>{heading}</h2><div class="grid{cls}">')
    for p in sorted((r/'assets/graphics'/folder).glob('*.png')):
        rel=f'graphics/{folder}/{p.name}';name=html.escape(p.stem)
        info=manifest['sprites'].get(p.stem) or manifest['terrain'].get(p.stem) or manifest['font_glyphs'].get(p.stem)
        dims=f"{info['width_pixels']} × {info['height_pixels']} px" if info else 'Decoded source effect/body'
        width=min((info['width_pixels'] if info else 40)*4,180)
        sections.append(f'<a class="tile searchable" data-filter="{name.lower()}" href="{rel}"><img class="pixel" loading="lazy" width="{width}" src="{rel}" alt="{name}"><span class="label">{name}<br>{dims}</span></a>')
    sections.append('</div>')

for group,heading in [('events','Game sound events'),('commands','Individual sound commands')]:
    sections.append(f'<h2>{heading}</h2><div class="audio">')
    for name,item in audio[group].items():
        event=events.get(name,{})
        description=event.get('comment',event.get('description',''))
        if group=='commands':description=', '.join(item.get('event_uses',[])) or 'Original command; no normal event reference'
        description=html.escape(str(description))
        sections.append(f'<section class="sound searchable" data-filter="{html.escape((name+" "+description).lower())}"><h3>{name}</h3><div class="muted">{description}</div>')
        if 'priority' in item:sections.append(f'<div>Priority {item["priority"]}</div>')
        if 'file' in item:
            sections.append(f'<div>{item["duration_seconds"]:.4f} seconds · 48 kHz mono</div><audio controls preload="none" src="audio/{item["file"]}"></audio>')
        else:sections.append('<div>Control-only event — no invented sound clip.</div>')
        sections.append('</section>')
    sections.append('</div>')
sections.append('''<script>
document.querySelector('#search').addEventListener('input', e=>{const q=e.target.value.toLowerCase();document.querySelectorAll('.searchable').forEach(x=>x.style.display=x.dataset.filter.includes(q)?'':'none')});
document.addEventListener('play',e=>{if(e.target.tagName==='AUDIO')document.querySelectorAll('audio').forEach(a=>{if(a!==e.target)a.pause()})},true);
</script></body></html>''')
(r/'assets/ASSET_CATALOG.html').write_text('\n'.join(sections))
print('Wrote OPEN_ME.html and graphics/audio catalog.')
