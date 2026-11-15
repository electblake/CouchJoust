// SPDX-License-Identifier: BSD-3-Clause
// Original instruction semantics and timings: Aaron Giles and MAME contributors.
// Adapted from MAME commit b7163a8d5ef30a8f0e72e4808ada8f904367311f.
// Offline extraction helper only; not part of the native Kotlin game runtime.
// See LICENSE-MAME.txt for the complete redistribution notice.
#include <array>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <new>

using u8 = uint8_t;
using u16 = uint16_t;
using u32 = uint32_t;
using s16 = int16_t;
union PAIR {
    u32 d;
#if defined(__BYTE_ORDER__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
    struct {u16 h,l;} w;
    struct {u8 h3,h2,h,l;} b;
#else
    struct {u16 l,h;} w;
    struct {u8 l,h,h2,h3;} b;
#endif
    PAIR() : d(0) {}
};
using read_callback = u8 (*)(void *, u16);
using write_callback = void (*)(void *, u16, u8, uint64_t);
enum {M6800_WAI=1, M6800_SLP=2};

class m6800_cpu_device {
public:
    PAIR m_ppc, m_pc, m_s, m_x, m_d, m_ea;
    u8 m_cc=0xc0|0x10, m_wai_state=0;
    uint64_t cycles=0, instructions=0;
    bool irq_line=false, nmi_pending=false, failed=false;
    std::array<uint64_t,256> opcode_counts{};
    std::array<u8,65536> memory{};
    char error[160]{};
    read_callback read_io=nullptr;
    write_callback write_io=nullptr;
    void *context=nullptr;
    using op_func = void (m6800_cpu_device::*)();
    static const u8 flags8i[256], flags8d[256], cycles_6800[256];
    static const op_func m6800_insn[256];
    m6800_cpu_device(const u8 *rom, size_t size, read_callback read_cb,
                    write_callback write_cb, void *opaque);
    u8 read(u32 addr);
    void write(u32 addr,u8 value);
    u32 RM16(u32 addr);
    void WM16(u32 addr, PAIR *value);
    void reset();
    void enter_interrupt(u16 vector);
    void check_irq_lines();
    void execute_one();
    void eat_cycles() {}
    void fail_illegal();
    uint64_t step();
    uint64_t run_until(uint64_t target);
    void illegl1();
    void nop();
    void tap();
    void tpa();
    void inx();
    void dex();
    void clv();
    void sev();
    void clc();
    void sec();
    void cli();
    void sei();
    void sba();
    void cba();
    void tab();
    void tba();
    void daa();
    void aba();
    void bra();
    void brn();
    void bhi();
    void bls();
    void bcc();
    void bcs();
    void bne();
    void beq();
    void bvc();
    void bvs();
    void bpl();
    void bmi();
    void bge();
    void blt();
    void bgt();
    void ble();
    void tsx();
    void ins();
    void pula();
    void pulb();
    void des();
    void txs();
    void psha();
    void pshb();
    void rts();
    void rti();
    void wai();
    void swi();
    void nega();
    void coma();
    void lsra();
    void rora();
    void asra();
    void asla();
    void rola();
    void deca();
    void inca();
    void tsta();
    void clra();
    void negb();
    void comb();
    void lsrb();
    void rorb();
    void asrb();
    void aslb();
    void rolb();
    void decb();
    void incb();
    void tstb();
    void clrb();
    void neg_ix();
    void illegl2();
    void com_ix();
    void lsr_ix();
    void ror_ix();
    void asr_ix();
    void asl_ix();
    void rol_ix();
    void dec_ix();
    void inc_ix();
    void tst_ix();
    void jmp_ix();
    void clr_ix();
    void neg_ex();
    void illegl3();
    void com_ex();
    void lsr_ex();
    void ror_ex();
    void asr_ex();
    void asl_ex();
    void rol_ex();
    void dec_ex();
    void inc_ex();
    void tst_ex();
    void jmp_ex();
    void clr_ex();
    void suba_im();
    void cmpa_im();
    void sbca_im();
    void anda_im();
    void bita_im();
    void lda_im();
    void sta_im();
    void eora_im();
    void adca_im();
    void ora_im();
    void adda_im();
    void cmpx_im();
    void bsr();
    void lds_im();
    void sts_im();
    void suba_di();
    void cmpa_di();
    void sbca_di();
    void anda_di();
    void bita_di();
    void lda_di();
    void sta_di();
    void eora_di();
    void adca_di();
    void ora_di();
    void adda_di();
    void cmpx_di();
    void jsr_di();
    void lds_di();
    void sts_di();
    void suba_ix();
    void cmpa_ix();
    void sbca_ix();
    void anda_ix();
    void bita_ix();
    void lda_ix();
    void sta_ix();
    void eora_ix();
    void adca_ix();
    void ora_ix();
    void adda_ix();
    void cmpx_ix();
    void jsr_ix();
    void lds_ix();
    void sts_ix();
    void suba_ex();
    void cmpa_ex();
    void sbca_ex();
    void anda_ex();
    void bita_ex();
    void lda_ex();
    void sta_ex();
    void eora_ex();
    void adca_ex();
    void ora_ex();
    void adda_ex();
    void cmpx_ex();
    void jsr_ex();
    void lds_ex();
    void sts_ex();
    void subb_im();
    void cmpb_im();
    void sbcb_im();
    void andb_im();
    void bitb_im();
    void ldb_im();
    void stb_im();
    void eorb_im();
    void adcb_im();
    void orb_im();
    void addb_im();
    void ldx_im();
    void stx_im();
    void subb_di();
    void cmpb_di();
    void sbcb_di();
    void andb_di();
    void bitb_di();
    void ldb_di();
    void stb_di();
    void eorb_di();
    void adcb_di();
    void orb_di();
    void addb_di();
    void ldx_di();
    void stx_di();
    void subb_ix();
    void cmpb_ix();
    void sbcb_ix();
    void andb_ix();
    void bitb_ix();
    void ldb_ix();
    void stb_ix();
    void eorb_ix();
    void adcb_ix();
    void orb_ix();
    void addb_ix();
    void ldx_ix();
    void stx_ix();
    void subb_ex();
    void cmpb_ex();
    void sbcb_ex();
    void andb_ex();
    void bitb_ex();
    void ldb_ex();
    void stb_ex();
    void eorb_ex();
    void adcb_ex();
    void orb_ex();
    void addb_ex();
    void ldx_ex();
    void stx_ex();
};
#define pPPC    m_ppc
#define pPC     m_pc
#define pS      m_s
#define pX      m_x
#define pD      m_d

#define PC      m_pc.w.l
#define PCD     m_pc.d
#define S       m_s.w.l
#define SD      m_s.d
#define X       m_x.w.l
#define D       m_d.w.l
#define A       m_d.b.h
#define B       m_d.b.l
#define CC      m_cc

#define EAD     m_ea.d
#define EA      m_ea.w.l

/* memory interface */

/****************************************************************************/
/* Read a byte from given memory location                                   */
/****************************************************************************/
#define RM(Addr) (read(Addr))

/****************************************************************************/
/* Write a byte to given memory location                                    */
/****************************************************************************/
#define WM(Addr,Value) (write(Addr,Value))

/****************************************************************************/
/* M6800_RDOP() is identical to M6800_RDMEM() except it is used for reading */
/* opcodes. In case of system with memory mapped I/O, this function can be  */
/* used to greatly speed up emulation                                       */
/****************************************************************************/
#define M_RDOP(Addr) (read(Addr))

/****************************************************************************/
/* M6800_RDOP_ARG() is identical to M6800_RDOP() but it's used for reading  */
/* opcode arguments. This difference can be used to support systems that    */
/* use different encoding mechanisms for opcodes and opcode arguments       */
/****************************************************************************/
#define M_RDOP_ARG(Addr) (read(Addr))

/* macros to access memory */
#define IMMBYTE(b)  b = M_RDOP_ARG(PCD); PC++
#define IMMWORD(w)  w.d = (M_RDOP_ARG(PCD)<<8) | M_RDOP_ARG((PCD+1)&0xffff); PC+=2

#define PUSHBYTE(b) WM(SD,b); --S
#define PUSHWORD(w) WM(SD,w.b.l); --S; WM(SD,w.b.h); --S
#define PULLBYTE(b) S++; b = RM(SD)
#define PULLWORD(w) S++; w.d = RM(SD)<<8; S++; w.d |= RM(SD)

/* CC masks                       HI NZVC
                                7654 3210   */
#define CLR_HNZVC   CC&=0xd0
#define CLR_NZV     CC&=0xf1
#define CLR_HNZC    CC&=0xd2
#define CLR_NZVC    CC&=0xf0
#define CLR_Z       CC&=0xfb
#define CLR_ZC      CC&=0xfa
#define CLR_C       CC&=0xfe

/* macros for CC -- CC bits affected should be reset before calling */
#define SET_Z(a)        if(!(a))SEZ
#define SET_Z8(a)       SET_Z(u8(a))
#define SET_Z16(a)      SET_Z(u16(a))
#define SET_N8(a)       CC|=(((a)&0x80)>>4)
#define SET_N16(a)      CC|=(((a)&0x8000)>>12)
#define SET_H(a,b,r)    CC|=((((a)^(b)^(r))&0x10)<<1)
#define SET_C8(a)       CC|=(((a)&0x100)>>8)
#define SET_C16(a)      CC|=(((a)&0x10000)>>16)
#define SET_V8(a,b,r)   CC|=((((a)^(b)^(r)^((r)>>1))&0x80)>>6)
#define SET_V16(a,b,r)  CC|=((((a)^(b)^(r)^((r)>>1))&0x8000)>>14)

const u8 m6800_cpu_device::flags8i[256]= /* increment */
{
0x04,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x0a,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08
};


const u8 m6800_cpu_device::flags8d[256]= /* decrement */
{
0x04,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x02,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,
0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08,0x08
};

#define SET_FLAGS8I(a)      {CC|=flags8i[(a)&0xff];}
#define SET_FLAGS8D(a)      {CC|=flags8d[(a)&0xff];}

/* combos */
#define SET_NZ8(a)          {SET_N8(a);SET_Z8(a);}
#define SET_NZ16(a)         {SET_N16(a);SET_Z16(a);}
#define SET_FLAGS8(a,b,r)   {SET_N8(r);SET_Z8(r);SET_V8(a,b,r);SET_C8(r);}
#define SET_FLAGS16(a,b,r)  {SET_N16(r);SET_Z16(r);SET_V16(a,b,r);SET_C16(r);}

/* for treating an u8 as a signed s16 */
#define SIGNED(b) (s16(b&0x80?b|0xff00:b))

/* Macros for addressing modes */
#define DIRECT IMMBYTE(EAD)
#define IMM8 EA=PC++
#define IMM16 {EA=PC;PC+=2;}
#define EXTENDED IMMWORD(m_ea)
#define INDEXED {EA=X+(u8)M_RDOP_ARG(PCD);PC++;}

/* macros to set status flags */
#if defined(SEC)
#undef SEC
#endif
#define SEC CC|=0x01
#define CLC CC&=0xfe
#define SEZ CC|=0x04
#define CLZ CC&=0xfb
#define SEN CC|=0x08
#define CLN CC&=0xf7
#define SEV CC|=0x02
#define CLV CC&=0xfd
#define SEH CC|=0x20
#define CLH CC&=0xdf
#define SEI CC|=0x10
#define CLI CC&=~0x10

/* macros for convenience */
#define DIRBYTE(b) {DIRECT;b=RM(EAD);}
#define DIRWORD(w) {DIRECT;w.d=RM16(EAD);}
#define EXTBYTE(b) {EXTENDED;b=RM(EAD);}
#define EXTWORD(w) {EXTENDED;w.d=RM16(EAD);}

#define IDXBYTE(b) {INDEXED;b=RM(EAD);}
#define IDXWORD(w) {INDEXED;w.d=RM16(EAD);}

/* Macros for branch instructions */
#define BRANCH(f) {IMMBYTE(t);if(f){PC+=SIGNED(t);}}
#define NXORV  ((CC&0x08)^((CC&0x02)<<2))
#define NXORC  ((CC&0x08)^((CC&0x01)<<3))


#define OP_HANDLER(_name) void m6800_cpu_device::_name()
OP_HANDLER( illegl1 ) { fail_illegal(); }

OP_HANDLER( nop )
{
}

OP_HANDLER( tap )
{
	CC=A;

	// TAP temporarily sets the I flag and blocks IRQ until the next opcode
	// (if the next opcode is TAP, IRQ is blocked again)
	execute_one();
	check_irq_lines();
}

OP_HANDLER( tpa )
{
	A=CC;
}

OP_HANDLER( inx )
{
	++X;
	CLR_Z; SET_Z16(X);
}

OP_HANDLER( dex )
{
	--X;
	CLR_Z; SET_Z16(X);
}

OP_HANDLER( clv )
{
	CLV;
}

OP_HANDLER( sev )
{
	SEV;
}

OP_HANDLER( clc )
{
	CLC;
}

OP_HANDLER( sec )
{
	SEC;
}

OP_HANDLER( cli )
{
	u8 i = CC & 0x10;
	CLI;

	// pending IRQ won't be triggered until next machine cycle
	if (i) execute_one();
	check_irq_lines();
}

OP_HANDLER( sei )
{
	SEI;
}

OP_HANDLER( sba )
{
	u16 t;
	t=A-B;
	CLR_NZVC; SET_FLAGS8(A,B,t);
	A=t;
}

OP_HANDLER( cba )
{
	u16 t;
	t=A-B;
	CLR_NZVC; SET_FLAGS8(A,B,t);
}

OP_HANDLER( tab )
{
	B=A;
	CLR_NZV; SET_NZ8(B);
}

OP_HANDLER( tba )
{
	A=B;
	CLR_NZV; SET_NZ8(A);
}

OP_HANDLER( daa )
{
	u8 msn, lsn;
	u16 t, cf = 0;
	msn=A & 0xf0; lsn=A & 0x0f;
	if (lsn>0x09 || CC&0x20) cf |= 0x06;
	if (msn>0x80 && lsn>0x09) cf |= 0x60;
	if (msn>0x90 || CC&0x01) cf |= 0x60;
	t = cf + A;
	CLR_NZV; // keep carry from previous operation
	SET_NZ8((u8)t); SET_C8(t);
	A = t;
}

OP_HANDLER( aba )
{
	u16 t;
	t=A+B;
	CLR_HNZVC; SET_FLAGS8(A,B,t); SET_H(A,B,t);
	A=t;
}

OP_HANDLER( bra )
{
	u8 t;
	BRANCH(1);
}

OP_HANDLER( brn )
{
	u8 t;
	BRANCH(0);
}

OP_HANDLER( bhi )
{
	u8 t;
	BRANCH(!(CC&0x05));
}

OP_HANDLER( bls )
{
	u8 t;
	BRANCH(CC&0x05);
}

OP_HANDLER( bcc )
{
	u8 t;
	BRANCH(!(CC&0x01));
}

OP_HANDLER( bcs )
{
	u8 t;
	BRANCH(CC&0x01);
}

OP_HANDLER( bne )
{
	u8 t;
	BRANCH(!(CC&0x04));
}

OP_HANDLER( beq )
{
	u8 t;
	BRANCH(CC&0x04);
}

OP_HANDLER( bvc )
{
	u8 t;
	BRANCH(!(CC&0x02));
}

OP_HANDLER( bvs )
{
	u8 t;
	BRANCH(CC&0x02);
}

OP_HANDLER( bpl )
{
	u8 t;
	BRANCH(!(CC&0x08));
}

OP_HANDLER( bmi )
{
	u8 t;
	BRANCH(CC&0x08);
}

OP_HANDLER( bge )
{
	u8 t;
	BRANCH(!NXORV);
}

OP_HANDLER( blt )
{
	u8 t;
	BRANCH(NXORV);
}

OP_HANDLER( bgt )
{
	u8 t;
	BRANCH(!(NXORV||CC&0x04));
}

OP_HANDLER( ble )
{
	u8 t;
	BRANCH(NXORV||CC&0x04);
}

OP_HANDLER( tsx )
{
	X = ( S + 1 );
}

OP_HANDLER( ins )
{
	++S;
}

OP_HANDLER( pula )
{
	PULLBYTE(m_d.b.h);
}

OP_HANDLER( pulb )
{
	PULLBYTE(m_d.b.l);
}

OP_HANDLER( des )
{
	--S;
}

OP_HANDLER( txs )
{
	S = ( X - 1 );
}

OP_HANDLER( psha )
{
	PUSHBYTE(m_d.b.h);
}

OP_HANDLER( pshb )
{
	PUSHBYTE(m_d.b.l);
}

OP_HANDLER( rts )
{
	PULLWORD(pPC);
}

OP_HANDLER( rti )
{
	PULLBYTE(CC);
	PULLBYTE(B);
	PULLBYTE(A);
	PULLWORD(pX);
	PULLWORD(pPC);

	check_irq_lines();
}

OP_HANDLER( wai )
{
	// WAI stacks the entire machine state on the hardware stack,
	// then waits for an interrupt.
	m_wai_state |= M6800_WAI;

	PUSHWORD(pPC);
	PUSHWORD(pX);
	PUSHBYTE(A);
	PUSHBYTE(B);
	PUSHBYTE(CC);

	check_irq_lines();
	if (m_wai_state & M6800_WAI)
		eat_cycles();
}

OP_HANDLER( swi )
{
	PUSHWORD(pPC);
	PUSHWORD(pX);
	PUSHBYTE(A);
	PUSHBYTE(B);
	PUSHBYTE(CC);

	SEI;
	PCD = RM16(0xfffa);
}

OP_HANDLER( nega )
{
	u16 r;
	r=-A;
	CLR_NZVC; SET_FLAGS8(0,A,r);
	A=r;
}

OP_HANDLER( coma )
{
	A = ~A;
	CLR_NZV; SET_NZ8(A); SEC;
}

OP_HANDLER( lsra )
{
	CLR_NZVC; CC|=(A&0x01);
	A>>=1; SET_Z8(A);
	if (NXORC) SEV;
}

OP_HANDLER( rora )
{
	u8 r;
	r=(CC&0x01)<<7;
	CLR_NZVC; CC|=(A&0x01);
	r |= A>>1; SET_NZ8(r);
	if (NXORC) SEV;
	A=r;
}

OP_HANDLER( asra )
{
	CLR_NZVC; CC|=(A&0x01);
	A>>=1; A|=((A&0x40)<<1);
	SET_NZ8(A);
	if (NXORC) SEV;
}

OP_HANDLER( asla )
{
	u16 r;
	r=A<<1;
	CLR_NZVC; SET_FLAGS8(A,A,r);
	A=r;
}

OP_HANDLER( rola )
{
	u16 t,r;
	t = A; r = CC&0x01; r |= t<<1;
	CLR_NZVC; SET_FLAGS8(t,t,r);
	A=r;
}

OP_HANDLER( deca )
{
	--A;
	CLR_NZV; SET_FLAGS8D(A);
}

OP_HANDLER( inca )
{
	++A;
	CLR_NZV; SET_FLAGS8I(A);
}

OP_HANDLER( tsta )
{
	CLR_NZVC; SET_NZ8(A);
}

OP_HANDLER( clra )
{
	A=0;
	CLR_NZVC; SEZ;
}

OP_HANDLER( negb )
{
	u16 r;
	r=-B;
	CLR_NZVC; SET_FLAGS8(0,B,r);
	B=r;
}

OP_HANDLER( comb )
{
	B = ~B;
	CLR_NZV; SET_NZ8(B); SEC;
}

OP_HANDLER( lsrb )
{
	CLR_NZVC; CC|=(B&0x01);
	B>>=1; SET_Z8(B);
	if (NXORC) SEV;
}

OP_HANDLER( rorb )
{
	u8 r;
	r=(CC&0x01)<<7;
	CLR_NZVC; CC|=(B&0x01);
	r |= B>>1; SET_NZ8(r);
	if (NXORC) SEV;
	B=r;
}

OP_HANDLER( asrb )
{
	CLR_NZVC; CC|=(B&0x01);
	B>>=1; B|=((B&0x40)<<1);
	SET_NZ8(B);
	if (NXORC) SEV;
}

OP_HANDLER( aslb )
{
	u16 r;
	r=B<<1;
	CLR_NZVC; SET_FLAGS8(B,B,r);
	B=r;
}

OP_HANDLER( rolb )
{
	u16 t,r;
	t = B; r = CC&0x01; r |= t<<1;
	CLR_NZVC; SET_FLAGS8(t,t,r);
	B=r;
}

OP_HANDLER( decb )
{
	--B;
	CLR_NZV; SET_FLAGS8D(B);
}

OP_HANDLER( incb )
{
	++B;
	CLR_NZV; SET_FLAGS8I(B);
}

OP_HANDLER( tstb )
{
	CLR_NZVC; SET_NZ8(B);
}

OP_HANDLER( clrb )
{
	B=0;
	CLR_NZVC; SEZ;
}

OP_HANDLER( neg_ix )
{
	u16 r,t;
	IDXBYTE(t); r=-t;
	CLR_NZVC; SET_FLAGS8(0,t,r);
	WM(EAD,r);
}

OP_HANDLER( illegl2 ) { fail_illegal(); }

OP_HANDLER( com_ix )
{
	u8 t;
	IDXBYTE(t); t = ~t;
	CLR_NZV; SET_NZ8(t); SEC;
	WM(EAD,t);
}

OP_HANDLER( lsr_ix )
{
	u8 t;
	IDXBYTE(t); CLR_NZVC; CC|=(t&0x01);
	t>>=1; SET_Z8(t);
	if (NXORC) SEV;
	WM(EAD,t);
}

OP_HANDLER( ror_ix )
{
	u8 t,r;
	IDXBYTE(t); r=(CC&0x01)<<7;
	CLR_NZVC; CC|=(t&0x01);
	r |= t>>1; SET_NZ8(r);
	if (NXORC) SEV;
	WM(EAD,r);
}

OP_HANDLER( asr_ix )
{
	u8 t;
	IDXBYTE(t); CLR_NZVC; CC|=(t&0x01);
	t>>=1; t|=((t&0x40)<<1);
	SET_NZ8(t);
	if (NXORC) SEV;
	WM(EAD,t);
}

OP_HANDLER( asl_ix )
{
	u16 t,r;
	IDXBYTE(t); r=t<<1;
	CLR_NZVC; SET_FLAGS8(t,t,r);
	WM(EAD,r);
}

OP_HANDLER( rol_ix )
{
	u16 t,r;
	IDXBYTE(t); r = CC&0x01; r |= t<<1;
	CLR_NZVC; SET_FLAGS8(t,t,r);
	WM(EAD,r);
}

OP_HANDLER( dec_ix )
{
	u8 t;
	IDXBYTE(t); --t;
	CLR_NZV; SET_FLAGS8D(t);
	WM(EAD,t);
}

OP_HANDLER( inc_ix )
{
	u8 t;
	IDXBYTE(t); ++t;
	CLR_NZV; SET_FLAGS8I(t);
	WM(EAD,t);
}

OP_HANDLER( tst_ix )
{
	u8 t;
	IDXBYTE(t); CLR_NZVC; SET_NZ8(t);
}

OP_HANDLER( jmp_ix )
{
	INDEXED; PC=EA;
}

OP_HANDLER( clr_ix )
{
	INDEXED; RM(EAD); WM(EAD,0);
	CLR_NZVC; SEZ;
}

OP_HANDLER( neg_ex )
{
	u16 r,t;
	EXTBYTE(t); r=-t;
	CLR_NZVC; SET_FLAGS8(0,t,r);
	WM(EAD,r);
}

OP_HANDLER( illegl3 ) { fail_illegal(); }

OP_HANDLER( com_ex )
{
	u8 t;
	EXTBYTE(t); t = ~t;
	CLR_NZV; SET_NZ8(t); SEC;
	WM(EAD,t);
}

OP_HANDLER( lsr_ex )
{
	u8 t;
	EXTBYTE(t);
	CLR_NZVC;
	CC|=(t&0x01);
	t>>=1;
	SET_Z8(t);
	if (NXORC) SEV;
	WM(EAD,t);
}

OP_HANDLER( ror_ex )
{
	u8 t,r;
	EXTBYTE(t); r=(CC&0x01)<<7;
	CLR_NZVC; CC|=(t&0x01);
	r |= t>>1; SET_NZ8(r);
	if (NXORC) SEV;
	WM(EAD,r);
}

OP_HANDLER( asr_ex )
{
	u8 t;
	EXTBYTE(t); CLR_NZVC; CC|=(t&0x01);
	t>>=1; t|=((t&0x40)<<1);
	SET_NZ8(t);
	if (NXORC) SEV;
	WM(EAD,t);
}

OP_HANDLER( asl_ex )
{
	u16 t,r;
	EXTBYTE(t); r=t<<1;
	CLR_NZVC; SET_FLAGS8(t,t,r);
	WM(EAD,r);
}

OP_HANDLER( rol_ex )
{
	u16 t,r;
	EXTBYTE(t); r = CC&0x01; r |= t<<1;
	CLR_NZVC; SET_FLAGS8(t,t,r);
	WM(EAD,r);
}

OP_HANDLER( dec_ex )
{
	u8 t;
	EXTBYTE(t); --t;
	CLR_NZV; SET_FLAGS8D(t);
	WM(EAD,t);
}

OP_HANDLER( inc_ex )
{
	u8 t;
	EXTBYTE(t); ++t;
	CLR_NZV; SET_FLAGS8I(t);
	WM(EAD,t);
}

OP_HANDLER( tst_ex )
{
	u8 t;
	EXTBYTE(t); CLR_NZVC; SET_NZ8(t);
}

OP_HANDLER( jmp_ex )
{
	EXTENDED; PC=EA;
}

OP_HANDLER( clr_ex )
{
	EXTENDED; RM(EAD); WM(EAD,0);
	CLR_NZVC; SEZ;
}

OP_HANDLER( suba_im )
{
	u16    t,r;
	IMMBYTE(t); r = A-t;
	CLR_NZVC; SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( cmpa_im )
{
	u16    t,r;
	IMMBYTE(t); r = A-t;
	CLR_NZVC; SET_FLAGS8(A,t,r);
}

OP_HANDLER( sbca_im )
{
	u16    t,r;
	IMMBYTE(t); r = A-t-(CC&0x01);
	CLR_NZVC; SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( anda_im )
{
	u8 t;
	IMMBYTE(t); A &= t;
	CLR_NZV; SET_NZ8(A);
}

OP_HANDLER( bita_im )
{
	u8 t,r;
	IMMBYTE(t); r = A&t;
	CLR_NZV; SET_NZ8(r);
}

OP_HANDLER( lda_im )
{
	IMMBYTE(A);
	CLR_NZV; SET_NZ8(A);
}

OP_HANDLER( sta_im )
{
	CLR_NZV; SET_NZ8(A);
	IMM8; WM(EAD,A);
}

OP_HANDLER( eora_im )
{
	u8 t;
	IMMBYTE(t); A ^= t;
	CLR_NZV; SET_NZ8(A);
}

OP_HANDLER( adca_im )
{
	u16 t,r;
	IMMBYTE(t); r = A+t+(CC&0x01);
	CLR_HNZVC; SET_FLAGS8(A,t,r); SET_H(A,t,r);
	A = r;
}

OP_HANDLER( ora_im )
{
	u8 t;
	IMMBYTE(t); A |= t;
	CLR_NZV; SET_NZ8(A);
}

OP_HANDLER( adda_im )
{
	u16 t,r;
	IMMBYTE(t); r = A+t;
	CLR_HNZVC; SET_FLAGS8(A,t,r); SET_H(A,t,r);
	A = r;
}

OP_HANDLER( cmpx_im )
{
	PAIR r,d,b;
	IMMWORD(b);
	d.d = X;
	r.w.l = d.b.h - b.b.h;
	CLR_NZV;
	SET_N8(r.b.l);
	SET_V8(d.b.h, b.b.h, r.w.l);
	r.d = d.d - b.d;
	SET_Z16(r.d);
}

OP_HANDLER( bsr )
{
	u8 t;
	IMMBYTE(t);
	PUSHWORD(pPC);
	PC += SIGNED(t);
}

OP_HANDLER( lds_im )
{
	IMMWORD(m_s);
	CLR_NZV;
	SET_NZ16(S);
}

OP_HANDLER( sts_im )
{
	CLR_NZV;
	SET_NZ16(S);
	IMM16;
	WM16(EAD,&m_s);
}

OP_HANDLER( suba_di )
{
	u16    t,r;
	DIRBYTE(t); r = A-t;
	CLR_NZVC; SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( cmpa_di )
{
	u16    t,r;
	DIRBYTE(t); r = A-t;
	CLR_NZVC; SET_FLAGS8(A,t,r);
}

OP_HANDLER( sbca_di )
{
	u16    t,r;
	DIRBYTE(t); r = A-t-(CC&0x01);
	CLR_NZVC; SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( anda_di )
{
	u8 t;
	DIRBYTE(t); A &= t;
	CLR_NZV; SET_NZ8(A);
}

OP_HANDLER( bita_di )
{
	u8 t,r;
	DIRBYTE(t); r = A&t;
	CLR_NZV; SET_NZ8(r);
}

OP_HANDLER( lda_di )
{
	DIRBYTE(A);
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( sta_di )
{
	CLR_NZV;
	SET_NZ8(A);
	DIRECT;
	WM(EAD,A);
}

OP_HANDLER( eora_di )
{
	u8 t;
	DIRBYTE(t);
	A ^= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( adca_di )
{
	u16 t,r;
	DIRBYTE(t);
	r = A+t+(CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(A,t,r);
	SET_H(A,t,r);
	A = r;
}

OP_HANDLER( ora_di )
{
	u8 t;
	DIRBYTE(t);
	A |= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( adda_di )
{
	u16 t,r;
	DIRBYTE(t);
	r = A + t;
	CLR_HNZVC;
	SET_FLAGS8(A,t,r);
	SET_H(A,t,r);
	A = r;
}

OP_HANDLER( cmpx_di )
{
	PAIR r,d,b;
	DIRWORD(b);
	d.d = X;
	r.w.l = d.b.h - b.b.h;
	CLR_NZV;
	SET_N8(r.b.l);
	SET_V8(d.b.h, b.b.h, r.w.l);
	r.d = d.d - b.d;
	SET_Z16(r.d);
}

OP_HANDLER( jsr_di )
{
	DIRECT;
	PUSHWORD(pPC);
	PC = EA;
}

OP_HANDLER( lds_di )
{
	DIRWORD(m_s);
	CLR_NZV;
	SET_NZ16(S);
}

OP_HANDLER( sts_di )
{
	CLR_NZV;
	SET_NZ16(S);
	DIRECT;
	WM16(EAD,&m_s);
}

OP_HANDLER( suba_ix )
{
	u16    t,r;
	IDXBYTE(t);
	r = A - t;
	CLR_NZVC;
	SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( cmpa_ix )
{
	u16    t,r;
	IDXBYTE(t);
	r = A - t;
	CLR_NZVC;
	SET_FLAGS8(A,t,r);
}

OP_HANDLER( sbca_ix )
{
	u16    t,r;
	IDXBYTE(t);
	r = A - t - (CC&0x01);
	CLR_NZVC;
	SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( anda_ix )
{
	u8 t;
	IDXBYTE(t); A &= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( bita_ix )
{
	u8 t,r;
	IDXBYTE(t); r = A&t;
	CLR_NZV;
	SET_NZ8(r);
}

OP_HANDLER( lda_ix )
{
	IDXBYTE(A);
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( sta_ix )
{
	CLR_NZV;
	SET_NZ8(A);
	INDEXED;
	WM(EAD,A);
}

OP_HANDLER( eora_ix )
{
	u8 t;
	IDXBYTE(t);
	A ^= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( adca_ix )
{
	u16 t,r;
	IDXBYTE(t);
	r = A + t + (CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(A,t,r);
	SET_H(A,t,r);
	A = r;
}

OP_HANDLER( ora_ix )
{
	u8 t;
	IDXBYTE(t);
	A |= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( adda_ix )
{
	u16 t,r;
	IDXBYTE(t);
	r = A+t;
	CLR_HNZVC;
	SET_FLAGS8(A,t,r);
	SET_H(A,t,r);
	A = r;
}

OP_HANDLER( cmpx_ix )
{
	PAIR r,d,b;
	IDXWORD(b);
	d.d = X;
	r.w.l = d.b.h - b.b.h;
	CLR_NZV;
	SET_N8(r.b.l);
	SET_V8(d.b.h, b.b.h, r.w.l);
	r.d = d.d - b.d;
	SET_Z16(r.d);
}

OP_HANDLER( jsr_ix )
{
	INDEXED;
	PUSHWORD(pPC);
	PC = EA;
}

OP_HANDLER( lds_ix )
{
	IDXWORD(m_s);
	CLR_NZV;
	SET_NZ16(S);
}

OP_HANDLER( sts_ix )
{
	CLR_NZV;
	SET_NZ16(S);
	INDEXED;
	WM16(EAD,&m_s);
}

OP_HANDLER( suba_ex )
{
	u16    t,r;
	EXTBYTE(t);
	r = A - t;
	CLR_NZVC;
	SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( cmpa_ex )
{
	u16    t,r;
	EXTBYTE(t);
	r = A-t;
	CLR_NZVC;
	SET_FLAGS8(A,t,r);
}

OP_HANDLER( sbca_ex )
{
	u16    t,r;
	EXTBYTE(t);
	r = A-t-(CC&0x01);
	CLR_NZVC;
	SET_FLAGS8(A,t,r);
	A = r;
}

OP_HANDLER( anda_ex )
{
	u8 t;
	EXTBYTE(t);
	A &= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( bita_ex )
{
	u8 t,r;
	EXTBYTE(t);
	r = A&t;
	CLR_NZV;
	SET_NZ8(r);
}

OP_HANDLER( lda_ex )
{
	EXTBYTE(A);
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( sta_ex )
{
	CLR_NZV;
	SET_NZ8(A);
	EXTENDED;
	WM(EAD,A);
}

OP_HANDLER( eora_ex )
{
	u8 t;
	EXTBYTE(t);
	A ^= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( adca_ex )
{
	u16 t,r;
	EXTBYTE(t);
	r = A+t+(CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(A,t,r);
	SET_H(A,t,r);
	A = r;
}

OP_HANDLER( ora_ex )
{
	u8 t;
	EXTBYTE(t);
	A |= t;
	CLR_NZV;
	SET_NZ8(A);
}

OP_HANDLER( adda_ex )
{
	u16 t,r;
	EXTBYTE(t);
	r = A+t;
	CLR_HNZVC;
	SET_FLAGS8(A,t,r);
	SET_H(A,t,r);
	A = r;
}

OP_HANDLER( cmpx_ex )
{
	PAIR r,d,b;
	EXTWORD(b);
	d.d = X;
	r.w.l = d.b.h - b.b.h;
	CLR_NZV;
	SET_N8(r.b.l);
	SET_V8(d.b.h, b.b.h, r.w.l);
	r.d = d.d - b.d;
	SET_Z16(r.d);
}

OP_HANDLER( jsr_ex )
{
	EXTENDED;
	PUSHWORD(pPC);
	PC = EA;
}

OP_HANDLER( lds_ex )
{
	EXTWORD(m_s);
	CLR_NZV;
	SET_NZ16(S);
}

OP_HANDLER( sts_ex )
{
	CLR_NZV;
	SET_NZ16(S);
	EXTENDED;
	WM16(EAD,&m_s);
}

OP_HANDLER( subb_im )
{
	u16    t,r;
	IMMBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( cmpb_im )
{
	u16    t,r;
	IMMBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
}

OP_HANDLER( sbcb_im )
{
	u16    t,r;
	IMMBYTE(t);
	r = B-t-(CC&0x01);
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( andb_im )
{
	u8 t;
	IMMBYTE(t);
	B &= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( bitb_im )
{
	u8 t,r;
	IMMBYTE(t);
	r = B&t;
	CLR_NZV;
	SET_NZ8(r);
}

OP_HANDLER( ldb_im )
{
	IMMBYTE(B);
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( stb_im )
{
	CLR_NZV;
	SET_NZ8(B);
	IMM8;
	WM(EAD,B);
}

OP_HANDLER( eorb_im )
{
	u8 t;
	IMMBYTE(t);
	B ^= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( adcb_im )
{
	u16 t,r;
	IMMBYTE(t);
	r = B+t+(CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( orb_im )
{
	u8 t;
	IMMBYTE(t);
	B |= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( addb_im )
{
	u16 t,r;
	IMMBYTE(t);
	r = B+t;
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( ldx_im )
{
	IMMWORD(m_x);
	CLR_NZV;
	SET_NZ16(X);
}

OP_HANDLER( stx_im )
{
	CLR_NZV;
	SET_NZ16(X);
	IMM16;
	WM16(EAD,&m_x);
}

OP_HANDLER( subb_di )
{
	u16    t,r;
	DIRBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( cmpb_di )
{
	u16    t,r;
	DIRBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
}

OP_HANDLER( sbcb_di )
{
	u16    t,r;
	DIRBYTE(t);
	r = B-t-(CC&0x01);
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( andb_di )
{
	u8 t;
	DIRBYTE(t);
	B &= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( bitb_di )
{
	u8 t,r;
	DIRBYTE(t);
	r = B&t;
	CLR_NZV;
	SET_NZ8(r);
}

OP_HANDLER( ldb_di )
{
	DIRBYTE(B);
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( stb_di )
{
	CLR_NZV;
	SET_NZ8(B);
	DIRECT;
	WM(EAD,B);
}

OP_HANDLER( eorb_di )
{
	u8 t;
	DIRBYTE(t);
	B ^= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( adcb_di )
{
	u16 t,r;
	DIRBYTE(t);
	r = B+t+(CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( orb_di )
{
	u8 t;
	DIRBYTE(t);
	B |= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( addb_di )
{
	u16 t,r;
	DIRBYTE(t);
	r = B+t;
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( ldx_di )
{
	DIRWORD(m_x);
	CLR_NZV;
	SET_NZ16(X);
}

OP_HANDLER( stx_di )
{
	CLR_NZV;
	SET_NZ16(X);
	DIRECT;
	WM16(EAD,&m_x);
}

OP_HANDLER( subb_ix )
{
	u16    t,r;
	IDXBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( cmpb_ix )
{
	u16    t,r;
	IDXBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
}

OP_HANDLER( sbcb_ix )
{
	u16    t,r;
	IDXBYTE(t);
	r = B-t-(CC&0x01);
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( andb_ix )
{
	u8 t;
	IDXBYTE(t);
	B &= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( bitb_ix )
{
	u8 t,r;
	IDXBYTE(t);
	r = B&t;
	CLR_NZV;
	SET_NZ8(r);
}

OP_HANDLER( ldb_ix )
{
	IDXBYTE(B);
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( stb_ix )
{
	CLR_NZV;
	SET_NZ8(B);
	INDEXED;
	WM(EAD,B);
}

OP_HANDLER( eorb_ix )
{
	u8 t;
	IDXBYTE(t);
	B ^= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( adcb_ix )
{
	u16 t,r;
	IDXBYTE(t);
	r = B+t+(CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( orb_ix )
{
	u8 t;
	IDXBYTE(t);
	B |= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( addb_ix )
{
	u16 t,r;
	IDXBYTE(t);
	r = B+t;
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( ldx_ix )
{
	IDXWORD(m_x);
	CLR_NZV;
	SET_NZ16(X);
}

OP_HANDLER( stx_ix )
{
	CLR_NZV;
	SET_NZ16(X);
	INDEXED;
	WM16(EAD,&m_x);
}

OP_HANDLER( subb_ex )
{
	u16    t,r;
	EXTBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( cmpb_ex )
{
	u16    t,r;
	EXTBYTE(t);
	r = B-t;
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
}

OP_HANDLER( sbcb_ex )
{
	u16    t,r;
	EXTBYTE(t);
	r = B-t-(CC&0x01);
	CLR_NZVC;
	SET_FLAGS8(B,t,r);
	B = r;
}

OP_HANDLER( andb_ex )
{
	u8 t;
	EXTBYTE(t);
	B &= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( bitb_ex )
{
	u8 t,r;
	EXTBYTE(t);
	r = B & t;
	CLR_NZV;
	SET_NZ8(r);
}

OP_HANDLER( ldb_ex )
{
	EXTBYTE(B);
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( stb_ex )
{
	CLR_NZV;
	SET_NZ8(B);
	EXTENDED;
	WM(EAD,B);
}

OP_HANDLER( eorb_ex )
{
	u8 t;
	EXTBYTE(t);
	B ^= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( adcb_ex )
{
	u16 t,r;
	EXTBYTE(t);
	r = B+t+(CC&0x01);
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( orb_ex )
{
	u8 t;
	EXTBYTE(t);
	B |= t;
	CLR_NZV;
	SET_NZ8(B);
}

OP_HANDLER( addb_ex )
{
	u16 t,r;
	EXTBYTE(t);
	r = B+t;
	CLR_HNZVC;
	SET_FLAGS8(B,t,r);
	SET_H(B,t,r);
	B = r;
}

OP_HANDLER( ldx_ex )
{
	EXTWORD(m_x);
	CLR_NZV;
	SET_NZ16(X);
}

OP_HANDLER( stx_ex )
{
	CLR_NZV;
	SET_NZ16(X);
	EXTENDED;
	WM16(EAD,&m_x);
}
#define XX 4
const u8 m6800_cpu_device::cycles_6800[256] =
{
		/* 0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F */
	/*0*/ XX, 2,XX,XX,XX,XX, 2, 2, 4, 4, 2, 2, 2, 2, 2, 2,
	/*1*/  2, 2,XX,XX,XX,XX, 2, 2,XX, 2,XX, 2,XX,XX,XX,XX,
	/*2*/  4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
	/*3*/  4, 4, 4, 4, 4, 4, 4, 4,XX, 5,XX,10,XX,XX, 9,12,
	/*4*/  2,XX,XX, 2, 2,XX, 2, 2, 2, 2, 2,XX, 2, 2,XX, 2,
	/*5*/  2,XX,XX, 2, 2,XX, 2, 2, 2, 2, 2,XX, 2, 2,XX, 2,
	/*6*/  7,XX,XX, 7, 7,XX, 7, 7, 7, 7, 7,XX, 7, 7, 4, 7,
	/*7*/  6,XX,XX, 6, 6,XX, 6, 6, 6, 6, 6,XX, 6, 6, 3, 6,
	/*8*/  2, 2, 2,XX, 2, 2, 2, 3, 2, 2, 2, 2, 3, 8, 3, 4,
	/*9*/  3, 3, 3,XX, 3, 3, 3, 4, 3, 3, 3, 3, 4, 6, 4, 5,
	/*A*/  5, 5, 5,XX, 5, 5, 5, 6, 5, 5, 5, 5, 6, 8, 6, 7,
	/*B*/  4, 4, 4,XX, 4, 4, 4, 5, 4, 4, 4, 4, 5, 9, 5, 6,
	/*C*/  2, 2, 2,XX, 2, 2, 2, 3, 2, 2, 2, 2,XX,XX, 3, 4,
	/*D*/  3, 3, 3,XX, 3, 3, 3, 4, 3, 3, 3, 3,XX,XX, 4, 5,
	/*E*/  5, 5, 5,XX, 5, 5, 5, 6, 5, 5, 5, 5,XX,XX, 6, 7,
	/*F*/  4, 4, 4,XX, 4, 4, 4, 5, 4, 4, 4, 4,XX,XX, 5, 6
};


#undef XX
const m6800_cpu_device::op_func m6800_cpu_device::m6800_insn[0x100] = {
// 0/8                     1/9                        2/A                        3/B                        4/C                        5/D                        6/E                        7/F
&m6800_cpu_device::illegl1,&m6800_cpu_device::nop,    &m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::tap,    &m6800_cpu_device::tpa,    // 0
&m6800_cpu_device::inx,    &m6800_cpu_device::dex,    &m6800_cpu_device::clv,    &m6800_cpu_device::sev,    &m6800_cpu_device::clc,    &m6800_cpu_device::sec,    &m6800_cpu_device::cli,    &m6800_cpu_device::sei,
&m6800_cpu_device::sba,    &m6800_cpu_device::cba,    &m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::tab,    &m6800_cpu_device::tba,    // 1
&m6800_cpu_device::illegl1,&m6800_cpu_device::daa,    &m6800_cpu_device::illegl1,&m6800_cpu_device::aba,    &m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,
&m6800_cpu_device::bra,    &m6800_cpu_device::brn,    &m6800_cpu_device::bhi,    &m6800_cpu_device::bls,    &m6800_cpu_device::bcc,    &m6800_cpu_device::bcs,    &m6800_cpu_device::bne,    &m6800_cpu_device::beq,    // 2
&m6800_cpu_device::bvc,    &m6800_cpu_device::bvs,    &m6800_cpu_device::bpl,    &m6800_cpu_device::bmi,    &m6800_cpu_device::bge,    &m6800_cpu_device::blt,    &m6800_cpu_device::bgt,    &m6800_cpu_device::ble,
&m6800_cpu_device::tsx,    &m6800_cpu_device::ins,    &m6800_cpu_device::pula,   &m6800_cpu_device::pulb,   &m6800_cpu_device::des,    &m6800_cpu_device::txs,    &m6800_cpu_device::psha,   &m6800_cpu_device::pshb,   // 3
&m6800_cpu_device::illegl1,&m6800_cpu_device::rts,    &m6800_cpu_device::illegl1,&m6800_cpu_device::rti,    &m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::wai,    &m6800_cpu_device::swi,
&m6800_cpu_device::nega,   &m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::coma,   &m6800_cpu_device::lsra,   &m6800_cpu_device::illegl1,&m6800_cpu_device::rora,   &m6800_cpu_device::asra,   // 4
&m6800_cpu_device::asla,   &m6800_cpu_device::rola,   &m6800_cpu_device::deca,   &m6800_cpu_device::illegl1,&m6800_cpu_device::inca,   &m6800_cpu_device::tsta,   &m6800_cpu_device::illegl1,&m6800_cpu_device::clra,
&m6800_cpu_device::negb,   &m6800_cpu_device::illegl1,&m6800_cpu_device::illegl1,&m6800_cpu_device::comb,   &m6800_cpu_device::lsrb,   &m6800_cpu_device::illegl1,&m6800_cpu_device::rorb,   &m6800_cpu_device::asrb,   // 5
&m6800_cpu_device::aslb,   &m6800_cpu_device::rolb,   &m6800_cpu_device::decb,   &m6800_cpu_device::illegl1,&m6800_cpu_device::incb,   &m6800_cpu_device::tstb,   &m6800_cpu_device::illegl1,&m6800_cpu_device::clrb,
&m6800_cpu_device::neg_ix, &m6800_cpu_device::illegl2,&m6800_cpu_device::illegl2,&m6800_cpu_device::com_ix, &m6800_cpu_device::lsr_ix, &m6800_cpu_device::illegl2,&m6800_cpu_device::ror_ix, &m6800_cpu_device::asr_ix, // 6
&m6800_cpu_device::asl_ix, &m6800_cpu_device::rol_ix, &m6800_cpu_device::dec_ix, &m6800_cpu_device::illegl2,&m6800_cpu_device::inc_ix, &m6800_cpu_device::tst_ix, &m6800_cpu_device::jmp_ix, &m6800_cpu_device::clr_ix,
&m6800_cpu_device::neg_ex, &m6800_cpu_device::illegl3,&m6800_cpu_device::illegl3,&m6800_cpu_device::com_ex, &m6800_cpu_device::lsr_ex, &m6800_cpu_device::illegl3,&m6800_cpu_device::ror_ex, &m6800_cpu_device::asr_ex, // 7
&m6800_cpu_device::asl_ex, &m6800_cpu_device::rol_ex, &m6800_cpu_device::dec_ex, &m6800_cpu_device::illegl3,&m6800_cpu_device::inc_ex, &m6800_cpu_device::tst_ex, &m6800_cpu_device::jmp_ex, &m6800_cpu_device::clr_ex,
&m6800_cpu_device::suba_im,&m6800_cpu_device::cmpa_im,&m6800_cpu_device::sbca_im,&m6800_cpu_device::illegl2,&m6800_cpu_device::anda_im,&m6800_cpu_device::bita_im,&m6800_cpu_device::lda_im, &m6800_cpu_device::sta_im, // 8
&m6800_cpu_device::eora_im,&m6800_cpu_device::adca_im,&m6800_cpu_device::ora_im, &m6800_cpu_device::adda_im,&m6800_cpu_device::cmpx_im,&m6800_cpu_device::bsr,    &m6800_cpu_device::lds_im, &m6800_cpu_device::sts_im,
&m6800_cpu_device::suba_di,&m6800_cpu_device::cmpa_di,&m6800_cpu_device::sbca_di,&m6800_cpu_device::illegl2,&m6800_cpu_device::anda_di,&m6800_cpu_device::bita_di,&m6800_cpu_device::lda_di, &m6800_cpu_device::sta_di, // 9
&m6800_cpu_device::eora_di,&m6800_cpu_device::adca_di,&m6800_cpu_device::ora_di, &m6800_cpu_device::adda_di,&m6800_cpu_device::cmpx_di,&m6800_cpu_device::jsr_di, &m6800_cpu_device::lds_di, &m6800_cpu_device::sts_di,
&m6800_cpu_device::suba_ix,&m6800_cpu_device::cmpa_ix,&m6800_cpu_device::sbca_ix,&m6800_cpu_device::illegl2,&m6800_cpu_device::anda_ix,&m6800_cpu_device::bita_ix,&m6800_cpu_device::lda_ix, &m6800_cpu_device::sta_ix, // A
&m6800_cpu_device::eora_ix,&m6800_cpu_device::adca_ix,&m6800_cpu_device::ora_ix, &m6800_cpu_device::adda_ix,&m6800_cpu_device::cmpx_ix,&m6800_cpu_device::jsr_ix, &m6800_cpu_device::lds_ix, &m6800_cpu_device::sts_ix,
&m6800_cpu_device::suba_ex,&m6800_cpu_device::cmpa_ex,&m6800_cpu_device::sbca_ex,&m6800_cpu_device::illegl3,&m6800_cpu_device::anda_ex,&m6800_cpu_device::bita_ex,&m6800_cpu_device::lda_ex, &m6800_cpu_device::sta_ex, // B
&m6800_cpu_device::eora_ex,&m6800_cpu_device::adca_ex,&m6800_cpu_device::ora_ex, &m6800_cpu_device::adda_ex,&m6800_cpu_device::cmpx_ex,&m6800_cpu_device::jsr_ex, &m6800_cpu_device::lds_ex, &m6800_cpu_device::sts_ex,
&m6800_cpu_device::subb_im,&m6800_cpu_device::cmpb_im,&m6800_cpu_device::sbcb_im,&m6800_cpu_device::illegl2,&m6800_cpu_device::andb_im,&m6800_cpu_device::bitb_im,&m6800_cpu_device::ldb_im, &m6800_cpu_device::stb_im, // C
&m6800_cpu_device::eorb_im,&m6800_cpu_device::adcb_im,&m6800_cpu_device::orb_im, &m6800_cpu_device::addb_im,&m6800_cpu_device::illegl3,&m6800_cpu_device::illegl3,&m6800_cpu_device::ldx_im, &m6800_cpu_device::stx_im,
&m6800_cpu_device::subb_di,&m6800_cpu_device::cmpb_di,&m6800_cpu_device::sbcb_di,&m6800_cpu_device::illegl2,&m6800_cpu_device::andb_di,&m6800_cpu_device::bitb_di,&m6800_cpu_device::ldb_di, &m6800_cpu_device::stb_di, // D
&m6800_cpu_device::eorb_di,&m6800_cpu_device::adcb_di,&m6800_cpu_device::orb_di, &m6800_cpu_device::addb_di,&m6800_cpu_device::illegl2,&m6800_cpu_device::illegl2,&m6800_cpu_device::ldx_di, &m6800_cpu_device::stx_di,
&m6800_cpu_device::subb_ix,&m6800_cpu_device::cmpb_ix,&m6800_cpu_device::sbcb_ix,&m6800_cpu_device::illegl2,&m6800_cpu_device::andb_ix,&m6800_cpu_device::bitb_ix,&m6800_cpu_device::ldb_ix, &m6800_cpu_device::stb_ix, // E
&m6800_cpu_device::eorb_ix,&m6800_cpu_device::adcb_ix,&m6800_cpu_device::orb_ix, &m6800_cpu_device::addb_ix,&m6800_cpu_device::illegl2,&m6800_cpu_device::illegl2,&m6800_cpu_device::ldx_ix, &m6800_cpu_device::stx_ix,
&m6800_cpu_device::subb_ex,&m6800_cpu_device::cmpb_ex,&m6800_cpu_device::sbcb_ex,&m6800_cpu_device::illegl3,&m6800_cpu_device::andb_ex,&m6800_cpu_device::bitb_ex,&m6800_cpu_device::ldb_ex, &m6800_cpu_device::stb_ex, // F
&m6800_cpu_device::eorb_ex,&m6800_cpu_device::adcb_ex,&m6800_cpu_device::orb_ex, &m6800_cpu_device::addb_ex,&m6800_cpu_device::illegl3,&m6800_cpu_device::illegl3,&m6800_cpu_device::ldx_ex, &m6800_cpu_device::stx_ex
};


m6800_cpu_device::m6800_cpu_device(const u8 *rom, size_t size,
        read_callback read_cb, write_callback write_cb, void *opaque)
  : read_io(read_cb), write_io(write_cb), context(opaque) {
    memory.fill(0xff);
    std::fill(memory.begin(),memory.begin()+0x100,0);
    std::fill(memory.begin()+0xb000,memory.end(),0);
    if (size==4096) std::copy(rom,rom+size,memory.begin()+0xf000);
    else if (size==65536) std::copy(rom,rom+size,memory.begin());
    else {failed=true; std::snprintf(error,sizeof(error),"Expected 4096 or 65536 ROM bytes; got %zu",size);}
    reset();
}
u8 m6800_cpu_device::read(u32 addr) {
    addr &= 0xffff;
    if ((addr&0x7ffc)==0x0400) return read_io ? read_io(context,u16(addr&0x7fff)) : 0xff;
    if (addr<0x100 || addr>=0xb000) return memory[addr];
    return 0xff;
}
void m6800_cpu_device::write(u32 addr,u8 value) {
    addr &= 0xffff;
    if ((addr&0x7ffc)==0x0400) {
        if (write_io) write_io(context,u16(addr&0x7fff),value,cycles);
    } else if (addr<0x100) memory[addr]=value;
}
u32 m6800_cpu_device::RM16(u32 addr) {
    return (u32(read(addr))<<8)|read((addr+1)&0xffff);
}
void m6800_cpu_device::WM16(u32 addr,PAIR *v) {
    write(addr,v->b.h); write((addr+1)&0xffff,v->b.l);
}
void m6800_cpu_device::reset() {
    m_cc=0xd0; m_wai_state=0; irq_line=false; nmi_pending=false;
    m_pc.d=RM16(0xfffe); cycles=0; instructions=0;
    opcode_counts.fill(0);
}
void m6800_cpu_device::enter_interrupt(u16 vector) {
    if (m_wai_state & M6800_WAI) {
        m_wai_state &= ~M6800_WAI; cycles+=4;
    } else {
        cycles+=12;
        PUSHWORD(pPC); PUSHWORD(pX); PUSHBYTE(A); PUSHBYTE(B); PUSHBYTE(CC);
    }
    SEI;
    PCD=RM16(vector);
}
void m6800_cpu_device::check_irq_lines() {
    if (nmi_pending) {
        nmi_pending=false; m_wai_state &= ~M6800_SLP; enter_interrupt(0xfffc);
    } else if (irq_line && !(CC&0x10)) {
        m_wai_state &= ~M6800_SLP; enter_interrupt(0xfff8);
    }
}
void m6800_cpu_device::fail_illegal() {
    failed=true;
    std::snprintf(error,sizeof(error),"Illegal 6800 opcode %02x at %04x (cycle %llu)",
        read(m_ppc.w.l),m_ppc.w.l,static_cast<unsigned long long>(cycles));
}
void m6800_cpu_device::execute_one() {
    if (failed) return;
    pPPC=pPC;
    u8 opcode=read(PC++);
    // Time IO at instruction completion. All instruction and IRQ totals follow
    // MAME's 6800 table; individual sub-instruction bus phases are not modeled.
    cycles+=cycles_6800[opcode];
    ++instructions; ++opcode_counts[opcode];
    (this->*m6800_insn[opcode])();
}
uint64_t m6800_cpu_device::step() {
    auto old=cycles;
    if (failed) return 0;
    check_irq_lines();
    if (m_wai_state) ++cycles;
    else execute_one();
    return cycles-old;
}
uint64_t m6800_cpu_device::run_until(uint64_t target) {
    while (cycles<target && !failed) {
        check_irq_lines();
        if (m_wai_state) {cycles=target; break;}
        execute_one();
    }
    return cycles;
}

// Stable C interface for Python ctypes and standalone extraction programs.
extern "C" {
void *j6800_create(const u8 *rom,size_t size,read_callback r,write_callback w,void *opaque) {
    return new(std::nothrow) m6800_cpu_device(rom,size,r,w,opaque);
}
void j6800_destroy(void *p) {delete static_cast<m6800_cpu_device *>(p);}
uint64_t j6800_step(void *p) {return static_cast<m6800_cpu_device *>(p)->step();}
uint64_t j6800_run_until(void *p,uint64_t target) {return static_cast<m6800_cpu_device *>(p)->run_until(target);}
uint64_t j6800_cycles(void *p) {return static_cast<m6800_cpu_device *>(p)->cycles;}
uint64_t j6800_instructions(void *p) {return static_cast<m6800_cpu_device *>(p)->instructions;}
uint64_t j6800_opcode_count(void *p,u8 op) {return static_cast<m6800_cpu_device *>(p)->opcode_counts[op];}
u16 j6800_pc(void *p) {return static_cast<m6800_cpu_device *>(p)->m_pc.w.l;}
u8 j6800_peek(void *p,u16 addr) {return static_cast<m6800_cpu_device *>(p)->memory[addr];}
void j6800_poke(void *p,u16 addr,u8 value) {static_cast<m6800_cpu_device *>(p)->memory[addr]=value;}
void j6800_irq(void *p,int asserted) {static_cast<m6800_cpu_device *>(p)->irq_line=(asserted!=0);}
void j6800_nmi(void *p) {static_cast<m6800_cpu_device *>(p)->nmi_pending=true;}
void j6800_reset(void *p) {static_cast<m6800_cpu_device *>(p)->reset();}
const char *j6800_error(void *p) {return static_cast<m6800_cpu_device *>(p)->error;}
u32 j6800_register(void *p,int index) {
    auto &c=*static_cast<m6800_cpu_device *>(p);
    switch(index) {case 0:return c.m_d.b.h; case 1:return c.m_d.b.l;
        case 2:return c.m_x.w.l; case 3:return c.m_s.w.l;
        case 4:return c.m_cc; case 5:return c.m_pc.w.l; default:return 0;}
}
void j6800_set_register(void *p,int index,u32 value) {
    auto &c=*static_cast<m6800_cpu_device *>(p);
    switch(index) {case 0:c.m_d.b.h=value;break; case 1:c.m_d.b.l=value;break;
        case 2:c.m_x.w.l=value;break; case 3:c.m_s.w.l=value;break;
        case 4:c.m_cc=value;break; case 5:c.m_pc.w.l=value;break;}
}
}
