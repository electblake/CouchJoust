#!/usr/bin/env python3
"""Render original Joust sound commands/events to PCM using its verified source build.
Offline extraction only. Native Kotlin consumes WAVs; no runtime CPU is required.
Requirements: Python3, numpy, compiled offline6800 helper; source-built 4096-byte input.
"""
import argparse, gzip, hashlib, json, math, sys, wave, zlib
from pathlib import Path
import numpy as np
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE/'offline6800'))
from m6800 import CPU

CLOCK=3579545/4
FRAME_HZ=8_000_000/(512*260)
RATE=48000
GAIN=.25
IDLE={0xF7F8,0xF01D}

class SoundBoard:
    def __init__(self,rom):
        self.control=[0,0];self.ddr=[0,0];self.port=[0,0];self.input_b=255;self.irq_b=False
        self.dac=0;self.writes=[];self.commands=[]
        self.cpu=CPU(rom,self.read_io,self.write_io)
        self.cpu.run(200)
        self.writes.clear()
        self.start=self.cpu.cycles
        self.writes.append((self.start,self.dac))
    def read_io(self,addr):
        n=addr-0x400;p=n//2
        if n%2:return self.control[p]|(128 if p==1 and self.irq_b else 0)
        if not self.control[p]&4:return self.ddr[p]
        if p==1:
            self.irq_b=False;self.cpu.irq(False)
            return (self.port[1]&self.ddr[1])|(self.input_b&~self.ddr[1])
        return self.port[0]&self.ddr[0]
    def write_io(self,addr,v,cycle):
        n=addr-0x400;p=n//2
        if n%2:self.control[p]=v&63
        elif self.control[p]&4:
            self.port[p]=v
            if p==0:
                self.dac=v&self.ddr[0];self.writes.append((cycle,self.dac))
        else:
            self.ddr[p]=v
            if p==0:
                self.dac=self.port[0]&v;self.writes.append((cycle,self.dac))
    def command(self,cmd):
        # Game writes idle FF before every command, producing a new rising edge.
        self.input_b=255
        if cmd:
            self.input_b=(~cmd&63)|0xc0
            self.irq_b=True
            if self.control[1]&1:self.cpu.irq(True)
        self.commands.append({'command':cmd,'cycle':self.cpu.cycles-self.start})
    def run_to(self,relative):self.cpu.run_until(self.start+round(relative))
    def run_to_end(self,max_seconds):
        limit=self.cpu.cycles+math.ceil(max_seconds*CLOCK)
        # Idle-loop checks every ~1.1 ms affect only trailing file length.
        while self.cpu.cycles<limit:
            self.cpu.run_until(min(limit,self.cpu.cycles+1024))
            if self.cpu.pc in IDLE:return True
        return False
    def finish(self,path,tracepath,end=None,**extra):
        if end is None:end=self.cpu.cycles-self.start
        duration=max(end/CLOCK,.001)
        count=math.ceil(duration*RATE)
        # Collapse same-cycle writes with last-write-wins, then integrate exact
        # held DAC levels over each output sample. No per-clip normalization/EQ.
        times=[];values=[]
        for cycle,value in self.writes:
            t=(cycle-self.start)/CLOCK
            if t<0:continue
            if times and t==times[-1]:values[-1]=value
            else:times.append(t);values.append(value)
        times=np.asarray(times,dtype=float);values=np.asarray(values,dtype=float)
        edges=np.arange(count+1,dtype=float)/RATE
        t=np.r_[times, max(edges[-1],times[-1])+1e-9]
        vals=(values/128-1)*GAIN
        integral=np.r_[0,np.cumsum(np.diff(t)*vals)]
        output=np.diff(np.interp(edges,t,integral))*RATE
        pcm=np.clip(np.rint(output*32768),-32768,32767).astype('<i2')
        path.parent.mkdir(parents=True,exist_ok=True)
        with wave.open(str(path),'wb') as w:
            w.setnchannels(1);w.setsampwidth(2);w.setframerate(RATE);w.writeframes(pcm.tobytes())
        tracepath.parent.mkdir(parents=True,exist_ok=True)
        # Keep original integer-cycle event trace as the exact extraction output;
        # WAV is a resampling of these levels. No guessed loop points.
        trace={'clock_hz':CLOCK,'start_cycle':self.start,'end_cycle':self.start+end,'commands':self.commands,'writes':self.writes}
        with gzip.open(tracepath,'wt',encoding='utf-8') as f:json.dump(trace,f,separators=(',',':'))
        result={'file':str(path),'trace':str(tracepath),'duration_seconds':count/RATE,'sample_rate_hz':RATE,'channels':1,'bits_per_sample':16,'dac_write_count':len(self.writes)-1,'first_dac_write_seconds':(self.writes[1][0]-self.start)/CLOCK if len(self.writes)>1 else None,'first_dac_change_seconds':next(((c-self.start)/CLOCK for c,v in self.writes if v!=self.writes[0][1]),None),'last_dac_write_seconds':(self.writes[-1][0]-self.start)/CLOCK,'ending_pc':f'{self.cpu.pc:04x}','returned_to_idle':self.cpu.pc in IDLE,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'commands':self.commands,'amplitude_min':int(pcm.min()),'amplitude_max':int(pcm.max()),'signal_variation':int(pcm.max())-int(pcm.min())}
        result.update(extra)
        self.cpu.close()
        return result

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--rom',type=Path,default=HERE.parent/'reference/video_sound_rom_4_std_780.ic12');ap.add_argument('--events',type=Path,default=HERE.parent.parent/'sound-events.json');ap.add_argument('--out',type=Path,default=HERE.parent);ap.add_argument('--commands',default='all');ap.add_argument('--skip-events',action='store_true');ap.add_argument('--max-seconds',type=float,default=60);args=ap.parse_args()
    rom=args.rom.read_bytes();assert len(rom)==4096;assert zlib.crc32(rom)==0xf1835bdd
    events=json.loads(args.events.read_text())
    manifest={'source_commit':'87e22aab945709fa107df609c0efcb839a17c1f9','rom_crc32':'f1835bdd','rom_sha1':hashlib.sha1(rom).hexdigest(),'rom_sha256':hashlib.sha256(rom).hexdigest(),'clock_hz':CLOCK,'event_tick_hz':FRAME_HZ,'wav_sample_rate_hz':RATE,'dac_gain':GAIN,'commands':{},'events':{},'limitations':['Offline original-program rendering with MAME-derived 6800 instruction semantics and total cycles. Individual DAC writes are timestamped at instruction completion, not individual bus phase.','Raw held DAC output uses the reference renderer 0.25 gain and per-sample box integration. No cabinet speaker response, analogue amplifier response, added EQ, fade, per-clip normalization or invented sounds.','Each isolated command/event starts from reset with initially zero RAM. Some generator history/noise may vary in live sequences; event WAVs preserve state within their scheduled sequence.','A capture cutoff is not a loop point. Any command still active at cutoff must not be blindly looped. Raw traces and renderer permit longer captures.','Command 00 is no new trigger; command3f interrupts synthesis without changing the last DAC level. Neither is an audible asset.']}
    commands=list(range(1,40)) if args.commands=='all' else [int(x,0) for x in args.commands.split(',')]
    for cmd in commands:
        board=SoundBoard(rom);board.command(cmd)
        finished=board.run_to_end(args.max_seconds)
        uses=[k for k,v in events.items() if any(s['command']==cmd for s in v['sequence'])]
        item=board.finish(args.out/'commands'/f'command_{cmd:02x}.wav',args.out/'dac-traces'/f'command_{cmd:02x}.json.gz',capture_limit_seconds=args.max_seconds,truncated=not finished,event_uses=uses,control_only=cmd==31)
        manifest['commands'][f'{cmd:02x}']=item
        print(f'command {cmd:02x}: {item["duration_seconds"]:.3f}s, {item["dac_write_count"]} writes, idle={finished}',flush=True)
    if not args.skip_events:
        for name,event in events.items():
            if all(s['command'] in (0,63) for s in event['sequence']):
                manifest['events'][name]={'control_only':True,'priority':event['priority'],'sequence':event['sequence']};continue
            board=SoundBoard(rom);tick=0
            for s in event['sequence']:
                board.run_to(tick/FRAME_HZ*CLOCK);board.command(s['command']);tick+=s['hold_ticks']
            # Keep every hold interval, then the natural remaining waveform.
            board.run_to(tick/FRAME_HZ*CLOCK)
            finished=board.cpu.pc in IDLE
            if not finished:finished=board.run_to_end(args.max_seconds)
            item=board.finish(args.out/'events'/f'{name}.wav',args.out/'dac-traces'/f'{name}.json.gz',priority=event['priority'],scheduler_hold_ticks=tick,truncated=not finished,sequence=event['sequence'])
            manifest['events'][name]=item
            print(f'event {name}: {item["duration_seconds"]:.3f}s, {item["dac_write_count"]} writes, idle={finished}',flush=True)
    # Manifest paths are relative to audio directory for portable archive use.
    for group in ('commands','events'):
        for item in manifest[group].values():
            for key in ('file','trace'):
                if key in item:item[key]=str(Path(item[key]).relative_to(args.out))
    args.out.mkdir(parents=True,exist_ok=True)
    (args.out/'audio-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
if __name__=='__main__':main()
