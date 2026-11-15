#!/usr/bin/env python3
"""Extract numeric image source from synamaxmusic/joust pinned revision.
Usage: python3 extract_assets.py /path/to/joust-source /path/to/output
Requires Pillow. This parser is intentionally restricted to this source format;
it is not a 6809 assembler. It never fetches ROMs or modifies the input files.
"""
import argparse, hashlib, json, re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ap = argparse.ArgumentParser()
ap.add_argument('source',type=Path)
ap.add_argument('output',type=Path)
ap.add_argument('--label-font',type=Path,help='Optional TTF/OTF for atlas labels only; source pixels are unaffected.')
a=ap.parse_args(); out=a.output; out.mkdir(parents=True,exist_ok=True)
lines=(a.source/'JOUSTI.ASM').read_text().splitlines()
source_sha=hashlib.sha256((a.source/'JOUSTI.ASM').read_bytes()).hexdigest()

def num(s):
    s=s.strip().replace('COFF','512').replace('DMAFIX','1028')
    s=re.sub(r'\$([0-9A-Fa-f]+)',lambda m:str(int(m[1],16)),s)
    s=re.sub(r'%([01]+)',lambda m:str(int(m[1],2)),s)
    s=re.sub(r'@([0-7]+)',lambda m:str(int(m[1],8)),s)
    s=s.replace('!','^')
    s=re.sub(r'\b0([0-9]+)\b',lambda m:str(int(m[0])),s)
    assert re.fullmatch(r'[0-9\s()+*^/~&|\-]+',s),s
    return int(eval(s,{'__builtins__':{}},{}))

def directive(line):
    line=line.split(';',1)[0]
    m=re.search(r'\b(FCB|FDB)\s+(.+)',line)
    if not m:return None
    return m[1],[num(x) for x in m[2].split(',')]

def collect(start,n=None):
    result=[];end=start
    for j in range(start,len(lines)):
        line=lines[j]
        if j>start and re.match(r'^\w',line) and result:break
        if not line.strip() or line.lstrip().startswith(';'):continue
        d=directive(line)
        if not d:
            if result:break
            continue
        op,vs=d
        for v in vs:
            if op=='FDB':result.extend([(v>>8)&255,v&255])
            else:result.append(v&255)
        end=j
        if n is not None and len(result)>=n:break
    if n is not None:assert len(result)==n,(start+1,n,len(result))
    return result,end+1

pal_bytes=[int(x,8) for x in re.findall(r'^\s*(?:COLOR1\s+)?FCB\s+@([0-7]+)',(a.source/'SYSTEM.ASM').read_text(),re.M)]
assert len(pal_bytes)==16
# Williams DAC resistor conductances are 1200, 560, 330 ohms for R/G;
# B has 560 and 330 ohm legs, normalized as its own divider network.
# Matches MAME williams_state::palette_init and compute_resistor_weights.
weights=[1/1200,1/560,1/330]; sw=sum(weights)
def rg(v):return round(255*sum(weights[b] for b in range(3) if v>>b&1)/sw)
def blue(v):return round(255*sum(weights[b+1] for b in range(2) if v>>b&1)/sum(weights[1:]))
palette=[(rg(v&7),rg((v>>3)&7),blue((v>>6)&3)) for v in pal_bytes]
palflat=[c for rgb in palette for c in rgb]+[0]*720

def image(data,w,h):
    idx=[]
    for v in data:idx.extend([v>>4,v&15])
    im=Image.new('P',(w*2,h));im.putpalette(palflat);im.putdata(idx)
    return im

def save(im,path,transparent=True):
    im.save(path,transparency=0 if transparent else None)

def index_rows(im):
    """One hexadecimal palette index per native pixel; no RGB quantization."""
    return [''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)]

manifest={'source_commit':'87e22aab945709fa107df609c0efcb839a17c1f9','source_sha256':source_sha,'index_grid_format':'Each row is a hex string; each character 0-f is one native pixel. Rows top-to-bottom, pixels left-to-right. For ordinary sprites 0 is transparent. PNG files retain the same indexed pixels.','palette_register_bytes':pal_bytes,'palette_rgb':palette,'sprites':{},'terrain':{}}
manifest['source_files_sha256']={n:hashlib.sha256((a.source/n).read_bytes()).hexdigest() for n in ('JOUSTI.ASM','MESSAGE.ASM','PHRASE.ASM','ATT.ASM','SYSTEM.ASM','JOUSTRV4.ASM','VSNDRM4.ASM')}
ims={}
for i,line in enumerate(lines):
    m=re.match(r'^(\w+)\s+FDB\s+\$([0-9A-F]+)!DMAFIX',line)
    if not m:continue
    label=m[1];val=int(m[2],16); w,h=val>>8,val&255
    data,end=collect(i+1,w*h)
    im=image(data,w,h);ims[label]=im
    (out/'sprites').mkdir(exist_ok=True);save(im,out/'sprites'/f'{label}.png')
    manifest['sprites'][label]={'width_pixels':2*w,'height_pixels':h,'width_bytes':w,'line_start':i+1,'line_end':end,'pixel_data_sha256':hashlib.sha256(bytes(data)).hexdigest(),'index_rows':index_rows(im)}

# Terrain descriptors hold collision address, pixel source, destination and dimensions.
labels={m[1]:i for i,l in enumerate(lines) if (m:=re.match(r'^(\w+)',l))}
arena=Image.new('P',(304,256));arena.putpalette(palflat)
for i,line in enumerate(lines):
    m=re.match(r'^(_CLIF\w+|_TRANS\d)\s+FDB\s+([^,]+),([^,]+),\$([0-9A-F]+),\$([0-9A-F]+)!DMAFIX',line)
    if not m:continue
    label,col,src,loc,size=m.groups();loc=int(loc,16);size=int(size,16); w,h=size>>8,size&255
    data,end=collect(labels[src],w*h)
    im=image(data,w,h);ims[label]=im;save(im,out/'sprites'/f'{label}.png')
    x=(loc>>8)*2;y=loc&255
    # Zero suppression: destination black here, but mask remains faithful for overlap.
    arena.paste(im,(x,y),im.point(lambda p:255 if p else 0,mode='L'))
    manifest['terrain'][label]={'source':src,'collision':col,'x_pixels':x,'y_pixels':y,'width_pixels':w*2,'height_pixels':h,'descriptor_line':i+1,'source_line_start':labels[src]+1,'source_line_end':end,'line_start':min(i+1,labels[src]+1),'line_end':max(i+1,end),'index_rows':index_rows(im)}

# Decode the sole compressed image exactly as SYSTEM.ASM UNCOM/REST.
data,end=collect(labels['_COMCL5'])
bits=''.join(f'{v:08b}' for v in data);bp=0;x=0;y=0;runs=[];pixels={};maxx=0
while True:
    n=1
    while bits[bp]=='0':n+=1;bp+=1
    bp+=1
    value=(1<<n)|int(bits[bp:bp+n],2);bp+=n
    length=value-2
    c=int(bits[bp:bp+3],2);bp+=3
    if length==0:
        if c==0:break
        maxx=max(maxx,x);x=0;y+=1
    else:
        ci=c+7 if c else 0
        runs.append([x,y,length,ci])
        for k in range(length):pixels[(x+k,y)]=ci
        x+=length
maxx=max(maxx,x); maxy=max(py for px,py in pixels)+1
bottom=Image.new('P',(maxx,maxy));bottom.putpalette(palflat)
for xy,c in pixels.items():bottom.putpixel(xy,c)
ims['_COMCL5']=bottom;save(bottom,out/'sprites'/'_COMCL5.png')
arena.paste(bottom,(54,211),bottom.point(lambda p:255 if p else 0,mode='L'))
manifest['compressed_cliff']={'x_pixels':54,'y_pixels':211,'width_pixels':maxx,'height_pixels':maxy,'compressed_bytes':len(data),'consumed_bits':bp,'run_count':len(runs),'line_start':labels['_COMCL5']+1,'line_end':end,'index_rows':index_rows(bottom)}
save(arena,out/'arena-source-space.png',False)
save(arena.crop((6,7,298,247)),out/'arena-visible.png',False)
save(arena.crop((6,7,298,247)).resize((1168,960),Image.Resampling.NEAREST),out/'arena-visible-4x.png',False)
save(arena.resize((1216,1024),Image.Resampling.NEAREST),out/'arena-source-space-4x.png',False)

# Fonts have unencoded two-byte width/height headers.
fl=(a.source/'MESSAGE.ASM').read_text().splitlines();fonts={}
for i,l in enumerate(fl):
    m=re.match(r'^(\w+)\s+FCB\s+\$([0-9A-F]+),\$([0-9A-F]+)',l)
    if not m or m[1][0] not in 'LS':continue
    label=m[1];w,h=int(m[2],16),int(m[3],16)
    if w>5 or h>9:continue
    data=[]
    for j in range(i+1,len(fl)):
        if re.match(r'^\w',fl[j]):break
        d=directive(fl[j])
        if d:
            op,vs=d;assert op=='FCB';data+=vs
    assert len(data)==w*h,(label,len(data),w*h)
    im=image(data,w,h);fonts[label]=im
    (out/'fonts').mkdir(exist_ok=True);save(im,out/'fonts'/f'{label}.png')
font_tables={}
for tab,stop in [('FONT57','FONT35'),('FONT35','L0')]:
    active=False; refs=[]
    for l in fl:
        if l.startswith(tab+' '):active=True
        if l.startswith(stop+' '):break
        if active and (mt:=re.search(r'\bFDB\s+(\w+)',l)):refs.append(mt[1])
    font_tables[tab]=refs
manifest['font_tables']=font_tables
manifest['font_glyphs']={n:{'width_pixels':i.width,'height_pixels':i.height,'index_rows':index_rows(i)} for n,i in fonts.items()}

# Descriptor records retained as literal source expressions to preserve aliases/offsets.
desc={};blocks=['_OSTRICH','_STORK','_BUZARD','_PLYR1','_PLYR2','_PLYR3','_PLYR4','_PLYR5','_EGGI','_ILAVAT','_IFLAME','_IPTERO','_POOF1','_POOF2','_POOF3']
for b in blocks:
    records=[]
    for z,line in enumerate(lines[labels[b]:]):
        s=line.split(';',1)[0]
        if z>0 and re.match(r'^_[A-Z0-9]+',s):break
        if not s.strip():continue
        m=re.search(r'\b(FDB|POSOFF)\s+(.+)',s)
        if m:
            parts=[p.strip() for p in m[2].split(',')]
            if len(parts) not in (3,4):break
            if m[1]=='FDB':
                v=num(parts[1]);dx=v>>8;dy=v&255
                dx=dx-256 if dx>=128 else dx;dy=dy-256 if dy>=128 else dy
                src=parts[2]
            else:dx=num(parts[1]);dy=-num(parts[2]);src=parts[3]
            records.append({'encoding':m[1],'fields':parts,'source_line':labels[b]+z+1,'x_offset_pixels':dx*2,'y_offset_pixels':dy,'sprite':src,'collision':parts[0],'collision_profile':None if parts[0]=='0' else parts[0]})
        elif re.search(r'\bEQU\b',s):continue
        elif re.match(r'^\w',s) and records:break
    for k,record in enumerate(records):
        if b in ('_OSTRICH','_STORK','_BUZARD'):
            record['logical_frame']=['SKID','STAND','RUN1','RUN2','RUN3','RUN4','FLY1','FLY2','FLY3'][k//2]
            record['facing']='right' if k%2==0 else 'left'
        elif b.startswith('_PLYR'):
            record['logical_frame']='SKID' if k<2 else ('MOUNTED' if k<4 else 'DISMOUNTED')
            record['facing']=('right' if k%2==0 else 'left') if k<4 else None
        elif b=='_IPTERO':
            record['logical_frame']=f'FLY{k//2+1}'
            record['facing']='right' if k%2==0 else 'left'
        else:record['logical_frame']=record['sprite']
    desc[b]=records
manifest['animation_descriptors']=desc

# Collision profiles: scan per-row endpoint FDBs through $8100 terminators.
collision={}
for label,start in labels.items():
    if not label.startswith(('CCLF','CWNG','CSKID','CSTN','CEGG','BWNG','BSTND','BSKID','PT')):continue
    vals=[];done=False
    for j in range(start,len(lines)):
        s=lines[j].split(';',1)[0]
        if j>start and re.match(r'^\w+\s+(FCB|FDB|EQU)',s) and vals:break
        m=re.search(r'\bFDB\s+(.+)',s)
        if not m:continue
        try:v=[num(q) for q in m[1].split(',')]
        except (AssertionError,ValueError):break
        if len(v)!=2:break
        if v==[0x8100,0x8100]:done=True;break
        vals.append(v)
    if done and vals:
        collision[label]={'line_start':start+1,'line_end':j+1,'rows':[None if v==[0x8000,0x8000] else [v[0]-512,v[1]-512] for v in vals],'raw_rows':vals}
manifest['collision_profiles']=collision

# Human-readable atlases; source sprites are never resampled except nearest-neighbor.
def label_font(size):
    if a.label_font:return ImageFont.truetype(str(a.label_font),size)
    try:return ImageFont.load_default(size=size)
    except TypeError:return ImageFont.load_default()
font=label_font(14)
small=label_font(12)
rows=[('Ostrich right',['ORUN1R','ORUN2R','ORUN3R','ORUN4R','ORUNSR','OFLY1R','OFLY3R']),('Ostrich left',['ORUN1L','ORUN2L','ORUN3L','ORUN4L','ORUNSL','OFLY1L','OFLY3L']),('Stork right',['SRUN1R','SRUN2R','SRUN3R','SRUN4R','SRUNSR','SFLY1R','SFLY3R']),('Buzzard right',['BRUN1R','BRUN2R','BRUN3R','BRUN4R','BRUNSR','BFLY1R','BFLY3R']),('Riders',['PLY1R','PLY2R','PLY3R','PLY4R','PLY5R','PLY3S','PLY4S']),('Eggs',['EGGUP','EGGRT','EGGLF','EGGB1','EGGB2','EGGB3','PLY5S']),('Lava troll',['GRAB1','GRAB2','GRAB3','GRAB4','GRAB5','GRAB6']),('Fire and deaths',['FLAME1','FLAME2','FLAME3','FLAME4','FL1','FL2','FL3']),('Pterodactyl',['PT1R','PT2R','PT3R','PT1L','PT2L','PT3L'])]
sheet=Image.new('RGB',(1120,len(rows)*140+70),(15,18,23));d=ImageDraw.Draw(sheet)
d.text((24,15),'JOUST SOURCE SPRITE ATLAS',font=font,fill='white')
d.text((24,38),'Native pixels at 4x nearest-neighbor; source palette; labels are assembly symbols',font=small,fill=(183,190,200))
for r,(title,names) in enumerate(rows):
    yy=70+r*140;d.text((20,yy),title,font=font,fill=(221,225,232))
    for c,n in enumerate(names):
        im=ims[n].convert('RGBA');im.putalpha(ims[n].point(lambda p:255 if p else 0,mode='L'))
        im=im.resize((im.width*4,im.height*4),Image.Resampling.NEAREST);xx=160+c*135
        sheet.paste(im,(xx+(125-im.width)//2,yy+24),im)
        d.text((xx+4,yy+112),n,font=small,fill=(183,190,200))
sheet.save(out/'sprite-atlas.png')
fsheet=Image.new('RGB',(1000,320),(15,18,23));d=ImageDraw.Draw(fsheet)
d.text((20,12),'JOUST SOURCE FONT GLYPHS',font=font,fill='white')
for row,prefix in enumerate('LS'):
    items=[(n,im) for n,im in fonts.items() if n.startswith(prefix)]
    for i,(n,im) in enumerate(items):
        x=20+(i%27)*36;y=50+row*130+(i//27)*60
        rgb=im.convert('RGB').resize((im.width*3,im.height*3),Image.Resampling.NEAREST)
        fsheet.paste(rgb,(x,y));d.text((x,y+28),n,font=small,fill=(183,190,200))
fsheet.save(out/'font-atlas.png')
# Sound events are priority + pairs(encoded command, scheduler hold count).
gl=(a.source/'JOUSTRV4.ASM').read_text().splitlines();sound={}
for i,l in enumerate(gl):
    m=re.match(r'^(SN[A-Z0-9]+)\s+FCB\s+(.+)',l)
    if not m:continue
    data=[];end=i
    for j in range(i,len(gl)):
        if j>i and re.match(r'^\w',gl[j]):break
        d=directive(gl[j])
        if d:
            op,vs=d;assert op=='FCB';data.extend(v&255 for v in vs);end=j
    assert len(data)%2==1,(m[1],data)
    sound[m[1]]={'priority':data[0],'line_start':i+1,'line_end':end+1,'comment':l.split(';')[1].strip() if ';' in l else '',
        'sequence':[{'command':(~data[k])&63,'continue':bool(data[k]&128),'hold_ticks':data[k+1],'encoded':data[k]} for k in range(1,len(data),2)]}
sound['SNBUB']={'priority':1,'line_start':1027,'line_end':1027,'source_file':'SYSTEM.ASM','comment':'THE SOUND OF A BUBBLE','sequence':[{'command':13,'continue':False,'hold_ticks':1,'encoded':114}]}
manifest['sound_events']=sound
(out/'sound-events.json').write_text(json.dumps(sound,indent=2)+'\n')
(out/'asset-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'sprites':len(ims),'font_glyphs':len(fonts),'collision_profiles':len(collision),'sound_events':len(sound),'compressed_cliff':{k:v for k,v in manifest['compressed_cliff'].items() if k!='index_rows'},'palette':palette}))
