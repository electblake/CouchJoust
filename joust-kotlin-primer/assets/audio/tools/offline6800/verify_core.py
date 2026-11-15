#!/usr/bin/env python3
"""Targeted timing, original-6800 flags, and real-sound preemption checks."""
from pathlib import Path
import json
import sys
from m6800 import CPU

HERE=Path(__file__).resolve().parent
ROM=Path(sys.argv[1]) if len(sys.argv)>1 else HERE.parent.parent/'reference/video_sound_rom_4_std_780.ic12'

def program(code):
    rom=bytearray([1]*4096)
    rom[:len(code)]=code
    rom[0xffe:0x1000]=bytes([0xf0,0x00])
    rom[0xff8:0xffa]=bytes([0xf1,0x00])
    return rom

checks=[]
with CPU(program(bytes([0x8c,0x80,0x01,0x8c,0x80,0x00]))) as c:
    c.set_register('x',0x8000)
    c.set_register('cc',0xd1)
    assert c.step()==3
    # Original 6800 compares high bytes for N,V and preserves C.
    # Full 16-bit subtraction would have set N for 8000-8001.
    assert c.register('cc')==0xd1
    assert c.step()==3 and c.register('cc')==0xd5
    checks.append('Original 6800 CPX high-byte N/V, full-word Z, preserved C')

with CPU(program(bytes([0x1b,0x10,0x44,0x4c,0x09]))) as c:
    c.set_register('a',0x7f);c.set_register('b',1)
    assert c.step()==2 and c.register('a')==0x80 and c.register('cc')==0xfa
    assert c.step()==2 and c.register('a')==0x7f and c.register('cc')==0xf2
    assert c.step()==2 and c.register('a')==0x3f and c.register('cc')==0xf3
    c.set_register('a',255);c.set_register('cc',0xd1)
    assert c.step()==2 and c.register('a')==0 and c.register('cc')==0xd5
    c.set_register('x',1);c.set_register('cc',0xdb)
    assert c.step()==4 and c.register('x')==0 and c.register('cc')==0xdf
    checks.append('ABA/SBA/LSR/INC arithmetic and DEX flag preservation with cycle totals')

rom=program(bytes([0xa6,0xff,0x26,0x02,0x01,0x01,0x26,0xfe]))
rom[0xff]=0x66
with CPU(rom) as c:
    c.set_register('x',0xf000)
    assert c.step()==5 and c.register('a')==0x66
    assert c.step()==4 and c.pc==0xf006
    c.set_register('cc',c.register('cc')|4)
    assert c.step()==4 and c.pc==0xf008
    checks.append('Unsigned indexed displacement; taken and untaken branches both four cycles')

with CPU(program(bytes([0x0e,0x4c,0x01]))) as c:
    c.set_register('a',5);c.set_register('sp',0x7f)
    c.irq()
    assert c.step()==16 and c.pc==0xf100 and c.register('a')==6
    assert c.register('sp')==0x78
    assert c.peek(0x7b)==6
    assert c.peek(0x7f)==2 and c.peek(0x7e)==0xf0
    checks.append('CLI executes one following opcode before pending IRQ; exact 12-cycle entry and stack')

class IO:
    def __init__(self):
        self.cpu=None;self.controls=[0,0];self.ddrs=[0,0]
        self.latch=255;self.dac=0;self.writes=[]
    def read(self,addr):
        if addr&1:return self.controls[(addr-0x401)//2]
        side=(addr-0x400)//2
        if not self.controls[side]&4:return self.ddrs[side]
        if side:self.cpu.irq(False);return self.latch
        return self.dac
    def write(self,addr,value,cycle):
        if addr&1:self.controls[(addr-0x401)//2]=value;return
        side=(addr-0x400)//2
        if not self.controls[side]&4:self.ddrs[side]=value;return
        if not side:self.dac=value;self.writes.append((cycle,value))
    def command(self,command):
        # Host presents idle FF then active-low six-bit command.
        self.latch=(~command&63)|0xc0
        if self.latch!=255:self.cpu.irq()

io=IO()
with CPU(ROM,io.read,io.write) as c:
    io.cpu=c
    c.run_until(1000)
    io.command(4);c.run(50000)
    before=len(io.writes)
    io.command(0);c.run(50000)
    after_idle=len(io.writes)
    assert after_idle>before and c.pc not in (0xf01d,0xf7f8)
    # 3F IRQ abandons the sound handler and holds the most recent DAC value.
    io.command(0x3f);c.run(10000)
    after_stop=len(io.writes)
    assert c.pc==0xf7f8 and after_stop==after_idle
    held=io.dac
    c.run(100000)
    assert len(io.writes)==after_stop and io.dac==held
    io.command(0x20);c.run(500000)
    assert c.pc==0xf7f8 and len(io.writes)-after_stop==352
    # A deliberately forced zero-command IRQ follows the ROM's zero branch;
    # normal host command00 never generates that edge.
    io.command(4);c.run(50000)
    io.latch=255;c.irq();c.run(10000)
    assert c.pc==0xf7f8
    checks.append('Command00 leaves active sound running; command3F preempts and holds last DAC; command20 restarts')
    checks.append('Forced command00 IRQ takes zero branch; ordinary command00 is only an idle latch')

result={'status':'passed','checks':checks,'all_command_smoke_file':'smoke-results.json',
        'scope':'Targeted behavioral checks, not exhaustive independent CPU certification',
        'timing_limit':'IO writes timestamped at instruction completion; sub-instruction bus phases unmodeled'}
(HERE/'verification-results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
