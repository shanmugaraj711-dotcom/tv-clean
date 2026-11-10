package ai.promptstudio.tvclean.adb.pairing

import java.math.BigInteger

// Minimal, self-contained Edwards25519 group arithmetic (affine coordinates via
// java.math.BigInteger). Used ONLY for the ADB TLS pairing SPAKE2, which needs arbitrary
// point addition / subtraction / scalar multiplication - operations Bouncy Castle's public
// Ed25519 API does not expose (it only offers sign/verify and base-point multiplication).
// Not constant-time; a pairing runs once per session, so simplicity and verifiability win.
//
// Curve (RFC 8032): twisted Edwards, a = -1, d = -121665/121666 mod p, p = 2^255 - 19.
// Points are encoded as in RFC 8032: 32-byte little-endian y with the sign of x in the
// top bit of the last byte.
internal object Ed25519Math {
    val P: BigInteger = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))
    val D: BigInteger = BigInteger.valueOf(-121665L)
        .multiply(BigInteger.valueOf(121666L).modInverse(P)).mod(P)
    // Group order l = 2^252 + 27742317777372353535851937790883648493.
    val L: BigInteger = BigInteger.TWO.pow(252)
        .add(BigInteger("27742317777372353535851937790883648493"))
    // sqrt(-1) mod p = 2^((p-1)/4) mod p (used when recovering x).
    private val SQRT_MINUS_ONE: BigInteger =
        BigInteger.TWO.modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)
    // I = sqrt(-1) as an integer constant is not needed; the recovery uses SQRT_MINUS_ONE.

    data class Point(val x: BigInteger, val y: BigInteger) {
        init {
            require(x >= BigInteger.ZERO && x < P && y >= BigInteger.ZERO && y < P)
        }
    }

    val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE)

    /** The standard Ed25519 base point, decoded from its RFC 8032 encoding. */
    val BASE: Point = decode(
        byteArrayOf(
            0x58, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66,
            0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66,
            0x66, 0x66, 0x66, 0x66,
        ),
    ) ?: throw IllegalStateException("base point does not decode")

    fun isOnCurve(p: Point): Boolean {
        val x2 = p.x.multiply(p.x).mod(P)
        val y2 = p.y.multiply(p.y).mod(P)
        val lhs = y2.subtract(x2).mod(P)
        val rhs = BigInteger.ONE.add(D.multiply(x2).multiply(y2)).mod(P)
        return lhs == rhs
    }

    /** Decode a 32-byte RFC 8032 point encoding; null if it is not a curve point. */
    fun decode(bytes: ByteArray): Point? {
        require(bytes.size == 32) { "Ed25519 point encoding is 32 bytes" }
        val sign = (bytes[31].toInt() and 0x80) != 0
        val yBytes = bytes.copyOf()
        yBytes[31] = (yBytes[31].toInt() and 0x7f).toByte()
        val y = BigInteger(1, yBytes.reversedArray())
        if (y >= P) return null
        val y2 = y.multiply(y).mod(P)
        // x^2 = (y^2 - 1) / (d*y^2 + 1)
        val xx = y2.subtract(BigInteger.ONE).mod(P)
            .multiply(BigInteger.ONE.add(D.multiply(y2)).mod(P).modInverse(P)).mod(P)
        // p = 5 mod 8, so x = xx^((p+3)/8); if that fails, multiply by sqrt(-1).
        var x = xx.modPow(P.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), P)
        if (x.multiply(x).mod(P) != xx) x = x.multiply(SQRT_MINUS_ONE).mod(P)
        if (x.multiply(x).mod(P) != xx) return null
        if (x.testBit(0) != sign) x = P.subtract(x)
        val point = Point(x, y)
        return if (isOnCurve(point)) point else null
    }

    /** Encode a point as a 32-byte RFC 8032 encoding. */
    fun encode(p: Point): ByteArray {
        require(isOnCurve(p)) { "not an Ed25519 point" }
        val le = yLittleEndian(p.y)
        if (p.x.testBit(0)) le[31] = (le[31].toInt() or 0x80).toByte()
        return le
    }

    private fun yLittleEndian(y: BigInteger): ByteArray {
        val be = y.toByteArray() // big-endian, may have a leading zero byte
        val out = ByteArray(32)
        val src = if (be.size > 32) be.copyOfRange(be.size - 32, be.size) else be
        for (i in src.indices) out[i] = src[src.size - 1 - i]
        return out
    }

    fun add(a: Point, b: Point): Point {
        val x1 = a.x; val y1 = a.y; val x2 = b.x; val y2 = b.y
        val dxy = D.multiply(x1).multiply(x2).multiply(y1).multiply(y2).mod(P)
        val x3 = (x1.multiply(y2).add(x2.multiply(y1)))
            .multiply(BigInteger.ONE.add(dxy).modInverse(P)).mod(P)
        val y3 = (y1.multiply(y2).add(x1.multiply(x2)))
            .multiply(BigInteger.ONE.subtract(dxy).modInverse(P)).mod(P)
        return Point(x3, y3)
    }

    fun negate(p: Point): Point = Point(P.subtract(p.x).mod(P), p.y)

    fun subtract(a: Point, b: Point): Point = add(a, negate(b))

    /** Scalar multiplication by double-and-add. The scalar is reduced mod l first. */
    fun scalarMult(scalar: BigInteger, p: Point): Point {
        var k = scalar.mod(L)
        var result = IDENTITY
        var addend = p
        while (k > BigInteger.ZERO) {
            if (k.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            k = k.shiftRight(1)
        }
        return result
    }

    /** Reduce a 64-byte little-endian integer mod l (BoringSSL x25519_sc_reduce). */
    fun reduce64(input: ByteArray): BigInteger {
        require(input.size == 64) { "sc_reduce input is 64 bytes" }
        return BigInteger(1, input.reversedArray()).mod(L)
    }

    /** Encode a scalar (already < 8*l) as 32-byte little-endian. */
    fun scalarToBytes(scalar: BigInteger): ByteArray {
        require(scalar >= BigInteger.ZERO && scalar.bitLength() <= 256)
        val be = scalar.toByteArray()
        val out = ByteArray(32)
        val src = if (be.size > 32) be.copyOfRange(be.size - 32, be.size) else be
        for (i in src.indices) out[i] = src[src.size - 1 - i]
        return out
    }
}
