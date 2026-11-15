#!/usr/bin/env python3
"""Rebuild and verify the Joust sound program from the user's source checkout.
Usage: python3 rebuild_sound_image.py /path/to/joust-source --asl /path/to/asl --p2bin /path/to/p2bin
No ROM is downloaded. Temporary assembler output never changes the input repo.
"""
import argparse, hashlib, shutil, subprocess, tempfile, zlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--asl',required=True,type=Path);p.add_argument('--p2bin',required=True,type=Path);p.add_argument('--output',type=Path,default=Path(__file__).resolve().parent.parent/'reference/video_sound_rom_4_std_780.ic12');a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='joust_sound_') as temp:
 d=Path(temp);shutil.copy2(a.source/'VSNDRM4.ASM',d/'VSNDRM4.ASM')
 subprocess.run([str(a.asl.resolve()),'VSNDRM4.ASM','-o','vsndrm4.p'],cwd=d,check=True)
 subprocess.run([str(a.p2bin.resolve()),'vsndrm4.p','sound.bin','-l','00','-r','$F000-$FFFF'],cwd=d,check=True)
 b=(d/'sound.bin').read_bytes();assert len(b)==4096
 assert zlib.crc32(b)==0xf1835bdd
 assert hashlib.sha1(b).hexdigest()=='af7c066d2949d36b87ea8c425ca7d12f82b5c653'
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(b)
 print('Verified 4096 bytes; CRC32 f1835bdd; SHA1 af7c066d2949d36b87ea8c425ca7d12f82b5c653')
