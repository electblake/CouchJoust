#!/usr/bin/env python3
"""Validate archive integrity, source data counts, PNG index grids and PCM files.
Usage: python tools/verify_package.py --package-dir .
Requires Pillow. Does not claim full-game or cabinet comparison validation.
"""
import argparse
import gzip
import hashlib
import json
import wave
from pathlib import Path
from PIL import Image

ap = argparse.ArgumentParser(description=__doc__)
ap.add_argument('--package-dir', type=Path, required=True)
ap.add_argument('--skip-file-manifest', action='store_true')
args = ap.parse_args()
root = args.package_dir.resolve()
checks = []

def load(path):
    return json.loads((root / path).read_text())

if not args.skip_file_manifest:
    manifest = load('FILE_MANIFEST.json')
    for item in manifest['files']:
        p = root / item['path']
        assert p.is_file(), str(p)
        assert p.stat().st_size == item['bytes'], str(p)
        assert hashlib.sha256(p.read_bytes()).hexdigest() == item['sha256'], str(p)
    checks.append(f"SHA256 and size validated for {len(manifest['files'])} files")

g = load('data/gameplay_tables.json')
assert len(g['waves']) == 90 and g['wave_table_repeat_entry'] == 81
assert len(g['dynamic_adjustments']) == 28
assert len(g['egg_wave_positions']) == 69
assert g['waves'][0]['raw_hex'] == ['30','01','00','02']
assert g['waves'][4]['egg_count'] == 12
checks.append('90 waves, 81–90 repeat, 28 dynamic records and 69 egg positions validated')

p = load('data/physics_tables.json')
assert len(p['ground_states']) == 13
assert len(p['flight_velocities_q8']) == 9
checks.append('13 ground states and 9 horizontal flight velocities validated')

text_catalog=load('data/text_catalog.json')
assert len(text_catalog['phrases'])==139
assert len(text_catalog['text_pages'])==30
assert len(text_catalog['default_score_entries'])==41
checks.append('139 literal phrases, 30 text pages and 41 default score records present')

a = load('assets/graphics/asset-manifest.json')
assert len(a['collision_profiles']) == 43
assert len(a['font_glyphs']) == 99
pngs = 0
for group,folder in [('sprites','sprites'),('terrain','sprites'),('font_glyphs','fonts')]:
    for name,item in a[group].items():
        im = Image.open(root/'assets/graphics'/folder/f'{name}.png')
        assert im.mode == 'P', name
        assert im.size == (item['width_pixels'],item['height_pixels']), name
        rows = [''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)]
        assert rows == item['index_rows'], name
        pngs += 1
bottom=a['compressed_cliff']
im=Image.open(root/'assets/graphics/sprites/_COMCL5.png')
assert im.size == (bottom['width_pixels'],bottom['height_pixels'])
assert [''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)] == bottom['index_rows']
pngs += 1
effects = load('assets/graphics/special-effects.json')
for group in ['pterodactyl_ashes','cliff_debris']:
    for item in effects[group]['frames']:
        im = Image.open(root/'assets/graphics/sprites'/f"{item['name']}.png")
        rows = [''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)]
        assert rows == item['index_rows'], item['name']
        assert all(len(r)==im.width for r in item['write_mask_rows'])
        pngs += 1
checks.append(f'{pngs} sprite/terrain/font/effect PNGs match exact index grids; 43 collision profiles present')

audio = load('assets/audio/audio-manifest.json')
assert len(audio['commands']) == 39
assert len(audio['events']) == 40
wav_count = 0
control_only = 0
for group in ['commands','events']:
    for name,item in audio[group].items():
        if 'file' not in item:
            assert item['control_only']
            control_only += 1
            continue
        path = root/'assets/audio'/item['file']
        assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'], name
        with wave.open(str(path),'rb') as w:
            assert (w.getnchannels(),w.getsampwidth(),w.getframerate())==(1,2,48000),name
            assert abs(w.getnframes()/48000-item['duration_seconds'])<1e-9,name
        assert not item['truncated'], name
        assert item['returned_to_idle'], name
        with gzip.open(root/'assets/audio'/item['trace'],'rt') as f:
            trace = json.load(f)
        assert trace['commands'] == item['commands'], name
        assert all(trace['writes'][i][0] <= trace['writes'][i+1][0] for i in range(len(trace['writes'])-1)),name
        wav_count += 1
assert wav_count == 76 and control_only == 3
checks.append('76 mono 48kHz/16bit WAVs and DAC traces validated; all captures completed, 3 control-only events retained')

print(json.dumps({'status':'PASS','checks':checks,'scope':'Package integrity and extracted asset/data checks, not a playable-game or physical-cabinet comparison'},indent=2))
