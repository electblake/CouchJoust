"""Exercise real Wormhole pairing and iroh traffic on two installed Android TV apps."""
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
with host_path.open('w') as host_output, guest_path.open('w') as guest_output:
    host = subprocess.Popen([a.adb, '-s', a.host, 'shell', 'am', 'instrument', '-w', '-e',
                             'connectionRole', 'host', runner], stdout=host_output, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 100
    code_lines = []
    while not code_lines and host.poll() is None and time.monotonic() < deadline:
        time.sleep(0.25)
        code_lines = [line for line in host_path.read_text().splitlines()
                      if line.startswith('INSTRUMENTATION_STATUS: pairing_code=')]
    code = code_lines[0].split('=', 1)[1]
    guest = subprocess.Popen([a.adb, '-s', a.guest, 'shell', 'am', 'instrument', '-w', '-e',
                              'connectionRole', 'guest', '-e', 'code', code, runner],
                             stdout=guest_output, stderr=subprocess.STDOUT)
    host.wait(timeout=130)
    guest.wait(timeout=130)
for role, serial, path in [('host', a.host, host_path), ('guest', a.guest, guest_path)]:
    result = path.read_text()
    # Do not retain even expired pairing codes in the verification report.
    result = '\n'.join(line for line in result.splitlines() if 'pairing_code=' not in line)
    path.write_text(result)
    assert 'PASS connection prototype' in result, result
    assert 'Process crashed' not in result, result
    subprocess.run([a.adb, '-s', serial, 'pull',
                    f'/sdcard/Android/data/com.couchjoust/files/connection-{role}.png', str(a.output)], check=True)
    print(role, result)
