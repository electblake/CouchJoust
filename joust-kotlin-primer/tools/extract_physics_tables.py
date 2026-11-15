#!/usr/bin/env python3
"""Extract the pinned Joust V4 gameplay physics tables using Python's stdlib.

Usage:
  python extract_physics_tables.py --source-dir reference/source --output-dir data

This is a source-data parser, not an assembler. It explicitly reads the literal
tables and expressions that define gameplay masks, ground states, flight speeds,
and selected movement constants. It rejects unexpected table sizes. The optional
--verify-against compares the complete result with an existing JSON artifact.
"""
from pathlib import Path
import argparse
import ast
import hashlib
import json
import operator
import re

COMMIT='87e22aab945709fa107df609c0efcb839a17c1f9'

def integer(expression, symbols=None):
    """Evaluate only integer literals, names and the arithmetic used here."""
    expression=re.sub(r'\$([0-9a-fA-F]+)',r'0x\1',expression.strip())
    expression=re.sub(r'%([01]+)',r'0b\1',expression)
    binary={ast.Add:operator.add,ast.Sub:operator.sub,ast.Mult:operator.mul,
            ast.Div:operator.floordiv,ast.FloorDiv:operator.floordiv,
            ast.BitOr:operator.or_,ast.BitAnd:operator.and_}
    unary={ast.UAdd:operator.pos,ast.USub:operator.neg}
    def visit(node):
        if isinstance(node,ast.Constant) and type(node.value) is int:return node.value
        if isinstance(node,ast.Name) and symbols and node.id in symbols:return symbols[node.id]
        if isinstance(node,ast.BinOp) and type(node.op) in binary:
            return binary[type(node.op)](visit(node.left),visit(node.right))
        if isinstance(node,ast.UnaryOp) and type(node.op) in unary:
            return unary[type(node.op)](visit(node.operand))
        raise ValueError(f'Unsupported source expression: {expression!r}')
    return visit(ast.parse(expression,mode='eval').body)

def ranges(values, origin, bit):
    result=[]
    start=None
    for i,value in enumerate(values+[0]):
        if value&(1<<bit):
            if start is None:start=i+origin
        elif start is not None:
            result.append([start,i+origin-1]);start=None
    return result

def extract(source):
    raw=source.read_bytes()
    lines=raw.decode().splitlines()
    code=[line.split(';',1)[0].rstrip() for line in lines]
    labels={}
    for i,line in enumerate(code):
        match=re.match(r'^([A-Z][A-Z0-9.]*)\s+',line)
        if match:labels[match[1]]=i

    def fcb_between(start,end):
        values=[]
        for line in code[labels[start]:labels[end]]:
            match=re.search(r'\bFCB\s+(.+)$',line)
            if match:values.extend(integer(x) for x in match[1].split(','))
        if not all(0<=x<=255 for x in values):raise ValueError('FCB outside byte range')
        return values

    specifications=[('background_y','BCKYTB','BCKXS1',0,256),
                    ('background_x','BCKXS1','BCKXS2',-32,352),
                    ('landing_y','LNDYTB','LNDXS1',0,256),
                    ('landing_x_template','LNDXS1','LNDXS2',-32,352)]
    masks={}
    for name,start,end,origin,expected in specifications:
        values=fcb_between(start,end)
        if name=='landing_y':
            # The following byte is KZAPPER's fudge byte, not a 257th y row.
            if len(values)!=257 or values[-1]!=0x18:
                raise ValueError('Unexpected landing-y trailing KZAPPER byte')
            values=values[:256]
        if len(values)!=expected:raise ValueError(f'{name}: expected {expected}, got {len(values)}')
        masks[name]={'origin':origin,'values':values,
                     'bit_ranges':{str(bit):ranges(values,origin,bit) for bit in range(8)}}

    states=[]
    for number,line in enumerate(code,1):
        match=re.match(r'^(PLY[A-M]R)\s+STATE\s+(.+)$',line)
        if not match:continue
        fields=[v.strip() for v in match[2].split(',')]
        if len(fields)!=6:raise ValueError('Unexpected STATE arity')
        wait,routine,reverse,neutral,forward,index=fields
        states.append(dict(label=match[1],wait=integer(wait),routine=routine,
            reverse=reverse,neutral=neutral,forward=forward,
            flight_index=integer(index),source_line=number))
    if len(states)!=13:raise ValueError('Expected thirteen ground states')

    zero=labels['FLYX']
    flight=[]
    for line in code[zero-4:zero+5]:
        match=re.search(r'\bFDB\s+(.+)$',line)
        if not match:raise ValueError('Unexpected flight-table layout')
        flight.append(integer(match[1]))
    flight={str(2*(i-4)):value for i,value in enumerate(flight)}

    equates={}
    for name in ('ELEFT','ERIGHT','CEILNG','FLOOR'):
        match=re.match(r'^'+name+r'\s+EQU\s+(.+)$',code[labels[name]])
        if not match:raise ValueError(f'Missing constant {name}')
        equates[name]=integer(match[1])
    joined='\n'.join(code)
    wrap_code='\n'.join(code[labels['WRAPX']:labels['WRXMIN']])
    wrap=re.search(r'\bADDD\s+#\(([^)]+)\)',wrap_code)
    if not wrap:raise ValueError('Missing positive horizontal wrap expression')
    wrap_value=integer(wrap[1],equates)
    lava=re.search(r'^ADGCEI\s+CMPA\s+#([^\n]+)',joined,re.M)
    gravity=re.search(r'\bLDA\s+#([^\n]+)\n\s*STA\s+GRAV\b',joined)
    if not lava or not gravity:raise ValueError('Missing integration constants')
    constants={'ELEFT':equates['ELEFT'],'ERIGHT':equates['ERIGHT'],
        'WRAP_PERIOD':wrap_value,'CEILING':equates['CEILNG'],
        'FLOOR':equates['FLOOR'],'LAVA_ENTRY':integer(lava[1],equates),
        'GRAV':integer(gravity[1])}

    flap='\n'.join(code[labels['ADDFLP']:labels['ADXMAX']])
    coefficient=integer(re.search(r'\bLDA\s+#([^\n]+)',flap)[1])
    subtraction=integer(re.search(r'\bSUBD\s+#([^\n]+)',flap)[1])
    examples={str(t):(coefficient*t)//256-subtraction for t in (0,1,2,4,8,16,32,64,128,255)}
    result={'commit':COMMIT,'source':source.name,'masks':masks,'ground_states':states,
            'flight_velocities_q8':flight,'flap_impulse_examples_q8':examples,
            'constants':constants,
            'note':'Parsed source masks are actor-position candidate maps, not terrain pixel silhouettes. Runtime copies mutate these masks. Initial landing_x ORs 0x20 into every entry; bridge removal restores template bit 5.'}
    return result,raw

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--verify-against',type=Path)
    args=parser.parse_args()
    result,raw=extract(args.source_dir/'JOUSTRV4.ASM')
    rendered=json.dumps(result,indent=2)+'\n'
    if args.verify_against:
        expected=json.loads(args.verify_against.read_text())
        if result!=expected:raise SystemExit('Physics data differs from --verify-against artifact')
    args.output_dir.mkdir(parents=True,exist_ok=True)
    path=args.output_dir/'physics_tables.json'
    path.write_text(rendered)
    verification={'status':'passed','commit':COMMIT,
        'source_sha256':hashlib.sha256(raw).hexdigest(),
        'output_sha256':hashlib.sha256(rendered.encode()).hexdigest(),
        'mask_sizes':{k:len(v['values']) for k,v in result['masks'].items()},
        'ground_state_count':len(result['ground_states']),
        'flight_velocity_count':len(result['flight_velocities_q8']),
        'compared_to_existing_artifact':args.verify_against is not None}
    (args.output_dir/'physics_extraction_verification.json').write_text(json.dumps(verification,indent=2)+'\n')
    print(json.dumps(verification,indent=2))

if __name__=='__main__':main()
