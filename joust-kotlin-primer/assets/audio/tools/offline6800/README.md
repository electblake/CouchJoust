# Offline original-sound extraction helper

This helper executes the original 4 KB sound program to produce timestamped DAC
values for WAV extraction. It is an asset preparation tool only. The Kotlin game
uses the decoded sounds and does not require this helper at runtime.

## Build

```sh
g++ -std=c++17 -O3 -fPIC -shared -Wall -Wextra \
  -o libjoust6800.so m6800_offline.cpp
```

The generated C++ is self-contained and needs only a C++17 compiler. A full
MAME source checkout is not required to build or use the included helper.

## Python interface

```python
from m6800 import CPU

def read_io(address):
    # Return the PIA register value. Reading command port 0x402 must clear IRQ.
    return 0xff

def write_io(address, value, cycles):
    # PIA port A is a DAC only when PIA control A bit 2 enables the data register.
    pass

cpu = CPU('video_sound_rom_4_std_780.ic12', read_io, write_io)
cpu.run_until(1000)
cpu.irq(True)
cpu.run(894886)
```

`CPU.cycles`, `CPU.pc`, `CPU.instructions`, `CPU.step()`, `CPU.run_until(cycle)`,
`CPU.run(cycles)`, `CPU.irq(asserted)`, `CPU.nmi()`, `CPU.peek(address)`,
`CPU.poke(address,value)`, `CPU.register(name)`, and `CPU.opcode_counts()` expose
state for extraction and verification. `run_until` finishes the current
instruction and may pass the requested boundary. Schedule subsequent commands
using the resulting absolute cycle count.

RAM `0000..00FF` is internal and starts at zero. A 4 KB ROM image is loaded at
`F000..FFFF`; unpopulated ROM at `B000..EFFF` starts at zero. IO reads/writes are
normalized to `0400..0403`, including the `8400` mirror. Other unmapped reads are
`FF`; writes are ignored. CPU reset retains RAM; construct a new CPU for a cold
isolated sound extraction. ROM writes are ignored except explicit debugger
`poke` operations.

The instruction semantics, original 6800 CPX flags, cycle table, interrupt stack,
12-cycle IRQ entry, WAI wakeup, and CLI/TAP deferral follow MAME. Illegal opcodes
raise an error instead of silently proceeding. IO writes are timestamped at
instruction completion. Individual within-instruction bus phases are not
modeled. This preserves exact instruction-loop durations but leaves a residual
within-instruction timestamp uncertainty for the DAC edges; this limitation
must remain documented with the produced assets. Analog board response and
speaker/acoustic coloration are outside this CPU helper.

Reference: MAME commit `b7163a8d5ef30a8f0e72e4808ada8f904367311f`, files
`src/devices/cpu/m6800/m6800.cpp` and `6800ops.hxx` (BSD 3-Clause). Their
instruction bodies and tables are included in the generated C++, retaining
their original behavior. See `LICENSE-MAME.txt`.

