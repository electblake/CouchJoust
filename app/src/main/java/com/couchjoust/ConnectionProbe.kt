package com.couchjoust

import computer.iroh.Connection
import computer.iroh.BiStream
import computer.iroh.SendStream
import computer.iroh.Endpoint
import computer.iroh.EndpointOptions
import computer.iroh.EndpointTicket
import computer.iroh.presetN0
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uno.lux.wormhole.ReceiveEvent
import uno.lux.wormhole.SendEvent
import uno.lux.wormhole.Wormhole
import uno.lux.wormhole.WormholeConfig
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Connection experiment only: no Joust simulation or score traffic. Owned by the main thread. */
class ConnectionProbe(private val changed: () -> Unit) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var endpoint: Endpoint? = null
    private var connection: Connection? = null
    private var control: SendStream? = null
    private val writeLock = Mutex()
    private val traffic = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
    private var closing = false
    private var leaveRequested = false
    var echoes = 0L
        private set
    var status = "Choose Host or Join on each TV"
        private set
    var code = ""
        private set
    var paths = ""
        private set
    var pingMs = 0L
        private set
    var received = 0L
        private set
    var sent = 0L
        private set
    var remoteSequence = -1L
        private set
    var localX = 0.5f
    var localY = 0.5f
    var localFlaps = 0
    var remoteX = 0.5f
        private set
    var remoteY = 0.5f
        private set
    var remoteFlaps = 0
        private set
    var connected = false
        private set

    private fun wormhole() = Wormhole(WormholeConfig(appId = "com.couchjoust/connection-probe/1"))

    fun host() = scope.launch {
        status = "Opening encrypted endpoint"; changed()
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val alpn = "couchjoust/probe/1".toByteArray()
        val ep = Endpoint.bind(EndpointOptions(preset = presetN0(), alpns = listOf(alpn)))
        endpoint = ep
        ep.online()
        val invitation = JSONObject().put("ticket", EndpointTicket.fromAddr(ep.addr()).toString())
            .put("token", token.joinToString("") { "%02x".format(it) }).toString()
        status = "Allocating pairing code"; changed()
        wormhole().sendText(invitation).collect { event ->
            when (event) {
                is SendEvent.CodeAllocated -> { code = event.code; status = "Enter this code on the other TV"; changed() }
                SendEvent.Completed -> { status = "Invitation delivered; connecting"; changed() }
            }
        }
        val conn = ep.acceptNext()!!.accept().connect()
        // Only one guest is admitted for this endpoint's lifetime.
        ep.setAlpns(emptyList())
        val bi = conn.acceptBi()
        ProbeAdmission.authenticate(token, bi.recv().readExact(28u))
        startConnection(conn, bi)
    }

    fun join(phrase: String) = scope.launch {
        status = "Receiving encrypted invitation"; changed()
        val receivedText = wormhole().receive(phrase.trim().lowercase())
            .first { it is ReceiveEvent.TextReceived } as ReceiveEvent.TextReceived
        val invitation = JSONObject(receivedText.text)
        val token = invitation.getString("token").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ep = Endpoint.bind(EndpointOptions(preset = presetN0()))
        endpoint = ep
        status = "Connecting to host"; changed()
        val conn = ep.connect(EndpointTicket.fromString(invitation.getString("ticket")).endpointAddr(),
            "couchjoust/probe/1".toByteArray())
        val bi = conn.openBi()
        // Sending first also announces the bidirectional stream to the host.
        bi.send().writeAll(ProbeAdmission.proof(token))
        startConnection(conn, bi)
    }

    private suspend fun sendControl(value: Long) = writeLock.withLock {
        control!!.writeAll(ByteBuffer.allocate(8).putLong(value).array())
    }

    private fun startConnection(conn: Connection, bi: BiStream) {
        connection = conn
        control = bi.send()
        startDatagrams(conn)
        scope.launch {
            val recv = bi.recv()
            while (isActive) {
                when (val value = ByteBuffer.wrap(recv.readExact(8u)).long) {
                    0L -> {
                        closing = true
                        traffic.coroutineContext[Job]!!.cancelAndJoin()
                        sendControl(Long.MIN_VALUE)
                        connected = false; status = "Other TV left the connection test"; changed()
                        if (!leaveRequested) {
                            conn.closed()
                            endpoint!!.shutdown()
                            scope.cancel()
                        }
                    }
                    Long.MIN_VALUE -> {
                        connected = false; status = "Connection test ended"; changed()
                        conn.close(0, "Left connection test".toByteArray())
                        endpoint!!.shutdown()
                        scope.cancel()
                    }
                    else -> if (value > 0) sendControl(-value) else {
                        pingMs = (System.nanoTime() + value) / 1_000_000
                        echoes++; changed()
                    }
                }
            }
        }
        traffic.launch {
            while (isActive) { sendControl(System.nanoTime()); delay(1000) }
        }
    }

    private fun startDatagrams(conn: Connection) {
        connected = true; code = ""; status = "Connected — live connection test"; changed()
        traffic.launch {
            while (isActive) {
                conn.sendDatagram(ProbeFrame(sent, localX, localY, localFlaps).encode())
                sent++
                delay(16)
            }
        }
        traffic.launch {
            while (isActive) {
                val frame = ProbeFrame.decode(conn.readDatagram())
                received++
                // Datagram sequencing is normal protocol behavior, not transport recovery.
                if (frame.sequence > remoteSequence) {
                    remoteSequence = frame.sequence
                    remoteX = frame.x; remoteY = frame.y; remoteFlaps = frame.flaps
                }
                changed()
            }
        }
        traffic.launch {
            while (isActive) {
                paths = conn.paths().joinToString(" | ") {
                    "${if (it.isSelected) "* " else ""}${if (it.isRelay) "Relay" else "Direct"}: ${it.rttMs} ms"
                }
                changed()
                delay(1000)
            }
        }
    }

    fun close() {
        if (closing) return
        closing = true
        if (connection != null) {
            leaveRequested = true
            scope.launch {
                traffic.coroutineContext[Job]!!.cancelAndJoin()
                sendControl(0)
            }
        } else {
            scope.cancel()
            endpoint?.let { ep -> CoroutineScope(Dispatchers.IO).launch { ep.shutdown(); ep.close() } }
        }
    }
}

/** Authenticate possession of the per-session secret inside the encrypted QUIC stream. */
object ProbeAdmission {
    fun proof(token: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(token, "AES"), GCMParameterSpec(128, nonce))
        return nonce + cipher.doFinal(byteArrayOf())
    }
    fun authenticate(token: ByteArray, proof: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(token, "AES"), GCMParameterSpec(128, proof.copyOfRange(0, 12)))
        cipher.doFinal(proof.copyOfRange(12, 28))
    }
}

/** Fixed 20-byte datagram, comfortably below QUIC's datagram payload limit. */
data class ProbeFrame(val sequence: Long, val x: Float, val y: Float, val flaps: Int) {
    fun encode(): ByteArray = ByteBuffer.allocate(20).putLong(sequence).putFloat(x).putFloat(y).putInt(flaps).array()
    companion object {
        fun decode(bytes: ByteArray): ProbeFrame = ByteBuffer.wrap(bytes).let {
            ProbeFrame(it.long, it.float, it.float, it.int)
        }
    }
}
