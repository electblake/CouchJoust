package com.couchjoust

import com.couchjoust.core.Game
import org.junit.Assert.*
import org.junit.Test

class NetworkSimulationTest {
    @Test fun orderedInputsProduceIdenticalWorldsDespiteDifferentDeliveryBatches() {
        val host = Game(); val guest = Game(); host.start(2); guest.start(2)
        val commands = (0 until 15000).map { tick ->
            (if (tick % 90 < 45) 1 else 2) or (if (tick % 8 < 4) 4 else 0) to
                ((if (tick % 110 < 55) 2 else 1) or (if (tick % 10 < 5) 4 else 0))
        }
        val states = commands.map { (a, b) -> MatchInput.apply(host, a, b); host.step(); host.snapshot() }
        var tick = 0
        commands.chunked(12).forEach { batch -> batch.forEach { (a, b) ->
            MatchInput.apply(guest, a, b); guest.step(); assertEquals(states[tick++], guest.snapshot())
        } }
        assertEquals(host.players.map { it.score }, guest.players.map { it.score })
    }
}
