"""Pair two real app processes, play, and compare every shared simulation tick."""
import argparse
from pathlib import Path
import subprocess
import time

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--host', required=True)
p.add_argument('--guest', required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
runner = 'com.couchjoust.test/com.couchjoust.GameplayInstrumentation'
host_path = a.output / 'host.txt'
guest_path = a.output / 'guest.txt'
with host_path.open('w') as ho, guest_path.open('w') as go:
    host = subprocess.Popen([a.adb, '-s', a.host, 'shell', 'am', 'instrument', '-w', '-e', 'networkRole', 'host', runner], stdout=ho, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 100
    codes = []
    while not codes and host.poll() is None and time.monotonic() < deadline:
        time.sleep(.25)
        codes = [line for line in host_path.read_text().splitlines() if line.startswith('INSTRUMENTATION_STATUS: pairing_code=')]
    code = codes[0].split('=', 1)[1]
    guest = subprocess.Popen([a.adb, '-s', a.guest, 'shell', 'am', 'instrument', '-w', '-e', 'networkRole', 'guest', '-e', 'code', code, runner], stdout=go, stderr=subprocess.STDOUT)
    host.wait(timeout=400)
    guest.wait(timeout=400)
for role, serial, report in [('host', a.host, host_path), ('guest', a.guest, guest_path)]:
    result = '\n'.join(line for line in report.read_text().splitlines() if 'pairing_code=' not in line)
    report.write_text(result + '\n')
    assert 'PASS network match:' in result and 'Process crashed' not in result, result
    for ext in ('png', 'txt'):
        subprocess.run([a.adb, '-s', serial, 'pull', f'/sdcard/Android/data/com.couchjoust/files/network-{role}.{ext}', str(a.output)], check=True)
    print(result)
def states(role):
    return {line.split(',', 1)[0]: line for line in (a.output / f'network-{role}.txt').read_text().splitlines()}
h, g = states('host'), states('guest')
shared = h.keys() & g.keys()
assert len(shared) >= 720
for tick in shared:
    assert h[tick] == g[tick], (tick, h[tick], g[tick])
assert any(t.startswith('2|') for t in shared)
summary = f'PASS identical game-state snapshots for {len(shared)} shared ticks across two matches\n'
(a.output / 'comparison.txt').write_text(summary)
print(summary)
