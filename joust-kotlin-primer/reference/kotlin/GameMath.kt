package joust.reference

/**
 * Small native gameplay helpers reconstructed from Joust V4.
 * Source pin: 87e22aab945709fa107df609c0efcb839a17c1f9.
 * These functions are not a complete movement engine. State-entry ordering,
 * timers, landing, bumps and actor update order are specified in the primer.
 */
object GameMath {
    const val TICK_NS: Long = 16_640_000L
    const val POSITION_SCALE = 256
    private val flightDx = intArrayOf(-512, -256, -128, -64, 0, 64, 128, 256, 512)

    // Kotlin shr is arithmetic; this intentionally rounds negative values down.
    fun pixelPart(q8: Int): Int = q8 shr 8
    fun fractionalPart(q8: Int): Int = q8 and 255
    fun signed16(value: Int): Int = value.toShort().toInt()

    fun direction(left: Boolean, right: Boolean): Int =
        (if (right) 1 else 0) - (if (left) 1 else 0)

    // ADDFLP: timer is incremented/reset by the calling flight state machine.
    fun flapImpulseQ8(releasedPhaseTimer: Int): Int {
        require(releasedPhaseTimer in 0..255)
        return ((96 * releasedPhaseTimer) shr 8) - 96
    }

    fun horizontalIndexOnFlap(index: Int, direction: Int): Int {
        require(index in -8..8 && index % 2 == 0)
        require(direction in -1..1)
        val candidate = index + 2 * direction
        return if (candidate in -8..8) candidate else index
    }

    fun flightDxQ8(index: Int): Int {
        require(index in -8..8 && index % 2 == 0)
        return flightDx[(index + 8) / 2]
    }

    data class HorizontalStep(val pixels: Int, val remainder: Int)
    fun integrateHorizontal(remainder: Int, index: Int): HorizontalStep {
        require(remainder in 0..255)
        val sum = remainder + flightDxQ8(index)
        return HorizontalStep(sum shr 8, sum and 255)
    }

    data class BumpStep(val pixels: Int, val remaining: Int)
    fun consumeHorizontalBump(remaining: Int): BumpStep {
        val step = remaining.coerceIn(-3, 3)
        return BumpStep(step, remaining - step)
    }

    // One source wrap correction; intended for the small movement displacements
    // produced by the original rules, not arbitrary teleport destinations.
    fun wrapMovedX(x: Int): Int = when {
        x > 292 -> x - 303
        x < -10 -> x + 303
        else -> x
    }

    fun nextWaveTableEntry(entry: Int): Int {
        require(entry in 1..90)
        return if (entry == 90) 81 else entry + 1
    }

    fun nextDisplayedWave(displayed: Int): Int {
        require(displayed in 0..99)
        return (displayed + 1) % 100
    }

    // points is the combined amount for display/comparison. EGGSCR applies
    // basePoints first, then airBonusPoints if nonzero, running score/life/sound
    // effects after each award before it decrements the unresolved-rider count.
    data class EggAward(val points: Int, val newChain: Int) {
        val basePoints: Int get() = newChain * 250
        val airBonusPoints: Int get() = points - basePoints
    }
    fun collectEgg(chain: Int, beforeFirstTerrainBounce: Boolean): EggAward {
        require(chain in 0..4)
        val next = (chain + 1).coerceAtMost(4)
        return EggAward(next * 250 + if (beforeFirstTerrainBounce) 500 else 0, next)
    }

    // IWAVE signed clamp. Do not replace with an intuitive approach-to-target.
    fun adjustDifficulty(value: Int, increment: Int, endpoint: Int): Int {
        require(increment in -128..127)
        val candidate = signed16(value + increment)
        val end = signed16(endpoint)
        return if (increment >= 0) {
            if (candidate < end) candidate else end
        } else {
            if (candidate > end) candidate else end
        }
    }
}
