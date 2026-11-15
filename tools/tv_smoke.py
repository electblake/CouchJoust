"""Exercise Android's input dispatcher and record an actual rendered TV session."""
import argparse
from pathlib import Path
import subprocess
import time

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
args = p.parse_args()
out = Path(__file__).resolve().parents[1] / 'artifacts' / 'tv-smoke'
out.mkdir(parents=True, exist_ok=True)
base = [args.adb, '-s', args.serial]

def adb(*command):
    return subprocess.run(base + list(command), check=True, capture_output=True).stdout

adb('shell', 'am', 'force-stop', 'com.couchjoust')
adb('shell', 'am', 'start', '-W', '-n', 'com.couchjoust/.MainActivity')
adb('shell', 'input', 'keyevent', 'DPAD_CENTER')
recording = subprocess.Popen(base + ['shell', 'screenrecord', '--time-limit', '30', '/sdcard/couchjoust-gameplay.mp4'])
adb('shell', 'input', 'keyevent', '--duration', '900', 'DPAD_RIGHT')
for index in range(48):
    direction = 'DPAD_RIGHT' if index // 12 % 2 == 0 else 'DPAD_LEFT'
    adb('shell', 'input', 'keycombination', '-t', '90', direction, 'DPAD_CENTER')
    time.sleep(0.08)
    if index % 12 == 0:
        print(f'OS input sequence {index + 1}/48', flush=True)
adb('shell', 'input', 'keyevent', 'BACK')
time.sleep(0.5)
adb('shell', 'screencap', '-p', '/sdcard/couchjoust-pause.png')
adb('pull', '/sdcard/couchjoust-pause.png', str(out / 'pause.png'))
adb('shell', 'input', 'keyevent', 'DPAD_CENTER')
adb('shell', 'input', 'keyevent', '--duration', '100', 'DPAD_CENTER')
recording.wait()
assert recording.returncode == 0
adb('pull', '/sdcard/couchjoust-gameplay.mp4', str(out / 'gameplay.mp4'))
(out / 'frames.txt').write_bytes(adb('shell', 'dumpsys', 'gfxinfo', 'com.couchjoust', 'framestats'))
adb('shell', 'screencap', '-p', '/sdcard/couchjoust-play.png')
adb('pull', '/sdcard/couchjoust-play.png', str(out / 'play.png'))
print('Recorded Android TV gameplay, pause, resume, and frame statistics.', flush=True)
