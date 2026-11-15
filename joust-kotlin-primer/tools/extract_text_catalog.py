#!/usr/bin/env python3
"""Extract Joust's literal phrase, text-layout, and default-name data.

Uses only the Python standard library. Comments do not determine displayed text.
  python extract_text_catalog.py --source-dir reference/source --output-dir data
"""
from pathlib import Path
import argparse
import ast
import hashlib
import json
import operator
import re

COMMIT='87e22aab945709fa107df609c0efcb839a17c1f9'
BASE=f'https://github.com/synamaxmusic/joust/blob/{COMMIT}/'

def evaluate(expression,symbols):
    # AS gives XOR (!) higher precedence than +. Parenthesize the exact forms
    # occurring in encoded strings before translating to Python's AST operators.
    if '!' in expression:
        expression=re.sub(r'(\([^()]*\)|\$[0-9a-fA-F]+|[A-Za-z_]\w*)!([A-Za-z_]\w*|\$[0-9a-fA-F]+)',
                          r'(\1 ^ \2)',expression)
        if '!' in expression:raise ValueError(f'Unsupported XOR form {expression!r}')
    expression=re.sub(r'\$([0-9a-fA-F]+)',r'0x\1',expression.strip())
    expression=re.sub(r'%([01]+)',r'0b\1',expression)
    binary={ast.Add:operator.add,ast.Sub:operator.sub,ast.Mult:operator.mul,
            ast.Div:operator.floordiv,ast.BitOr:operator.or_,ast.BitAnd:operator.and_,
            ast.BitXor:operator.xor}
    def visit(n):
        if isinstance(n,ast.Constant) and type(n.value) is int:return n.value
        if isinstance(n,ast.Name) and n.id in symbols:return symbols[n.id]
        if isinstance(n,ast.BinOp) and type(n.op) in binary:
            return binary[type(n.op)](visit(n.left),visit(n.right))
        if isinstance(n,ast.UnaryOp) and isinstance(n.op,ast.USub):return -visit(n.operand)
        raise ValueError(f'Unsupported expression {expression!r}')
    return visit(ast.parse(expression,mode='eval').body)

class Source:
    def __init__(self,path):
        self.path=path
        self.raw=path.read_bytes()
        self.lines=self.raw.decode().splitlines()
        self.code=[line.split(';',1)[0].rstrip() for line in self.lines]
        self.labels={}
        for i,line in enumerate(self.code):
            m=re.match(r'^([A-Z][A-Z0-9_.]*)(?:\s|$)',line)
            if m:self.labels[m[1]]=i
    def anchor(self,start,end=None):
        end=start if end is None else end
        return dict(file=self.path.name,line_start=start,line_end=end,
                    url=f'{BASE}{self.path.name}#L{start}-L{end}')
    def equates(self):
        result={}
        for i,line in enumerate(self.code):
            m=re.match(r'^(\w+)\s+EQU\s+(.+)$',line)
            if m:
                try:result[m[1]]=evaluate(m[2],{k:v['value'] for k,v in result.items()})
                except (ValueError,SyntaxError):continue
                result[m[1]]={'value':result[m[1]],'source':self.anchor(i+1)}
        return result
    def directives(self,start,end):
        result=[]
        for i in range(start,end):
            m=re.search(r'\b(FCB|FDB)\s+(.+)$',self.code[i])
            if m:
                result.append((m[1],[v.strip() for v in m[2].split(',')],i+1))
        return result
    def bytes(self,start,end,symbols):
        data=[];origins=[];expressions=[]
        for op,fields,line in self.directives(start,end):
            for expression in fields:
                value=evaluate(expression,symbols)
                if op=='FCB':
                    if not 0<=value<=255:raise ValueError(f'FCB out of range at {line}')
                    data.append(value);origins.append(line);expressions.append(expression)
                else:
                    if not 0<=value<=65535:raise ValueError(f'FDB out of range at {line}')
                    data.extend([value>>8,value&255]);origins.extend([line,line]);expressions.extend([expression,expression])
        return data,origins,expressions

SPECIAL={'CSPC':' ','CBARRW':'⟨BACK_ARROW⟩','CEQU':'=','CDSH':'-',
         'CQUE':'?','CEXC':'!','CBRKL':'(','CBRKR':')','CSQOT':"'",
         'CCMMA':',','CPER':'.','CTHOU':'000','CCURSR':'⟨CURSOR⟩',
         'CSLSH':'/','CAMP':'&','CDQOT':'"','CCOLN':':',
         'CCURS':'⟨CURSOR⟩','CCNARW':'⟨CENTER_ARROW⟩'}
def symbolic_text(symbol):
    if symbol in SPECIAL:return SPECIAL[symbol]
    if re.fullmatch(r'C[0-9A-Z]',symbol):return symbol[1:]
    raise ValueError(f'Unmapped character symbol {symbol}')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    args=parser.parse_args()
    sources={p.name:Source(p) for p in sorted(args.source_dir.glob('*.ASM'))}
    phrase=sources['PHRASE.ASM'];eq2=sources['MESSEQU2.ASM'].equates();eq1=sources['MESSEQU.ASM'].equates()
    symbols={k:v['value'] for k,v in (eq1|eq2).items()}
    characters={k:v for k,v in eq2.items() if k.startswith('C') and k!='CEND'}
    small={};large={}
    for symbol,definition in characters.items():
        code=definition['value'];text=symbolic_text(symbol)
        if code<=46:small[code]=large[code]=text
        elif symbol in ('CTHOU','CCURSR'):small[code]=text
        else:large[code]=text

    # Font-pointer order is authoritative, including O=0 and S=5 aliases in FONT35.
    message=sources['MESSAGE.ASM']
    font_tables={}
    glyph_sizes={}
    for name,start,end in [('FONT57','FONT57','FONT35'),('FONT35','FONT35','L0')]:
        labels=[field for op,fields,_ in message.directives(message.labels[start],message.labels[end]) for field in fields]
        entries=[]
        for code,label in enumerate(labels):
            header=message.code[message.labels[label]]
            m=re.search(r'\bFCB\s+(.+)$',header)
            sizes=[evaluate(x,symbols) for x in m[1].split(',')[:2]]
            glyph_sizes[label]=(2*sizes[0],sizes[1])
            entries.append({'code':code,'glyph_label':label,'advance_pixels':2*sizes[0],
                            'height_pixels':sizes[1],
                            'readable':(large if name=='FONT57' else small)[code]})
        font_tables[name]={'entries':entries,'source':message.anchor(message.labels[start]+1,message.labels[end])}
    if len(font_tables['FONT57']['entries'])!=53 or len(font_tables['FONT35']['entries'])!=49:
        raise ValueError('Unexpected font table counts')

    # Phrase IDs are table positions relative to PHRASE, including negative IDs.
    pointer_records=[]
    for op,fields,line in phrase.directives(0,phrase.labels['OVER']):
        if op!='FDB':raise ValueError('Unexpected phrase pointer directive')
        pointer_records.extend((field,line) for field in fields)
    base=next(i for i,(label,_) in enumerate(pointer_records) if label=='OVER')
    indexed=[];by_id={};by_label={}
    for i,(label,pointer_line) in enumerate(pointer_records):
        start=phrase.labels[label]
        end=min(v for k,v in phrase.labels.items() if v>start)
        encoded,origins,expressions=phrase.bytes(start,end,symbols)
        if not encoded or not encoded[-1]&128 or any(v&128 for v in encoded[:-1]):
            raise ValueError(f'Bad terminator in phrase {label}')
        codes=[v&127 for v in encoded]
        chars=[re.sub(r'\+\s*CEND\s*$','',x).strip() for x in expressions]
        if any(c not in characters for c in chars):raise ValueError(f'Unexpected phrase expression {label}')
        readable=''.join(symbolic_text(c) for c in chars)
        phrase_id=(i-base)&255
        aliases=[name for name,entry in eq1.items() if entry['value']==phrase_id]
        warnings=[]
        if any(c in ('CTHOU','CCURSR') for c in chars):
            warnings.append('Uses a small-font special symbol; codes47/48 have different large-font meanings.')
        if any(v>=49 for v in codes):warnings.append('Contains a large-font-only code; FONT35 has only codes0..48.')
        renderings={}
        for font,table in [('FONT57',large),('FONT35',small)]:
            valid=all(c in table for c in codes)
            renderings[font]={'supported_codes':valid,
                'text':''.join(table.get(c,f'⟨UNDEFINED_0x{c:02X}⟩') for c in codes),
                'advance_pixels':sum(font_tables[font]['entries'][c]['advance_pixels'] for c in codes) if valid else None}
        entry={'id':phrase_id,'id_hex':f'{phrase_id:02X}','signed_id':phrase_id if phrase_id<128 else phrase_id-256,
            'label':label,'message_symbols':aliases,'text':readable,'codes':codes,
            'character_symbols':chars,'encoded_bytes':encoded,'font_renderings':renderings,
            'warnings':warnings,'source':phrase.anchor(origins[0],origins[-1]),
            'pointer_source':phrase.anchor(pointer_line)}
        indexed.append(entry);by_id[phrase_id]=entry;by_label[label]=entry
    if len(indexed)!=139 or len(by_id)!=139:raise ValueError('Expected139 unique phrases')

    # TXT records are exactly cursor16,color8,phrase/end8 regardless of FCB/FDB syntax.
    layout_pointers=[]
    for op,fields,line in phrase.directives(phrase.labels['TXT'],phrase.labels['ROMOK']):
        if op!='FDB':raise ValueError('Unexpected TXT pointer directive')
        layout_pointers.extend((field,line) for field in fields)
    layouts=[]
    for page_id,(label,pointer_line) in enumerate(layout_pointers):
        start=phrase.labels[label]
        end=min([v for v in phrase.labels.values() if v>start]+[len(phrase.lines)])
        raw,origins,_=phrase.bytes(start,end,symbols)
        if len(raw)%4:raise ValueError(f'Incomplete layout {label}')
        entries=[]
        for pos in range(0,len(raw),4):
            xbyte,y,color,marked=raw[pos:pos+4]
            pid=marked&127
            if pid not in by_id:raise ValueError(f'Missing phrase for layout {label}')
            entry={'x':2*xbyte,'y':y,'color_byte':color,'palette_index_even':color>>4,
                   'palette_index_odd':color&15,'phrase_id':pid,'phrase_label':by_id[pid]['label'],
                   'text':by_id[pid]['text'],'end_of_page':bool(marked&128),
                   'cursor_word':(xbyte<<8)|y,'raw_bytes':raw[pos:pos+4],
                   'source':phrase.anchor(origins[pos],origins[pos+3])}
            entries.append(entry)
        if not entries[-1]['end_of_page'] or any(x['end_of_page'] for x in entries[:-1]):
            raise ValueError(f'Bad layout terminator {label}')
        aliases=[name for name,entry in eq2.items() if name.startswith(('TX','ATX')) and entry['value']==page_id]
        layouts.append({'id':page_id,'id_hex':f'{page_id:02X}','label':label,'text_symbols':aliases,
                        'font':'caller_selected','entries':entries,'encoded_bytes':raw,
                        'source':phrase.anchor(origins[0],origins[-1]),'pointer_source':phrase.anchor(pointer_line)})
    if len(layouts)!=30:raise ValueError('Expected30 TXT pages')

    # Record proven callers without pretending a layout encodes its own font.
    # Direct adjacent load/call pairs are unambiguous. Attract entries encode
    # font35 in the high bit of the message byte before the dispatcher masks it.
    layout_by_id={p['id']:p for p in layouts}
    for page in layouts:page['known_font_uses']=[]
    for source in sources.values():
        previous=None
        for i,line in enumerate(source.code):
            if not line.strip():continue
            call=re.search(r'\b(?:JSR|JMP)\s+(TEXT35|TEXT)\s*$',line)
            if call and previous:
                prior_line,prior_code=previous
                load=re.search(r'\bLDA\s+#(.+)$',prior_code)
                if load and re.search(r'\b(?:TX[A-Z]+|ATX[0-9]+)\b',load[1]):
                    page_id=evaluate(load[1],symbols)
                    if page_id in layout_by_id:
                        layout_by_id[page_id]['known_font_uses'].append({
                            'font':'FONT35' if call[1]=='TEXT35' else 'FONT57',
                            'context':'Direct source call; surrounding build/state conditions still apply.',
                            'source':source.anchor(prior_line,i+1)})
            previous=(i+1,line)
    game=sources['JOUSTRV4.ASM']
    for op,fields,line in game.directives(game.labels['ATMST'],game.labels['ATMEND']):
        if op=='FDB' and len(fields)==2 and re.search(r'\bATX[0-9]+\b',fields[1]):
            message_byte=(evaluate(fields[1],symbols)>>8)&255
            layout_by_id[message_byte&127]['known_font_uses'].append({
                'font':'FONT35' if message_byte&128 else 'FONT57',
                'context':'Attract tutorial message table; message bit7 selects the small font.',
                'source':game.anchor(line),
                'dispatcher_source':game.anchor(326,333)})
    # These reviewed shared-call paths are not adjacent load/call pairs.
    # Their page IDs still come from the parsed equates; anchors identify the
    # conditionals selecting a message before the common large-font call.
    for name,file,start,end in [
        ('TXCMOK','T12REV3.ASM',527,545),
        ('TXCMER','T12REV3.ASM',540,545),
        ('TXCMDR','T12REV3.ASM',543,545),
        ('TXREST','TB12REV3.ASM',280,293),
        ('ATX9','JOUSTRV4.ASM',788,793)]:
        source=sources[file]
        region='\n'.join(source.code[start-1:end])
        if name not in region or not re.search(r'\bJSR\s+TEXT\b',region):
            raise ValueError(f'Reviewed font call context changed for {name}')
        layout_by_id[symbols[name]]['known_font_uses'].append({
            'font':'FONT57','context':'Reviewed conditional path to a shared large-font TEXT call.',
            'source':source.anchor(start,end)})
    for page in layouts:
        known=sorted({x['font'] for x in page['known_font_uses']})
        page['observed_font_choices']=known
        for font in known:
            if not all(by_id[e['phrase_id']]['font_renderings'][font]['supported_codes'] for e in page['entries']):
                raise ValueError(f'Unsupported character in observed font for page {page["label"]}')

    # Fixed-length operator text and default names live outside PHRASE.
    tb=sources['TB12REV3.ASM']
    def fixed_text(codes):return ''.join(large[v] for v in codes)
    operator_bytes,operator_lines,_=tb.bytes(tb.labels['OPD1'],tb.labels['ADVSC'],symbols)
    if len(operator_bytes)!=52:raise ValueError('Expected52 operator-default bytes')
    operator_defaults=[]
    for i,label in enumerate(('OPD1','OPD2')):
        codes=operator_bytes[i*25:(i+1)*25]
        operator_defaults.append({'label':label,'text':fixed_text(codes),'codes':codes,
            'font':'FONT57','initial_x':operator_bytes[50+i]*2,
            'source':tb.anchor(operator_lines[i*25],operator_lines[(i+1)*25-1]),
            'note':'25 fixed-length characters; x positions are separately stored defaults. Runtime context supplies y/color.'})
    defaults,default_lines,_=tb.bytes(tb.labels['DEFGOD'],tb.labels['CDEFS2'],symbols)
    if len(defaults)!=20+41*7:raise ValueError('Unexpected high-score-default layout')
    default_scores=[]
    for i in range(41):
        offset=20+i*7;codes=defaults[offset:offset+3];bcd=defaults[offset+3:offset+7]
        if any((v>>4)>9 or (v&15)>9 for v in bcd):raise ValueError('Nondecimal default score')
        digits=''.join(f'{v>>4}{v&15}' for v in bcd)
        default_scores.append({'entry':i+1,'initials':fixed_text(codes),'codes':codes,
            'score_bcd_bytes':bcd,'score':int(digits),
            'source':tb.anchor(default_lines[offset],default_lines[offset+6])})
    default_name={'label':'DEFGOD','text':fixed_text(defaults[:20]),'codes':defaults[:20],
                  'source':tb.anchor(default_lines[0],default_lines[19])}
    null_bytes,null_lines,_=tb.bytes(tb.labels['NULSCR'],tb.labels['NULSIZ'],symbols)
    null_score={'text':fixed_text(null_bytes[:3]),'codes':null_bytes[:3],
                'score_bcd_bytes':null_bytes[3:],'source':tb.anchor(null_lines[0],null_lines[-1])}

    # Fixed encoded strings displayed outside the phrase/text-table machinery.
    game_equates=game.equates()
    hidden_symbols=symbols|{k:v['value'] for k,v in game_equates.items()}
    copyright_raw,copyright_lines,_=game.bytes(game.labels['MSCOP2'],game.labels['WJSRTB'],hidden_symbols)
    if copyright_raw[-1]!=0:raise ValueError('Missing MSCOP2 terminator')
    copyright_encoded=copyright_raw[2:3]+copyright_raw[4:-1]
    copyright_codes=[((v-hidden_symbols['HIDE2'])&255)^hidden_symbols['HIDE'] for v in copyright_encoded]
    hidden_copyright={'label':'MSCOP2','font':'FONT57','text':fixed_text(copyright_codes),
        'codes':copyright_codes,'encoded_bytes':copyright_raw,
        'x':copyright_raw[0]*2,'y':copyright_raw[1],'color_byte':copyright_raw[3],
        'source':game.anchor(copyright_lines[0],copyright_lines[-1]),
        'renderer_source':game.anchor(884,893),
        'note':'Normal-release alternate copyright. Retain its source y coordinate even when outside the visible crop.'}
    credits_raw,credits_lines,_=game.bytes(game.labels['TROMSG'],game.labels['CREGG'],hidden_symbols)
    credits_decoded=[((v^0x9b)-0x35)&255 for v in credits_raw]
    if credits_decoded[-1]!=128 or any(v>=128 for v in credits_decoded[:-1]):
        raise ValueError('Unexpected TROMSG control/termination bytes')
    credit_lines=[];current=[]
    for v in credits_decoded:
        if v==0 or v>=128:
            credit_lines.append({'x':32,'y':48+16*len(credit_lines),'codes':current,
                                 'text':fixed_text(current),'color_byte':17})
            current=[]
        else:current.append(v)
    hidden_credits={'label':'TROMSG','font':'FONT57','lines':credit_lines,
        'text':'\n'.join(x['text'] for x in credit_lines),'encoded_bytes':credits_raw,
        'decoded_control_codes':credits_decoded,
        'source':game.anchor(credits_lines[0],credits_lines[-1]),
        'renderer_source':game.anchor(1813,1828),
        'note':'Hidden original credits. Decoded zero starts the next line; a negative decoded byte terminates. Font code0 is therefore not printed by this special routine.'}

    ascii_strings=[]
    for source in sources.values():
        for line,code in enumerate(source.code,1):
            m=re.search(r'\bFCC\s+([\'\"])(.*?)\1\s*$',code)
            if m:ascii_strings.append({'text':m[2],'ascii_bytes':list(m[2].encode('ascii')),
                'source':source.anchor(line),'note':'Embedded ASCII literal; not asserted to be displayed during gameplay.'})

    result={'source_commit':COMMIT,
        'source_sha256':{name:hashlib.sha256(source.raw).hexdigest() for name,source in sources.items()},
        'format_notes':[
            'Phrase text is decoded from character symbols and numeric data, never from English source comments. Preserve spelling, punctuation, leading/trailing spaces.',
            'codes removes only CEND bit0x80 from the final character; encoded_bytes retains the original terminator.',
            'Phrase IDsF5..FF are signed negative table offsets. Do not erase bit7 from a direct phrase ID. TXT page records separately use bit7 as end-of-page and address only IDs00..7F.',
            'TEXT/OUTPHR selectsFONT57; TEXT35/OUTP35 selectsFONT35. A TXT record itself has no font selector. Choose the calling context, not a modern system font.',
            'Font35 codes47/48 mean the single000 glyph/cursor; Font57 codes47/48 mean slash/ampersand. Codes49..52 exist only inFont57.',
            'Font35 O shares the digit0 bitmap; S shares digit5. Use font-table glyph lookup, not a Unicode font replacement.',
            'Cursor words convert to source pixels as x=2*highByte,y=lowByte. Advance each character by its decoded glyph width; this includes spacing and the wide000 glyph.',
            'Palette colors are indexes resolved through the active runtime palette, not fixed RGB names. Layout color bytes retain both nibble values.',
            'The30page layouts are the complete PHRASE.ASM TXT table. Other messages have positions/colors assembled dynamically by game states; those dynamic arrangements are not complete pages in this file.',
            'Numeric counters, scores, credits, user-entered names and operator edits remain runtime text. Defaults and fixed original phrases are supplied here.'],
        'character_symbols':{name:dict(code=entry['value'],readable=symbolic_text(name),source=entry['source']) for name,entry in characters.items()},
        'font_tables':font_tables,'phrases':indexed,'text_pages':layouts,
        'operator_defaults':operator_defaults,'default_champion_name':default_name,
        'default_score_entries':default_scores,'null_score_entry':null_score,
        'encoded_text_blocks':[hidden_copyright,hidden_credits],
        'embedded_ascii_literals':ascii_strings,
        'rendering_evidence':message.anchor(135,229)}
    args.output_dir.mkdir(parents=True,exist_ok=True)
    output=args.output_dir/'text_catalog.json'
    rendered=json.dumps(result,indent=2,ensure_ascii=False)+'\n'
    output.write_text(rendered)
    verification={'status':'passed','phrase_count':len(indexed),'text_page_count':len(layouts),
        'layout_entry_count':sum(len(x['entries']) for x in layouts),
        'default_score_entries':len(default_scores),'embedded_ascii_literals':len(ascii_strings),
        'additional_encoded_text_blocks':2,
        'all_phrases_terminated':True,'all_pages_terminated':True,'all_layout_phrase_references_resolved':True,
        'font57_glyphs':53,'font35_glyphs':49,
        'pages_with_verified_font_call_context':sum(bool(p['observed_font_choices']) for p in layouts),
        'output_sha256':hashlib.sha256(rendered.encode()).hexdigest()}
    (args.output_dir/'text_catalog_verification.json').write_text(json.dumps(verification,indent=2)+'\n')
    print(json.dumps(verification,indent=2))

if __name__=='__main__':main()
