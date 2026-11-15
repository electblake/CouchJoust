"""Assemble the supplied source into local MAME verification input (not APK code)."""
import re
import shutil
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / '.tools/source'
roms = root / '.tools/mame/roms/joust'
roms.mkdir(parents=True, exist_ok=True)
p2bin = root / '.tools/asl/p2bin.exe'
for line in (source / 'BURN.BAT').read_text().splitlines():
    match = re.search(r'p2bin joust.p (\S+) -l (\S+) -r (\S+)', line)
    filename, load, address = match.groups()
    subprocess.run([str(p2bin), str(source/'joust.p'), str(roms/filename), '-l', load, '-r', address],check=True)
for filename, address in [('decoder_rom_4.3g','$0000-$01FF'),('decoder_rom_6.3c','$0200-$03FF')]:
    subprocess.run([str(p2bin),str(source/'decoder.p'),str(roms/filename),'-r',address],check=True)
shutil.copyfile(root/'joust-kotlin-primer/assets/audio/reference/video_sound_rom_4_std_780.ic12',roms/'video_sound_rom_4_std_780.ic12')
subprocess.run([str(root/'.tools/mame/mame.exe'),'joust','-rompath',str(roms.parent),'-verifyroms'],check=True)
