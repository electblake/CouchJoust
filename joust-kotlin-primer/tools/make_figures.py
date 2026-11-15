#!/usr/bin/env python3
"""Regenerate explanatory figures from this package's extracted source data.
Usage: python tools/make_figures.py --package-dir .
Requires Pillow, matplotlib and the Graphviz `dot` executable.
"""
import argparse
import json
import subprocess
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image

ap = argparse.ArgumentParser(description=__doc__)
ap.add_argument('--package-dir', type=Path, required=True)
root = ap.parse_args().package_dir.resolve()
out = root / 'docs' / 'figures'
out.mkdir(parents=True, exist_ok=True)
assets = root / 'assets' / 'graphics'
manifest = json.loads((assets / 'asset-manifest.json').read_text())
game = json.loads((root / 'data' / 'gameplay_tables.json').read_text())
plt.rcParams.update({'font.family':'DejaVu Sans','font.size':11,'axes.titlesize':16,'axes.titleweight':'bold'})

fig, ax = plt.subplots(figsize=(11.4,9.2), layout='constrained')
fig.patch.set_facecolor('#f4f5f7')
ax.set_facecolor('black')
im = Image.open(assets / 'arena-source-space.png').convert('RGB')
ax.imshow(im, extent=(-.5,303.5,255.5,-.5), interpolation='nearest')
for name, x,y,tx,ty in [
    ('CLIF1L',19,69,40,49),('CLIF1R',276,69,265,48),
    ('CLIF2',130,81,142,57),('CLIF3L',34,138,54,113),
    ('CLIF3U',231,129,214,104),('CLIF3R',280,138,271,167),
    ('CLIF4',137,163,129,189),('CLIF5',147,211,161,238)]:
    ax.annotate(name,(x,y),xytext=(tx,ty),color='white',fontsize=11,ha='center',
                arrowprops={'arrowstyle':'-','color':'#c4d3e2','linewidth':.8},
                bbox={'boxstyle':'round,pad=.25','facecolor':'#172638','edgecolor':'none'})
ax.axhline(32,color='#4cb9df',linewidth=.8,linestyle='--')
ax.text(7,27,'Bird ceiling: y=32',color='#8cddff',fontsize=10)
ax.axhline(230,color='#ff7c52',linewidth=.8,linestyle='--')
ax.text(8,226,'Lava-entry threshold: y=230',color='#ffad8c',fontsize=10)
for k,v in manifest['terrain'].items():
    if k.startswith('_TRANS'):
        ax.plot(v['x_pixels']+14,v['y_pixels'],marker='v',color='#ffef78',markersize=6)
ax.set_xlim(-12,305);ax.set_ylim(255,-1)
ax.set_xticks([0,50,100,150,200,250,300]);ax.set_yticks([0,32,68,80,128,137,162,210,230,255])
ax.set_xlabel('Source x (pixels)');ax.set_ylabel('Source y (pixels, downward)')
ax.set_title('Original terrain and game coordinates',loc='left',pad=14)
fig.text(.09,-.035,'Decoded static ledges; triangles mark transporters. Dynamic bridges, lava and actors are omitted.',fontsize=10,color='#44515e')
fig.savefig(out/'arena-annotated.png',dpi=160,bbox_inches='tight');fig.savefig(out/'arena-annotated.svg',bbox_inches='tight');plt.close(fig)

fig,axes=plt.subplots(1,2,figsize=(12,4.7),layout='constrained')
q=list(range(-8,9,2));v=[-2,-1,-.5,-.25,0,.25,.5,1,2]
axes[0].bar([str(x) for x in q],v,color=['#167d9a' if x<0 else '#b5791a' for x in v])
axes[0].axhline(0,color='#566372',linewidth=.8)
axes[0].set(title='Nine horizontal flight speeds',xlabel='Speed index',ylabel='Pixels per movement update')
t=np.arange(256);imp=((96*t)//256-96)/256
axes[1].step(t,imp,where='post',color='#af3f35',linewidth=2)
axes[1].set(title='New-press flap impulse',xlabel='Released-phase timer (samples)',ylabel='Change in vertical speed (px/update)',xlim=(0,255))
axes[1].grid(axis='y',alpha=.2)
for ax in axes:
    ax.spines[['top','right']].set_visible(False)
fig.suptitle('Exact movement values from Joust V4',fontsize=18,fontweight='bold')
fig.savefig(out/'movement-math.png',dpi=170);fig.savefig(out/'movement-math.svg');plt.close(fig)

fig,axes=plt.subplots(3,1,figsize=(14,11),layout='constrained')
colors={'bounders':'#b97827','hunters':'#cc4546','shadow_lords':'#6b67bc'}
for panel,ax in enumerate(axes):
    rows=game['waves'][panel*30:(panel+1)*30]
    xs=np.array([r['entry'] for r in rows]);bottom=np.zeros(30)
    for key,color in colors.items():
        values=np.array([0 if r['special']=='egg' else r[key] for r in rows])
        ax.bar(xs,values,bottom=bottom,color=color,label=key.replace('_',' ').title())
        bottom+=values
    eggx=[r['entry'] for r in rows if r['special']=='egg']
    ax.scatter(eggx,[12]*len(eggx),s=65,marker='o',facecolors='#f8f7e8',edgecolors='#49785b',linewidths=1.7,label='12-egg wave',zorder=4)
    for r in rows:
        if r['wave_pterodactyls']:
            ax.text(r['entry'],bottom[r['entry']-xs[0]]+.45,str(r['wave_pterodactyls'])+'P',ha='center',fontsize=8,color='#276b47')
    ax.set(xlim=(xs[0]-.8,xs[-1]+.8),ylim=(0,14.5),ylabel='Initial mounted enemies',xticks=xs,yticks=[0,3,6,9,12])
    ax.tick_params(axis='x',labelsize=8);ax.grid(axis='y',alpha=.16);ax.set_axisbelow(True)
    ax.spines[['top','right']].set_visible(False)
axes[0].legend(ncol=4,loc='upper left',frameon=False,fontsize=10)
axes[-1].set_xlabel('Wave table entry — after 90, repeat entries 81–90')
fig.suptitle('All 90 wave records',fontsize=20,fontweight='bold')
fig.text(.085,-.027,'P labels show special-wave pterodactyl counts. Egg-wave hatch species/caps, pursuit quotas and removed cliffs are in waves.csv.',fontsize=10)
fig.savefig(out/'wave-overview.png',dpi=160,bbox_inches='tight');fig.savefig(out/'wave-overview.svg',bbox_inches='tight');plt.close(fig)

dot='''digraph egg {
graph [rankdir=TB, bgcolor="white", pad="0.3", nodesep="0.32", ranksep="0.5"];
node [shape=box, style="rounded,filled", fillcolor="#edf1f5", color="#53677b", fontname="DejaVu Sans", fontsize=12, margin="0.18,0.12"];
edge [fontname="DejaVu Sans", fontsize=10, color="#53677b"];
mounted [label="Mounted enemy\nBounder → Hunter → Shadow lord"];
fall [label="Falling egg\nRider remains unresolved"];
rest [label="Resting egg\nHatch countdown"];
rider [label="Standing rider\nReplacement bird reserved"];
remount [label="Replacement bird arrives\nRider remounts"];
score [label="Player collects egg or rider\n250 / 500 / 750 / 1,000 chain", fillcolor="#fff1c6"];
gone [label="Rider resolved\nWave can finish when count reaches zero", fillcolor="#dcf0e5"];
mounted -> fall [label="Defeated; budget remains"];
fall -> rest [label="Terrain contact / bounce"];
rest -> rider [label="Hatch time + capacity available"];
rider -> remount [label="Bird navigation"];
remount -> mounted [label="Same rider budget", constraint=false];
fall -> score [label="Before first bounce: +500"];
rest -> score [label="Collected"];
rider -> score [label="Collected"];
score -> gone;
mounted -> score [label="Defeat 4\nCredited victor", constraint=false];
fall -> gone [label="Lost to lava", constraint=false];
}
'''
(out/'egg-lifecycle.dot').write_text(dot)
for fmt in ['svg','png']:
    subprocess.run(['dot',f'-T{fmt}',str(out/'egg-lifecycle.dot'),'-o',str(out/f'egg-lifecycle.{fmt}')],check=True)
print('Wrote arena, movement, 90-wave overview and egg lifecycle figures.')
