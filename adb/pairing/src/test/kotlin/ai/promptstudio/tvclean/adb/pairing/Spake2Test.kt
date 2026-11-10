package ai.promptstudio.tvclean.adb.pairing

import java.math.BigInteger
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Validates the SPAKE2 implementation against what BoringSSL publishes and against itself.
// There is no RFC 9382 cross-check on purpose: BoringSSL's M/N differ from the RFC's, and
// its key derivation is a single SHA-512 transcript hash, not the RFC's KDF + confirmation
// round. A real TV is the final cross-check (flagged UNVERIFIED in the code).
class Spake2Test {

    @Test fun basePointEncodesToTheKnownValue() {
        val encoded = Ed25519Math.encode(Ed25519Math.BASE)
        assertEquals(
            "5866666666666666666666666666666666666666666666666666666666666666",
            Spake2.bytesToHex(encoded),
        )
    }

    @Test fun fixedPointsMatchBoringSslPublishedCoordinates() {
        // BoringSSL spake25519.c header comments publish the x/y of M and N.
        val m = Spake2.POINT_M
        assertEquals(
            BigInteger("31406539342727633121250288103050113562375374900226415211311216773867585644232"),
            m.x,
        )
        assertEquals(
            BigInteger("21177308356423958466833845032658859666296341766942662650232962324899758529114"),
            m.y,
        )
        assertEquals(Spake2.POINT_M_HEX, Spake2.bytesToHex(Ed25519Math.encode(m)))
        val n = Spake2.POINT_N
        assertEquals(
            BigInteger("49918732221787544735331783592030787422991506689877079631459872391322455579424"),
            n.x,
        )
        assertEquals(
            BigInteger("54629554431565467720832445949441049581317094546788069926228343916274969994000"),
            n.y,
        )
        assertEquals(Spake2.POINT_N_HEX, Spake2.bytesToHex(Ed25519Math.encode(n)))
    }

    @Test fun aliceAndBobWithTheSamePasswordDeriveTheSameKey() {
        val password = "123456".toByteArray(Charsets.US_ASCII)
        val alice = Spake2(Spake2Role.ALICE, Spake2.CLIENT_NAME, Spake2.SERVER_NAME, password, SecureRandom())
        val bob = Spake2(Spake2Role.BOB, Spake2.SERVER_NAME, Spake2.CLIENT_NAME, password, SecureRandom())
        val aliceMsg = alice.generateMsg()
        val bobMsg = bob.generateMsg()
        assertEquals(32, aliceMsg.size)
        assertEquals(32, bobMsg.size)
        assertNotEquals(Spake2.bytesToHex(aliceMsg), Spake2.bytesToHex(bobMsg))
        val aliceKey = alice.processMsg(bobMsg)
        val bobKey = bob.processMsg(aliceMsg)
        assertEquals(64, aliceKey.size)
        assertEquals(64, bobKey.size)
        assertEquals(Spake2.bytesToHex(aliceKey), Spake2.bytesToHex(bobKey))
    }

    @Test fun aWrongPasswordDerivesADifferentKey() {
        val alice = Spake2.forPairing("123456".toByteArray(Charsets.US_ASCII))
        val bobRight = Spake2(Spake2Role.BOB, Spake2.SERVER_NAME, Spake2.CLIENT_NAME, "123456".toByteArray(Charsets.US_ASCII))
        val bobWrong = Spake2(Spake2Role.BOB, Spake2.SERVER_NAME, Spake2.CLIENT_NAME, "654321".toByteArray(Charsets.US_ASCII))
        val aliceMsg = alice.generateMsg()
        val bobRightMsg = bobRight.generateMsg()
        val bobWrongMsg = bobWrong.generateMsg()
        val aliceKey = alice.processMsg(bobRightMsg)
        val rightKey = bobRight.processMsg(aliceMsg)
        assertEquals(Spake2.bytesToHex(aliceKey), Spake2.bytesToHex(rightKey))
        val wrongKey = bobWrong.processMsg(aliceMsg)
        assertNotEquals(Spake2.bytesToHex(aliceKey), Spake2.bytesToHex(wrongKey))
        // The wrong-password bob still "completes" SPAKE2 locally (there is no confirmation
        // round); the mismatch only surfaces when the PeerInfo fails to decrypt.
        assertEquals(64, wrongKey.size)
    }

    @Test fun thePairingPasswordIncludesTheExportedKeyMaterial() {
        // The AOSP password is code + 64 exported bytes; the two must not collide.
        val code = "123456".toByteArray(Charsets.US_ASCII)
        val exported = ByteArray(64) { 0x42 }
        val aliceA = Spake2.forPairing(code + exported)
        val bobA = Spake2(Spake2Role.BOB, Spake2.SERVER_NAME, Spake2.CLIENT_NAME, code + exported)
        val aliceAMsg = aliceA.generateMsg()
        val bobAMsg = bobA.generateMsg()
        val keyA = aliceA.processMsg(bobAMsg)
        val bobAKey = bobA.processMsg(aliceAMsg)
        assertEquals(Spake2.bytesToHex(keyA), Spake2.bytesToHex(bobAKey))
        val aliceB = Spake2.forPairing(code)
        val bobB = Spake2(Spake2Role.BOB, Spake2.SERVER_NAME, Spake2.CLIENT_NAME, code)
        val aliceBMsg = aliceB.generateMsg()
        val bobBMsg = bobB.generateMsg()
        val keyB = aliceB.processMsg(bobBMsg)
        val bobBKey = bobB.processMsg(aliceBMsg)
        assertEquals(Spake2.bytesToHex(keyB), Spake2.bytesToHex(bobBKey))
        assertNotEquals(Spake2.bytesToHex(keyA), Spake2.bytesToHex(keyB))
    }

    @Test fun processMsgRejectsANonPointAndAWrongSize() {
        val alice = Spake2.forPairing("123456".toByteArray(Charsets.US_ASCII))
        alice.generateMsg()
        try {
            alice.processMsg(ByteArray(31))
            assertTrue("expected a size check", false)
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            alice.processMsg(ByteArray(32) { 0xff.toByte() })
            assertTrue("expected a point check", false)
        } catch (e: IllegalArgumentException) {
            // expected: all-0xff does not decode to a curve point
        }
    }
}
