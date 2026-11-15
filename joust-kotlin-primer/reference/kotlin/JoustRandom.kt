package joust.reference

/**
 * Source-derived gameplay random sequence from Joust V4.
 * Source: SYSTEM.ASM RANDSYS, lines 746–754, commit
 * 87e22aab945709fa107df609c0efcb839a17c1f9.
 *
 * Keep one shared instance for the game, including lava-bubble choices.
 * Time perturbation is an explicit operation so replay can record its order
 * relative to next(). It is not automatically applied by this class.
 */
class JoustRandom(high: Int = 0, low: Int = 0) {
    var high: Int = high and 0xff
        private set
    var low: Int = low and 0xff
        private set

    data class Sample(val sevenBit: Int, val bit: Int) {
        /** Source callers using the full-byte conversion combine both outputs. */
        val fullByte: Int get() = (sevenBit shl 1) or bit
    }

    /** Original time-driven perturbation; preserve byte wrap independently. */
    fun perturb() {
        high = (high + 1) and 0xff
        low = (low - 1) and 0xff
    }

    fun next(): Sample {
        val oldHigh = high
        val oldLow = low
        val mixed = (oldLow ushr 3) xor oldLow
        high = (oldHigh ushr 1) or ((mixed and 1) shl 7)
        low = (oldLow ushr 1) or ((oldHigh and 1) shl 7)
        return Sample(mixed ushr 1, oldLow and 1)
    }
}
