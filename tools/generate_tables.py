"""Compile the pinned decoded data into platform-independent Kotlin tables."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
primer = root / 'joust-kotlin-primer'
physics = json.loads((primer / 'data/physics_tables.json').read_text())
game = json.loads((primer / 'data/gameplay_tables.json').read_text())
art = json.loads((primer / 'assets/graphics/asset-manifest.json').read_text())
text_data = json.loads((primer / 'data/text_catalog.json').read_text())
out = root / 'app/src/main/java/com/couchjoust/core'
out.mkdir(parents=True, exist_ok=True)

def ints(values):
    return 'intArrayOf(' + ','.join(map(str, values)) + ')'

def quote(s):
    return json.dumps(s)

lines = ['package com.couchjoust.core', '// Generated from pinned source data by tools/generate_tables.py.',
         'data class Ground(val wait:Int,val routine:String,val reverse:Int,val neutral:Int,val forward:Int,val speed:Int)',
         'data class WaveRecord(val counts:IntArray,val pursuers:Int,val pteros:Int,val early:Boolean,val special:String,val disabled:Set<String>)',
         'data class Adjustment(val name:String,val start:Int,val increment:Int,val end:Int,val period:Int)',
         'data class Frame(val sprite:String,val dx:Int,val dy:Int,val profile:String)',
         'data class TextLine(val x:Int,val y:Int,val color:Int,val phrase:Int)',
         'object Tables {']
for key, value in physics['masks'].items():
    lines.append(f'val {key} = {ints(value["values"])}')
labels = [s['label'] for s in physics['ground_states']]
lines.append('val ground = arrayOf(')
for s in physics['ground_states']:
    lines.append(f'Ground({s["wait"]},{quote(s["routine"])},{labels.index(s["reverse"])},{labels.index(s["neutral"])},{labels.index(s["forward"])},{s["flight_index"]}),')
lines.append(')')
lines.append('val waves = arrayOf(')
for w in game['waves']:
    disabled = 'setOf<String>(' + ','.join(map(quote,w['disabled_cliffs'])) + ')'
    lines.append(f'WaveRecord({ints([w["bounders"],w["hunters"],w["shadow_lords"]])},{w["initial_pursuer_limit"]},{w["wave_pterodactyls"]},{str(w["immediate_pursuit"]).lower()},{quote(w["special"])},{disabled}),')
lines.append(')')
lines.append('val adjustments = arrayOf(')
for d in game['dynamic_adjustments']:
    lines.append(f'Adjustment({quote(d["symbol"])},{d["start_s16"][1]},{d["increment_s8"]},{d["end_s16"]},{d["wave_intervals_by_difficulty"][5]}),')
lines.append(')')
lines.append('val eggPositions = arrayOf(' + ','.join(ints([e['x'],e['y']]) for e in game['egg_wave_positions']) + ')')
lines.append('val eggGroups = arrayOf(' + ','.join(ints(g) for g in game['egg_ledge_groups']) + ')')
lines.append('val baitTimes = '+ints(game['bait_times_in_eight_tick_units']))
lines.append('val profiles = mapOf(')
for name, profile in art['collision_profiles'].items():
    rows = ','.join('null' if r is None else ints(r) for r in profile['rows'])
    lines.append(f'{quote(name)} to arrayOf<IntArray?>({rows}),')
lines.append(')')
lines.append('val frames = mapOf(')
for name, descriptors in art['animation_descriptors'].items():
    frames = ','.join(f'Frame({quote(d["sprite"])},{d["x_offset_pixels"]},{d["y_offset_pixels"]},{quote(d["collision_profile"] or "")})' for d in descriptors)
    lines.append(f'{quote(name)} to arrayOf({frames}),')
lines.append(')')
lines.append('val defaultScores = listOf(' + ','.join(f'{s["score"]}L to {quote(s["initials"])}' for s in text_data['default_score_entries']) + ')')
lines.append('val phrases = mapOf(' + ','.join(f'{p["id"]} to {ints(p["codes"])}' for p in text_data['phrases']) + ')')
lines.append('val messages = mapOf(' + ','.join(f'{quote(symbol)} to {quote(p["text"])}' for p in text_data['phrases'] for symbol in p['message_symbols']) + ')')
lines.append('val textPages = mapOf(')
for p in text_data['text_pages']:
    entries = ','.join(f'TextLine({e["x"]},{e["y"]},{e["palette_index_even"]},{e["phrase_id"]})' for e in p['entries'])
    for symbol in p['text_symbols']:
        lines.append(f'{quote(symbol)} to listOf({entries}),')
lines.append(')')
lines.append('}')
(out / 'Tables.kt').write_text('\n'.join(lines)+'\n')

# Retain only pure arithmetic, without custom input-error branches.
for filename in ['GameMath.kt', 'JoustRandom.kt', 'ControllerState.kt']:
    source = (primer / 'reference/kotlin' / filename).read_text()
    source = source.replace('package joust.reference', 'package com.couchjoust.core')
    source = '\n'.join(line for line in source.splitlines() if 'require(' not in line)
    (out / filename).write_text(source+'\n')

from PIL import Image
resources = root / 'app/src/main/res/drawable-nodpi'
resources.mkdir(parents=True, exist_ok=True)
logo = Image.open(primer / 'assets/graphics/title-logo.png').convert('RGBA')
banner = Image.new('RGBA',(320,180),(0,0,0,255))
logo.thumbnail((300,140), Image.Resampling.NEAREST)
banner.alpha_composite(logo,((320-logo.width)//2,(180-logo.height)//2))
banner.convert('RGB').save(resources/'banner.png')
icon = Image.new('RGBA',(96,96),(0,0,0,255))
bird = Image.open(primer/'assets/graphics/sprites/OFLY3R.png').convert('RGBA')
bird = bird.resize((bird.width*4,bird.height*4),Image.Resampling.NEAREST)
icon.alpha_composite(bird,((96-bird.width)//2,(96-bird.height)//2))
icon.save(resources/'icon.png')
