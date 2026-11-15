#!/usr/bin/env python3
"""Extract Joust V4 gameplay tables using Python's standard library.

Usage:
    python extract_gameplay_tables.py --source-dir reference/source --output-dir data

Expected source: synamaxmusic/joust commit 87e22aab945709fa107df609c0efcb839a17c1f9.
All numeric table contents are parsed from JOUSTRV4.ASM. Derived difficulty
snapshots implement the per-wave signed clamp in IWAVE; they are labeled derived.
"""
import argparse
import ast
import csv
import hashlib
import json
import pathlib
import re

COMMIT = '87e22aab945709fa107df609c0efcb839a17c1f9'
EXPECTED_SOURCE_SHA256 = 'bc8b177d44e5bba5e5f9f4687c32c007309d9f476ca0e7f1b67cc413fe409ec3'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-dir', type=pathlib.Path, required=True)
parser.add_argument('--output-dir', type=pathlib.Path, required=True)
args = parser.parse_args()
source_path = args.source_dir / 'JOUSTRV4.ASM'
source_bytes = source_path.read_bytes()
source_hash = hashlib.sha256(source_bytes).hexdigest()
if source_hash != EXPECTED_SOURCE_SHA256:
    parser.error(f'{source_path}: source SHA-256 {source_hash} differs from pinned commit {COMMIT}; retain original file bytes and line endings')
src = source_bytes.decode('utf-8').splitlines()
args.output_dir.mkdir(parents=True, exist_ok=True)

constants = {}

def value(expression):
    expression = re.sub(r'\$([0-9a-fA-F]+)', lambda m: str(int(m[1], 16)), expression)
    expression = re.sub(r'\b0+(\d+)\b', lambda m: str(int(m[0], 10)), expression)
    tree = ast.parse(expression.strip(), mode='eval')
    def calculate(node):
        if isinstance(node, ast.Expression):
            return calculate(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, int):
            return node.value
        if isinstance(node, ast.Name):
            return constants[node.id]
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.USub, ast.UAdd)):
            return calculate(node.operand) * (-1 if isinstance(node.op, ast.USub) else 1)
        if isinstance(node, ast.BinOp):
            left, right = calculate(node.left), calculate(node.right)
            if isinstance(node.op, ast.Add):
                return left + right
            if isinstance(node.op, ast.Sub):
                return left - right
            if isinstance(node.op, ast.Mult):
                return left * right
            if isinstance(node.op, ast.Div):
                return abs(left) // abs(right) * (-1 if (left < 0) != (right < 0) else 1)
        raise ValueError(ast.dump(node))
    return calculate(tree)

for line in src:
    match = re.match(r'^(WB\w+)\s+EQU\s+([^;]+)', line)
    if match:
        constants[match[1]] = value(match[2])
assert constants['WBCLS'] == 240

def signed(number, bits):
    return ((number + 2 ** (bits - 1)) % (2 ** bits)) - 2 ** (bits - 1)

def link(line, end=None):
    result = f'https://github.com/synamaxmusic/joust/blob/{COMMIT}/JOUSTRV4.ASM#L{line}'
    return result + (f'-L{end}' if end else '')

def region(start_label, end_label):
    active = False
    for line_number, line in enumerate(src, 1):
        if line.startswith(end_label):
            break
        if line.startswith(start_label):
            active = True
        if active and not line.lstrip().startswith(';'):
            yield line_number, line

def fields(line, directive):
    text = line.split(directive, 1)[1].split(';', 1)[0].strip()
    return [value(item) for item in text.split(',')]

waves = []
repeat_entry = None
for line_number, line in region('WAVTBL', 'WTBEND'):
    if 'FCB' not in line:
        continue
    data = fields(line, 'FCB')
    assert len(data) == 4 and all(0 <= n <= 255 for n in data)
    if line.startswith('WTBRST'):
        repeat_entry = len(waves) + 1
    type_counts, lord_and_quota, pterodactyls, status = data
    egg_wave = (status & 14) == 8
    wave = {
        'entry': len(waves) + 1,
        'source_line': line_number,
        'raw_hex': [f'{n:02X}' for n in data],
        'bounders': type_counts >> 4,
        'hunters': type_counts & 15,
        'shadow_lords': lord_and_quota >> 4,
        'initial_pursuer_limit': lord_and_quota & 15,
        'wave_pterodactyls': pterodactyls,
        'status': status,
        'immediate_pursuit': bool(status & 1),
        'special': {0:'normal', 2:'intro', 4:'team_or_survival', 6:'gladiator', 8:'egg', 10:'pterodactyl'}[status & 14],
        'disabled_cliffs': [name for bit, name in [(16,'CLIF1L'), (32,'CLIF1R'), (64,'CLIF2'), (128,'CLIF4')] if status & bit],
        'source': link(line_number),
    }
    if egg_wave:
        wave['egg_count'] = 12
        wave['hatch_type'] = 'shadow_lord' if type_counts == 0 else ('hunter' if type_counts & 15 else 'bounder')
        wave['concurrent_hatch_cap'] = (lord_and_quota >> 4) if type_counts == 0 else ((type_counts & 15) or (type_counts >> 4))
    waves.append(wave)
assert len(waves) == 90 and repeat_entry == 81
assert waves[0]['raw_hex'] == ['30', '01', '00', '02']
assert waves[4]['egg_count'] == 12 and waves[4]['concurrent_hatch_cap'] == 6
assert waves[59]['hatch_type'] == 'shadow_lord' and waves[59]['concurrent_hatch_cap'] == 6

names = ['LAVTIM','LAVGRA','LNTLAV','EGGWT','EGGWT2','BODNRG','BODNDI','BODNVY','BOUPRG','BOUPDI','BOUPWD','BOUPWU','BOLETM','HUDNRG','HUDNDI','HUDNVY','HUUPRG','HUUPDI','HUUPWD','HUUPWU','HUUPVY','HULETM','SHDNRG','SHUPRG','SHUPVY','SHUPTM','SHLETM','SHCLTM']
dynamic = []
for line_number, line in region('DYTBL', 'DYEND'):
    if 'DYWORD' not in line:
        continue
    data = fields(line, 'DYWORD')
    assert len(data) == 8
    first, middle, last, increment, endpoint, periods1, periods2, periods3 = data
    packed_periods = f'{periods1:04X}{periods2:04X}{periods3:02X}'
    dynamic.append({
        'symbol': names[len(dynamic)],
        'source_line': line_number,
        'start_u16': [n & 65535 for n in (first,middle,last)],
        'start_s16': [signed(n,16) for n in (first,middle,last)],
        'start_hex': [f'{n & 65535:04X}' for n in (first,middle,last)],
        'increment_s8': signed(increment,8),
        'end_u16': endpoint & 65535,
        'end_s16': signed(endpoint,16),
        'end_hex': f'{endpoint & 65535:04X}',
        'wave_intervals_by_difficulty': [int(n,16) for n in packed_periods],
        'period_hex': packed_periods,
        'source': link(line_number),
    })
assert len(dynamic) == 28
assert all(len(item['wave_intervals_by_difficulty']) == 10 for item in dynamic)

def values_for_wave(difficulty, wave):
    assert 0 <= difficulty <= 9 and wave >= 1
    result = []
    band = 0 if difficulty <= 3 else (1 if difficulty <= 6 else 2)
    for entry in dynamic:
        current = entry['start_s16'][band]
        countdown = 2
        for _ in range(2, wave + 1):
            if countdown:
                countdown -= 1
            if countdown == 0:
                countdown = entry['wave_intervals_by_difficulty'][difficulty]
                candidate = signed(current + entry['increment_s8'],16)
                current = max(candidate,entry['end_s16']) if entry['increment_s8'] < 0 else min(candidate,entry['end_s16'])
        result.append({'symbol':entry['symbol'], 'value_s16':current, 'value_hex':f'{current & 65535:04X}', 'next_change_countdown':countdown})
    return result

positions = []
position_header = None
cliff_names = ['CLIF1L','CLIF1R','CLIF2','CLIF3U','CLIF3L','CLIF3R','CLIF4','CLIF5']
cliff_index = 0
for line_number, line in region('EGGLNT', 'DEATH4'):
    if 'FCB' in line:
        position_header = (line_number, fields(line,'FCB'))
        if position_header[1][0] == 255:
            break
    elif 'FDB' in line:
        assert position_header is not None
        header_line, (end_index, bottom_y) = position_header
        (base_x,) = fields(line,'FDB')
        start_index = len(positions)
        for index in range(start_index,end_index):
            positions.append({'index':index, 'cliff':cliff_names[cliff_index], 'x':index*8+base_x, 'y':bottom_y, 'source':link(header_line,line_number)})
        cliff_index += 1
assert len(positions) == 69 and cliff_index == 8
assert positions[0]['x'] == 9 and positions[37]['x'] == 289 and positions[-1]['x'] == 230

groups = []
start = 0
for line_number, line in region('EGLEDG','EGGLNT'):
    if 'FCB' in line:
        (end,) = fields(line,'FCB')
        groups.append([start,end-1])
        start = end
assert len(groups) == 6 and groups[-1] == [46,68]

bait_delays = []
for line_number,line in region('BAITBL','BAISBL'):
    if 'FDB' in line:
        (delay,) = fields(line,'FDB')
        bait_delays.append(delay)
assert bait_delays == [7,7,7,7,7,7,22,37,52,112,112,225,337,450]

result = {
    'source_commit': COMMIT,
    'source_file': 'JOUSTRV4.ASM',
    'source_file_sha256': source_hash,
    'wave_fields_note': 'On egg-wave rows the bounder/hunter/shadow fields encode hatch species and concurrent hatch capacity, not initial mounted enemy spawns; use hatch_type and concurrent_hatch_cap.',
    'wave_count': len(waves),
    'wave_table_repeat_entry': repeat_entry,
    'displayed_wave_counter': 'decimal modulo 100, independent of table entry',
    'waves': waves,
    'dynamic_adjustments': dynamic,
    'derived_default_difficulty_5_values_at_wave': {str(w):values_for_wave(5,w) for w in [1,2,3,4,5,10,20,40,90]},
    'egg_wave_positions': positions,
    'egg_ledge_groups': groups,
    'bait_times_in_eight_tick_units': bait_delays,
}
(args.output_dir / 'gameplay_tables.json').write_text(json.dumps(result,indent=2)+'\n', encoding='utf-8')
with (args.output_dir / 'waves.csv').open('w', newline='', encoding='utf-8') as file:
    writer = csv.writer(file)
    writer.writerow(['entry','raw0','raw1','raw2','raw3','bounders','hunters','shadow_lords','pursuer_limit','pterodactyls','special','disabled_cliffs','hatch_type','hatch_cap','source_line'])
    for item in waves:
        writer.writerow([item['entry'],*item['raw_hex'],item['bounders'],item['hunters'],item['shadow_lords'],item['initial_pursuer_limit'],item['wave_pterodactyls'],item['special'],' '.join(item['disabled_cliffs']),item.get('hatch_type',''),item.get('concurrent_hatch_cap',''),item['source_line']])
print(f'Extracted {len(waves)} waves, {len(dynamic)} dynamic parameters, {len(positions)} egg positions, and {len(bait_delays)} baiter delays.')
