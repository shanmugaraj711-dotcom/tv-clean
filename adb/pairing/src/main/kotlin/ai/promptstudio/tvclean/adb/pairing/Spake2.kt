package ai.promptstudio.tvclean.adb.pairing

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

// SPAKE2 as implemented by BoringSSL (crypto/curve25519/spake25519.c), which is what AOSP's
// ADB pairing (system/core/adb/pairing_auth.cpp) uses on the TV side. This is NOT the RFC 9382
// test-vector variant: BoringSSL's fixed points M/N differ from the RFC's, and BoringSSL
// derives the key material as a single SHA-512 over a length-prefixed transcript instead of
// the RFC's KDF + key-confirmation rounds. ADB pairing uses only the one-round exchange -
// the two sides swap a 32-byte message, derive 64 bytes of key material, and use that to
// encrypt the PeerInfo. There is no second (confirmation) round.
//
// BoringSSL wire facts, grounded in spake25519.c (Google, 2016, ISC licence):
//   M (encoded) = 5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e
//   N (encoded) = 10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778
//   (generated as SHA-256 of "edwards25519 point generation seed (M|N)", decoded as a point)
//   w           = SHA-512(password) mod l          (the 64-byte digest reduced mod the order)
//   x (ours)    = (SHA-512-free random 64 bytes mod l) * 8   (multiple of 8: clears the cofactor)
//   my message  = x*B + w*(M if alice else N), 32-byte RFC 8032 encoding
//   peer point  = decode(their message) - w*(N if alice else M)
//   shared      = x * peer point, encoded to 32 bytes
//   key         = SHA-512 over length-prefixed fields, in order:
//                   alice name, bob name, alice message, bob message, shared point, password hash
//                 where each field is 8-byte little-endian length || bytes, the names include
//                 their NUL terminator (16 bytes each), and the password hash is the full
//                 64-byte SHA-512(password).
//
// AOSP names (pairing_auth.cpp): client (alice) "adb pair client", server (bob) "adb pair
// server". The phone is always alice. The bob role exists so two instances can be paired
// against each other in tests; a real TV is bob.
//
// The password the TV expects is NOT the bare 6-digit pairing code: AOSP appends 64 bytes
// of TLS-exported keying material to the code before hashing (pairing_connection.cpp:
// "To ensure the connection is not stolen while we do the PAKE, append the exported key
// material from the tls connection to the password"). PairingClient builds that password.
// A pairing-code-only password would fail against a real TV - do not copy the simcountry
// shortcut that passes only the code.
//
// UNVERIFIED against a real TV: the maths above is grounded in BoringSSL source, and the
// unit tests prove alice/bob agree and the fixed points match BoringSSL's published
// coordinates, but no TV has been paired yet. The first real pairing is the true test.
enum class Spake2Role { ALICE, BOB }

class Spake2(
    private val role: Spake2Role,
    private val myName: ByteArray,
    private val theirName: ByteArray,
    password: ByteArray,
    random: SecureRandom = SecureRandom(),
) {
    companion object {
        // AOSP pairing names, NUL terminator included (16 bytes each).
        val CLIENT_NAME: ByteArray = "adb pair client\u0000".toByteArray(Charsets.US_ASCII)
        val SERVER_NAME: ByteArray = "adb pair server\u0000".toByteArray(Charsets.US_ASCII)

        // BoringSSL fixed points.
        val POINT_M_HEX = "5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"
        val POINT_N_HEX = "10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"
        // Internal: the point type is internal (Ed25519Math), so these are too.
        internal val POINT_M: Ed25519Math.Point = decodeHexPoint(POINT_M_HEX)
        internal val POINT_N: Ed25519Math.Point = decodeHexPoint(POINT_N_HEX)

        private fun decodeHexPoint(hex: String): Ed25519Math.Point {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return Ed25519Math.decode(bytes)
                ?: throw IllegalStateException("SPAKE2 fixed point $hex does not decode")
        }

        fun hexToBytes(hex: String): ByteArray =
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        /** Client-side helper: build a Spake2 in the alice role with the AOSP names. */
        fun forPairing(password: ByteArray, random: SecureRandom = SecureRandom()): Spake2 =
            Spake2(Spake2Role.ALICE, CLIENT_NAME, SERVER_NAME, password, random)
    }

    private val passwordHash: ByteArray = MessageDigest.getInstance("SHA-512").digest(password)
    private val w: BigInteger = Ed25519Math.reduce64(passwordHash)

    private val privateScalar: BigInteger = run {
        val tmp = ByteArray(64).also { random.nextBytes(it) }
        Ed25519Math.reduce64(tmp).multiply(BigInteger.valueOf(8))
    }

    private var myMsg: ByteArray? = null
    private var state: State = State.INIT

    private enum class State { INIT, MSG_GENERATED, KEY_GENERATED }

    /** Our 32-byte SPAKE2 message to send to the peer. */
    fun generateMsg(): ByteArray {
        check(state == State.INIT) { "SPAKE2 message already generated" }
        val myPoint = if (role == Spake2Role.ALICE) POINT_M else POINT_N
        val mask = Ed25519Math.scalarMult(w, myPoint)
        val base = Ed25519Math.scalarMult(privateScalar, Ed25519Math.BASE)
        val msgPoint = Ed25519Math.add(base, mask)
        val msg = Ed25519Math.encode(msgPoint)
        myMsg = msg
        state = State.MSG_GENERATED
        return msg
    }

    /** Process the peer's 32-byte message; returns up to 64 bytes of shared key material. */
    fun processMsg(theirMsg: ByteArray): ByteArray {
        check(state == State.MSG_GENERATED) { "generateMsg() must be called first" }
        require(theirMsg.size == 32) { "SPAKE2 message is 32 bytes, got ${theirMsg.size}" }
        val myMsgBytes = requireNotNull(myMsg)
        val peerStar = Ed25519Math.decode(theirMsg)
            ?: throw IllegalArgumentException("peer SPAKE2 message is not an Ed25519 point")
        val peerPoint = if (role == Spake2Role.ALICE) POINT_N else POINT_M
        val peerMask = Ed25519Math.scalarMult(w, peerPoint)
        val peer = Ed25519Math.subtract(peerStar, peerMask)
        val shared = Ed25519Math.scalarMult(privateScalar, peer)
        val sharedEncoded = Ed25519Math.encode(shared)

        val sha = MessageDigest.getInstance("SHA-512")
        // Transcript order is always alice first, then bob - regardless of our role.
        val (aliceName, bobName, aliceMsg, bobMsg) = if (role == Spake2Role.ALICE) {
            Quad(myName, theirName, myMsgBytes, theirMsg)
        } else {
            Quad(theirName, myName, theirMsg, myMsgBytes)
        }
        updateWithLengthPrefix(sha, aliceName)
        updateWithLengthPrefix(sha, bobName)
        updateWithLengthPrefix(sha, aliceMsg)
        updateWithLengthPrefix(sha, bobMsg)
        updateWithLengthPrefix(sha, sharedEncoded)
        updateWithLengthPrefix(sha, passwordHash)
        val key = sha.digest()
        state = State.KEY_GENERATED
        return key
    }

    private data class Quad(val a: ByteArray, val b: ByteArray, val c: ByteArray, val d: ByteArray)

    private fun updateWithLengthPrefix(sha: MessageDigest, data: ByteArray) {
        val len = ByteArray(8)
        var l = data.size.toLong()
        for (i in 0 until 8) {
            len[i] = (l and 0xff).toByte()
            l = l ushr 8
        }
        sha.update(len)
        sha.update(data)
    }
}
