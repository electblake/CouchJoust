package com.couchjoust

import org.junit.Assert.*
import org.junit.Test

class ProbeFrameTest {
    @Test fun invitationSecretAuthenticatesGuest() {
        val token = ByteArray(32) { it.toByte() }
        val proof = ProbeAdmission.proof(token)
        assertEquals(28, proof.size)
        ProbeAdmission.authenticate(token, proof)
    }
    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun anotherInvitationCannotAuthenticate() {
        ProbeAdmission.authenticate(ByteArray(32) { 1 }, ProbeAdmission.proof(ByteArray(32) { 2 }))
    }
    @Test fun datagramPreservesSequenceCoordinatesAndFlapCount() {
        val frame = ProbeFrame(1234567890123L, 0.125f, 0.875f, 987)
        assertEquals(20, frame.encode().size)
        assertEquals(frame, ProbeFrame.decode(frame.encode()))
    }
}
