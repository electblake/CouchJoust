"""Cycle-counted 6800 helper for offline extraction of original Joust sound.

The native Kotlin game consumes the resulting WAVs. This helper is not a game
runtime dependency. Build libjoust6800.so with the command in README.md.
"""
from pathlib import Path
import ctypes as C

_HERE = Path(__file__).resolve().parent
_lib = C.CDLL(str(_HERE / 'libjoust6800.so'))
_read_type = C.CFUNCTYPE(C.c_uint8, C.c_void_p, C.c_uint16)
_write_type = C.CFUNCTYPE(None, C.c_void_p, C.c_uint16, C.c_uint8, C.c_uint64)

def _signature(name, result, *arguments):
    fn = getattr(_lib, name)
    fn.restype, fn.argtypes = result, arguments
    return fn

_create = _signature('j6800_create', C.c_void_p, C.POINTER(C.c_uint8), C.c_size_t,
                     _read_type, _write_type, C.c_void_p)
_destroy = _signature('j6800_destroy', None, C.c_void_p)
_step = _signature('j6800_step', C.c_uint64, C.c_void_p)
_run = _signature('j6800_run_until', C.c_uint64, C.c_void_p, C.c_uint64)
_cycles = _signature('j6800_cycles', C.c_uint64, C.c_void_p)
_instructions = _signature('j6800_instructions', C.c_uint64, C.c_void_p)
_opcount = _signature('j6800_opcode_count', C.c_uint64, C.c_void_p, C.c_uint8)
_pc = _signature('j6800_pc', C.c_uint16, C.c_void_p)
_peek = _signature('j6800_peek', C.c_uint8, C.c_void_p, C.c_uint16)
_poke = _signature('j6800_poke', None, C.c_void_p, C.c_uint16, C.c_uint8)
_irq = _signature('j6800_irq', None, C.c_void_p, C.c_int)
_nmi = _signature('j6800_nmi', None, C.c_void_p)
_reset = _signature('j6800_reset', None, C.c_void_p)
_error = _signature('j6800_error', C.c_char_p, C.c_void_p)
_register = _signature('j6800_register', C.c_uint32, C.c_void_p, C.c_int)
_set_register = _signature('j6800_set_register', None, C.c_void_p, C.c_int, C.c_uint32)


class CPU:
    """CPU(rom, read_io, write_io).

    rom: 4096 bytes loaded at F000, or a full 65536-byte image.
    read_io(address): return byte; called only for 0400..0403.
    write_io(address, value, cycle): called only for 0400..0403.
    Mirror 8400..8403 is normalized to 0400..0403 before the callback.
    RAM 0000..00FF is internal, initially zero; unpopulated ROM is zero.
    IO write times are instruction-end timestamps, not individual bus phases.
    """
    REGISTER_IDS = {'a': 0, 'b': 1, 'x': 2, 'sp': 3, 'cc': 4, 'pc': 5}

    def __init__(self, rom, read_io=None, write_io=None):
        if isinstance(rom, (str, Path)):
            rom = Path(rom).read_bytes()
        self._exception = None
        self._closed = False
        self._buffer = (C.c_uint8 * len(rom)).from_buffer_copy(rom)
        def read_cb(_, addr):
            try:
                return 255 if read_io is None else int(read_io(addr)) & 255
            except BaseException as e:
                self._exception = e
                return 255
        def write_cb(_, addr, value, cycle):
            try:
                if write_io is not None:
                    write_io(addr, value, cycle)
            except BaseException as e:
                self._exception = e
        self._read_callback = _read_type(read_cb)
        self._write_callback = _write_type(write_cb)
        self._handle = _create(self._buffer, len(rom), self._read_callback,
                               self._write_callback, None)
        if not self._handle:
            raise MemoryError('Could not create offline 6800')
        self.check_error()

    def check_error(self):
        if self._exception is not None:
            error, self._exception = self._exception, None
            raise error
        error = _error(self._handle)
        if error:
            raise RuntimeError(error.decode())

    @property
    def cycles(self):
        return _cycles(self._handle)

    @property
    def pc(self):
        return _pc(self._handle)

    @property
    def instructions(self):
        return _instructions(self._handle)

    def step(self):
        elapsed = _step(self._handle)
        self.check_error()
        return elapsed

    def run_until(self, cycle):
        """Run whole instructions until cycles>=cycle; may overshoot a few cycles."""
        result = _run(self._handle, int(cycle))
        self.check_error()
        return result

    def run(self, cycles):
        return self.run_until(self.cycles + int(cycles))

    def irq(self, asserted=True):
        """Set level-sensitive IRQ; clear from the PIA-B read callback."""
        _irq(self._handle, bool(asserted))

    def nmi(self):
        _nmi(self._handle)

    def reset(self):
        """CPU reset retaining RAM, as a physical reset does."""
        _reset(self._handle)

    def peek(self, address):
        """Read backing memory with no IO side effects."""
        return _peek(self._handle, address)

    def poke(self, address, value):
        """Set backing memory directly for deterministic extraction/test state."""
        _poke(self._handle, address, value)

    def register(self, name):
        return _register(self._handle, self.REGISTER_IDS[name.lower()])

    def set_register(self, name, value):
        _set_register(self._handle, self.REGISTER_IDS[name.lower()], value)

    def opcode_counts(self):
        return {i: n for i in range(256) if (n := _opcount(self._handle, i))}

    def close(self):
        if not self._closed:
            _destroy(self._handle)
            self._closed = True

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

