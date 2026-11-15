#!/usr/bin/env python3
"""Decode original Joust title geometry and packed special-effect pixel data.
Run after extract_assets.py. Requires Pillow. No ROM input or image invention.
"""
import argparse, json, re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ap=argparse.ArgumentParser();ap.add_argument('source',type=Path);ap.add_argument('output',type=Path);ap.add_argument('--label-font',type=Path,help='Optional TTF/OTF for atlas labels only; source pixels are unaffected.');a=ap.parse_args()
manifest=json.loads((a.output/'asset-manifest.json').read_text());palette=manifest['palette_rgb']
flat=[c for rgb in palette for c in rgb]+[0]*720

def number(s,defs=None):
    s=s.strip()
    for k,v in (defs or {}).items():s=re.sub(r'\b'+k+r'\b',str(v),s)
    s=re.sub(r'\$([0-9a-fA-F]+)',lambda m:str(int(m[1],16)),s)
    s=re.sub(r'@([0-7]+)',lambda m:str(int(m[1],8)),s)
    assert re.fullmatch(r'[0-9+\-*/() ]+',s),s
    return int(eval(s,{'__builtins__':{}},{}))

def extract_bytes(file,start,end,defs=None):
    ls=(a.source/file).read_text().splitlines();active=False;data=[];startline=None;endline=None
    for i,l in enumerate(ls):
        if l.startswith(end):break
        if l.startswith(start):active=True;startline=i+1
        if active and (m:=re.search(r'\bFCB\s+(.+)',l.split(';',1)[0])):
            data.extend(number(s,defs) for s in m[1].split(','));endline=i+1
    return data,startline,endline

def rows(im):return [''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)]

def decode_effect(data,prefix,count):
    frames=[];pixels={};writes=set();x=y=0
    for v in data:
        if v==0:
            w=max(px for px,py in writes)+1;h=max(py for px,py in writes)+1
            im=Image.new('P',(w,h));im.putpalette(flat)
            for xy,c in pixels.items():im.putpixel(xy,c)
            name=f'{prefix}_{len(frames)+1}'
            im.save(a.output/'sprites'/f'{name}.png',transparency=0)
            frames.append({'name':name,'width_pixels':w,'height_pixels':h,'index_rows':rows(im),'write_mask_rows':[''.join('1' if (px,py) in writes else '0' for px in range(w)) for py in range(h)]})
            pixels={};writes=set();x=y=0
        elif v>>4==0:x=0;y+=1
        else:
            length=v>>4;color=v&15
            for q in range(length):
                pixels[x,y]=color;pixels[x+1,y]=color if q<length-1 else 0
                writes.add((x,y));writes.add((x+1,y));x+=2
    assert len(frames)==count,(prefix,len(frames),count)
    return frames

ashes,sl,el=extract_bytes('JOUSTI.ASM','_ASH1R','LENGTH')
debris_colors={m[1]:number(m[2]) for m in re.finditer(r'^(LIB|MEB|DKB)\s+EQU\s+([^;\r\n]+)',(a.source/'JOUSTRV4.ASM').read_text(),re.M)}
debris,dl,de=extract_bytes('JOUSTRV4.ASM','FIRSTI','KZAP1',debris_colors)
effects={'pterodactyl_ashes':{'source_file':'JOUSTI.ASM','source_lines':[sl,el],'x_offset_pixels':0,'y_offset_pixels':-10,'frames':decode_effect(ashes,'ASH',3)},'cliff_debris':{'source_file':'JOUSTRV4.ASM','source_lines':[dl,de],'frames':decode_effect(debris,'DEBRIS',5)},'semantics':'write_mask_rows marks every overwritten pixel. Index 0 within that mask is an intentional clear, not ordinary sprite transparency. Each span writes 2*n pixels with the last pixel cleared.'}
(a.output/'special-effects.json').write_text(json.dumps(effects,indent=2)+'\n')

# ATT LINE uses a connected integer staircase, not a platform antialiased path.
# Model its 16-bit accumulator and carry choices, including excluded end pixels.
def line(im,x,y,x1,y1,stroke,fill):
    dx=abs(x1-x);dy=abs(y1-y);sx=1 if x1>=x else -1;sy=1 if y1>=y else -1
    count=dx+dy
    assert count>0
    err=dx;delta=(-dy-1)&65535
    total=err+delta;err=total&65535;carry=total>65535
    for i in range(count):
        if 0<=x<304 and 0<=y<256:
            im.putpixel((x,y),stroke)
            if fill is not None:
                yy=y+1
                while yy<256 and im.getpixel((x,yy))==0:
                    im.putpixel((x,yy),fill);yy+=1
        if carry:
            x+=sx;total=err+delta
        else:
            y+=sy;total=err+dx
        err=total&65535;carry=total>65535

vals,tl,te=extract_bytes('ATT.ASM','LIST','        FCC',{'XPOS':4,'NOFILL':0,'FILL':128,'CL1':17,'CL2':34,'CL3':51,'CL4':68})
mar,marl,mare=extract_bytes('ATT.ASM','MARCOL','MAREND')
weights=[1/1200,1/560,1/330]
def rgb(v):
    return [round(255*sum(weights[b] for b in range(3) if (v>>off)>>b&1)/sum(weights)) for off in (0,3)]+[round(255*sum(weights[b+1] for b in range(2) if (v>>6)>>b&1)/sum(weights[1:]))]
marpal=[rgb(v) for v in mar[:16]]
im=Image.new('P',(304,256));im.putpalette([c for q in marpal for c in q]+[0]*720)
p=0;offset=vals[p];p+=1;segments=[]
while True:
    fill,stroke,fillc=vals[p:p+3];p+=3
    x,y=vals[p:p+2];p+=2;pts=[[x+offset,y]]
    while vals[p]!=0:
        x1,y1=vals[p:p+2];p+=2
        line(im,x+offset,y,x1+offset,y1,stroke&15,fillc&15 if fill else None)
        x,y=x1,y1;pts.append([x+offset,y])
    segments.append({'offset_x':offset,'stroke_palette_index':stroke&15,'fill_palette_index':fillc&15 if fill else None,'points':pts})
    p+=1;nextseg=vals[p];p+=1
    if nextseg==0:
        offset=vals[p];p+=1
        if offset==0:break
im.save(a.output/'title-logo.png')
im.crop((6,7,298,247)).resize((1168,960),Image.Resampling.NEAREST).save(a.output/'title-logo-4x.png')
(a.output/'title-line-data.json').write_text(json.dumps({'source_file':'ATT.ASM','source_lines':[tl,te],'palette_register_blocks':mar,'initial_palette_rgb':marpal,'segments':segments,'index_rows':rows(im),'notes':'Source-derived logo only. Omitted marquee border, messages and timed palette cycling. Line pixels follow ATT LINE integer stepping; zero-length source lines are rejected. Palette index 1 is black but nonzero for downward-fill boundaries.'},indent=2)+'\n')

if a.label_font:font=ImageFont.truetype(str(a.label_font),14)
else:
    try:font=ImageFont.load_default(size=14)
    except TypeError:font=ImageFont.load_default()
view=Image.new('RGB',(1100,480),(15,18,23));d=ImageDraw.Draw(view);d.text((20,15),'JOUST SOURCE SPECIAL EFFECTS',font=font,fill='white')
for group,frames,yy in [('Pterodactyl dissolve',effects['pterodactyl_ashes']['frames'],80),('Disintegrating cliff',effects['cliff_debris']['frames'],230)]:
    d.text((20,yy-25),group,font=font,fill=(220,225,235))
    for i,f in enumerate(frames):
        src=Image.open(a.output/'sprites'/f"{f['name']}.png").convert('RGBA');src=src.resize((src.width*3,src.height*3),Image.Resampling.NEAREST)
        view.paste(src,(20+i*210,yy),src);d.text((20+i*210,yy+90),f['name'],font=font,fill=(180,190,200))
view.save(a.output/'special-effects-atlas.png')
print(json.dumps({'title_segments':len(segments),'title_bytes_consumed':p,'effect_frames':8}))
