package com.couchjoust

import com.couchjoust.core.Game
import computer.iroh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import uno.lux.wormhole.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.ArrayDeque

/** Host-ordered deterministic simulation. Every simulation tick carries both players' inputs. */
class NetworkMatch(private val sound: (String, Boolean) -> Unit, private val changed: () -> Unit) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val writes = Mutex()
    private var endpoint: Endpoint? = null
    private var connection: Connection? = null
    private var stream: BiStream? = null
    private var leaving = false
    private var closing = false
    private val inputs = arrayOf(ArrayDeque<Int>(), ArrayDeque<Int>())
    private val held = IntArray(2)
    private var acknowledged = 0
    var isHost = false; private set
    var state = "SETUP"; private set
    var code = ""; private set
    var status = "Host a match or enter the other TV's code"; private set
    val ready = BooleanArray(2)
    var game = Game(sound); private set
    var matches = 0; private set
    internal var tickDelayMs = 16L
    internal var onTick: ((Game) -> Unit)? = null
    val localPlayer get() = if (isHost) 0 else 1
    private val alpn = "couchjoust/match/1".toByteArray()
    private fun wormhole() = Wormhole(WormholeConfig(appId = "com.couchjoust/match/1"))

    fun host() = scope.launch {
        isHost = true; state = "PAIRING"; status = "Creating match code"; changed()
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val ep = Endpoint.bind(EndpointOptions(preset = presetN0(), alpns = listOf(alpn)))
        endpoint = ep; ep.online()
        val invitation = JSONObject().put("ticket", EndpointTicket.fromAddr(ep.addr()).toString())
            .put("token", token.joinToString("") { "%02x".format(it) }).toString()
        wormhole().sendText(invitation).collect { event ->
            when (event) {
                is SendEvent.CodeAllocated -> { code = event.code; status = "Enter this code on the other TV"; changed() }
                SendEvent.Completed -> { status = "Connecting player 2"; changed() }
            }
        }
        val conn = ep.acceptNext()!!.accept().connect()
        ep.setAlpns(emptyList())
        val bi = conn.acceptBi()
        ProbeAdmission.authenticate(token, bi.recv().readExact(28u))
        connected(conn, bi)
    }

    fun join(phrase: String) = scope.launch {
        state = "PAIRING"; status = "Receiving match invitation"; changed()
        val text = wormhole().receive(phrase.trim().lowercase()).first { it is ReceiveEvent.TextReceived } as ReceiveEvent.TextReceived
        val invitation = JSONObject(text.text)
        val token = invitation.getString("token").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ep = Endpoint.bind(EndpointOptions(preset = presetN0())); endpoint = ep
        val conn = ep.connect(EndpointTicket.fromString(invitation.getString("ticket")).endpointAddr(), alpn)
        val bi = conn.openBi(); bi.send().writeAll(ProbeAdmission.proof(token))
        connected(conn, bi)
    }

    private fun connected(conn: Connection, bi: BiStream) {
        connection = conn; stream = bi; state = "LOBBY"; code = ""
        status = "Connected — you are player ${localPlayer + 1}"; changed()
        scope.launch {
            while (isActive) {
                val bytes = ByteBuffer.wrap(bi.recv().readExact(16u))
                val type = bytes.int; val a = bytes.int; val b = bytes.int; val c = bytes.int
                when (type) {
                    LEAVE -> {
                        closing = true; state = "LEFT"; status = "Other TV left the match"; changed()
                        send(LEFT)
                        if (!leaving) { conn.closed(); epShutdown() }
                    }
                    LEFT -> { conn.close(0, "Match ended".toByteArray()); epShutdown() }
                    INPUT -> if (isHost && state == "PLAY") inputs[1].add(a)
                    ACK -> if (isHost) acknowledged = a
                    PING -> send(PONG)
                    PONG -> Unit
                    else -> if (isHost) {
                        when (type) {
                            READY -> if (state == "LOBBY" || state == "RESULTS") publish(READY, 1, a)
                            PAUSE -> if (state == "PLAY") publish(PAUSE, 1)
                            RESUME -> if (state == "PAUSED") publish(RESUME)
                        }
                    } else {
                        apply(type, a, b, c)
                        if (type == TICK) send(ACK, c)
                    }
                }
            }
        }
        scope.launch {
            while (isActive && !closing) {
                send(PING)
                delay(5000)
            }
        }
        if (isHost) scope.launch {
            while (isActive) {
                writes.withLock {
                    // Bounded in-flight ticks prevent an unbounded stream backlog on a slow peer.
                    if (state == "PLAY" && game.tick - acknowledged < 12) {
                        for (i in 0..1) if (inputs[i].isNotEmpty()) held[i] = inputs[i].removeFirst()
                        val tick = game.tick.toInt() + 1
                        val first = held[0]; val second = held[1]
                        apply(TICK, first, second, tick)
                        write(TICK, first, second, tick)
                    }
                }
                delay(tickDelayMs)
            }
        }
    }

    private suspend fun write(type: Int, a: Int = 0, b: Int = 0, c: Int = 0) {
        stream!!.send().writeAll(ByteBuffer.allocate(16).putInt(type).putInt(a).putInt(b).putInt(c).array())
    }
    private suspend fun send(type: Int, a: Int = 0) = writes.withLock { write(type, a) }
    private suspend fun publish(type: Int, a: Int = 0, b: Int = 0) = writes.withLock {
        apply(type, a, b, 0); write(type, a, b)
    }
    private fun clearInputs() { inputs.forEach { it.clear() }; held.fill(0); game.controls.forEach { it.clearOnPauseOrDisconnect() } }
    private fun apply(type: Int, a: Int, b: Int, c: Int) {
        when (type) {
            READY -> ready[a] = b != 0
            START -> {
                game = Game(sound); clearInputs(); game.start(2); matches++; acknowledged = 0
                ready.fill(false); state = "PLAY"; status = "Player 1: host TV   Player 2: joining TV"
            }
            TICK -> {
                MatchInput.apply(game, a, b); game.step(); onTick?.invoke(game)
                if (game.gameOver && game.overTimer >= 88) {
                    state = "RESULTS"; ready.fill(false); clearInputs()
                    status = "Final scores — P1 ${game.players[0].score}   P2 ${game.players[1].score}"
                }
            }
            PAUSE -> { state = "PAUSED"; game.paused = true; clearInputs(); status = "Paused by player ${a + 1}" }
            RESUME -> { clearInputs(); game.paused = false; state = "PLAY"; status = "Player 1: host TV   Player 2: joining TV" }
        }
        changed()
    }
    fun setReady() = scope.launch {
        if (state == "LOBBY" || state == "RESULTS") {
            val value = if (ready[localPlayer]) 0 else 1
            if (isHost) publish(READY, 0, value) else send(READY, value)
        }
    }
    fun start() = scope.launch { if (isHost && ready.all { it } && state in listOf("LOBBY", "RESULTS")) publish(START) }
    fun input(bits: Int) {
        if (state == "PLAY") {
            if (isHost) inputs[0].add(bits) else scope.launch { send(INPUT, bits) }
        }
    }
    fun pause() = scope.launch { if (state == "PLAY") { if (isHost) publish(PAUSE, 0) else send(PAUSE) } }
    fun resume() = scope.launch { if (state == "PAUSED") { if (isHost) publish(RESUME) else send(RESUME) } }
    fun close() {
        if (closing) return
        closing = true; leaving = true; state = "LEFT"; status = "You left the match"; changed()
        if (connection != null) scope.launch { send(LEAVE) }
        else { scope.cancel(); endpoint?.let { ep -> CoroutineScope(Dispatchers.IO).launch { ep.shutdown(); ep.close() } } }
    }
    private suspend fun epShutdown() { endpoint!!.shutdown(); endpoint!!.close(); scope.cancel() }
    companion object {
        private const val READY = 1; private const val START = 2; private const val INPUT = 3
        private const val TICK = 4; private const val ACK = 5; private const val PAUSE = 6
        private const val RESUME = 7; private const val LEAVE = 8; private const val LEFT = 9
        private const val PING = 10; private const val PONG = 11
    }
}

object MatchInput {
    fun apply(game: Game, first: Int, second: Int) {
        intArrayOf(first, second).forEachIndexed { i, bits ->
            game.controls[i].leftKey = bits and 1 != 0
            game.controls[i].rightKey = bits and 2 != 0
            game.controls[i].flapKey = bits and 4 != 0
        }
    }
}
