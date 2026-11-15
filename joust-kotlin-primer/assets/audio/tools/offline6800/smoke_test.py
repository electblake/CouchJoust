#!/usr/bin/env python3
"""Exercise every sound command, preserving evidence of illegal-opcode failures."""
from pathlib import Path
import json
import sys
from m6800 import CPU

HERE = Path(__file__).resolve().parent
ROM = Path(sys.argv[1]) if len(sys.argv)>1 else HERE.parent.parent/'reference/video_sound_rom_4_std_780.ic12'

class IO:
    def __init__(self):
        self.cpu=None
        self.ctrl_a=0
        self.ctrl_b=0
        self.ddr_a=0
        self.ddr_b=0
        self.latch=255
        self.dac=128
        self.writes=0
        self.first=None
        self.last=None
        self.low=255
        self.high=0
        self.sum=0
    def read(self,addr):
        if addr==0x400: return self.dac if self.ctrl_a&4 else self.ddr_a
        if addr==0x401: return self.ctrl_a
        if addr==0x402:
            if self.ctrl_b&4:
                self.cpu.irq(False)
                return self.latch
            return self.ddr_b
        if addr==0x403: return self.ctrl_b
        return 255
    def write(self,addr,value,cycle):
        if addr==0x400:
            if self.ctrl_a&4:
                self.dac=value
                self.writes+=1
                if self.first is None: self.first=cycle
                self.last=cycle
                self.low=min(self.low,value)
                self.high=max(self.high,value)
                self.sum=(self.sum+value)&0xffffffff
            else: self.ddr_a=value
        elif addr==0x401: self.ctrl_a=value
        elif addr==0x402:
            if not self.ctrl_b&4:self.ddr_b=value
        elif addr==0x403:self.ctrl_b=value

results=[]
for command in list(range(1,0x28))+[0x3f]:
    io=IO()
    with CPU(ROM,io.read,io.write) as cpu:
        io.cpu=cpu
        cpu.run_until(1000)
        assert cpu.peek(0)==0x3c
        assert io.ctrl_a==0x3c and io.ctrl_b==0x37
        io.latch=(~command&63)|0xc0
        cpu.irq()
        started=cpu.cycles
        cap=started+int(894886.25*180)
        while cpu.cycles<cap:
            cpu.run(10000)
            if cpu.pc in (0xf7f8,0xf01d): break
        result=dict(command=f'{command:02x}',cycles=cpu.cycles-started,
            instructions=cpu.instructions,pc=f'{cpu.pc:04x}',
            completed=cpu.pc in (0xf7f8,0xf01d),dac_writes=io.writes,
            first=io.first,last=io.last,min=io.low,max=io.high,
            checksum=io.sum,
            opcode_counts={f'{k:02x}':v for k,v in cpu.opcode_counts().items()})
        results.append(result)
        print(command,result['pc'],result['dac_writes'],result['completed'],flush=True)
(HERE/'smoke-results.json').write_text(json.dumps(results,indent=2)+'\n')
assert len(results)==40
assert all(r['completed'] for r in results)
assert all(r['dac_writes']>0 for r in results if r['command'] not in ('1f','3f'))
assert next(r for r in results if r['command']=='1f')['dac_writes']==0
assert results[-1]['dac_writes']==0
