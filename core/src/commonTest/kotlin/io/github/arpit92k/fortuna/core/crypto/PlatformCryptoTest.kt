@file:OptIn(ExperimentalStdlibApi::class)

package io.github.arpit92k.fortuna.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PlatformCryptoTest {
    private val crypto: Crypto = PlatformCrypto

    private fun hex(text: String): ByteArray = text.hexToByteArray()

    // AES-256-GCM: test cases 13 to 16 of "The Galois/Counter Mode of Operation
    // (GCM)" by McGrew and Viega, the specification's own test vectors.
    //
    // Each key and nonce pair is used to encrypt at most once in this class,
    // as in real use. The JDK refuses to encrypt twice in a row with the same
    // pair, so the vectors that share a pair are checked by decrypting instead,
    // which proves the same thing: the published tag only verifies if the
    // implementation is right.

    private val zeroKey = ByteArray(32)
    private val zeroNonce = ByteArray(12)
    private val key = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    private val nonce = hex("cafebabefacedbaddecaf888")
    private val plaintext = hex(
        "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255",
    )
    private val ciphertext = hex(
        "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad",
    )
    private val associatedData = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")

    @Test
    fun gcmTestCase13EmptyPlaintext() {
        val sealed = hex("530f8afbc74536b9a963b4f1c4cb738b")
        assertContentEquals(ByteArray(0), crypto.decrypt(zeroKey, zeroNonce, sealed, ByteArray(0)))
    }

    @Test
    fun gcmTestCase14OneZeroBlock() {
        val sealed = crypto.encrypt(zeroKey, zeroNonce, ByteArray(16), ByteArray(0))
        assertContentEquals(hex("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919"), sealed)
        assertContentEquals(ByteArray(16), crypto.decrypt(zeroKey, zeroNonce, sealed, ByteArray(0)))
    }

    @Test
    fun gcmTestCase15FourBlocks() {
        val sealed = crypto.encrypt(key, nonce, plaintext, ByteArray(0))
        assertContentEquals(ciphertext + hex("b094dac5d93471bdec1a502270e3cc6c"), sealed)
        assertContentEquals(plaintext, crypto.decrypt(key, nonce, sealed, ByteArray(0)))
    }

    @Test
    fun gcmTestCase16WithAssociatedData() {
        val sealed = ciphertext.copyOf(60) + hex("76fc6ece0f4e1768cddf8853bb2d551b")
        assertContentEquals(plaintext.copyOf(60), crypto.decrypt(key, nonce, sealed, associatedData))
        assertFailsWith<DecryptionFailedException> { crypto.decrypt(key, nonce, sealed, ByteArray(0)) }
    }

    // From the plan: encrypt then decrypt returns the original, and tampering is rejected.

    private val message = "The data key, or a whole dataset".encodeToByteArray()
    private val header = "Fortuna file header".encodeToByteArray()

    private fun freshKey() = crypto.randomBytes(Crypto.KEY_SIZE)

    private fun freshNonce() = crypto.randomBytes(Crypto.NONCE_SIZE)

    @Test
    fun encryptThenDecryptReturnsTheOriginal() {
        val k = crypto.randomBytes(Crypto.KEY_SIZE)
        val n = crypto.randomBytes(Crypto.NONCE_SIZE)
        val sealed = crypto.encrypt(k, n, message, header)
        assertEquals(message.size + Crypto.TAG_SIZE, sealed.size)
        assertContentEquals(message, crypto.decrypt(k, n, sealed, header))
    }

    @Test
    fun aChangedByteInTheDataIsRejected() {
        val k = freshKey()
        val n = freshNonce()
        val sealed = crypto.encrypt(k, n, message, header)
        for (index in sealed.indices) {
            val tampered = sealed.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertFailsWith<DecryptionFailedException>("byte $index changed") {
                crypto.decrypt(k, n, tampered, header)
            }
        }
    }

    @Test
    fun aChangedByteInTheAssociatedDataIsRejected() {
        val k = freshKey()
        val n = freshNonce()
        val sealed = crypto.encrypt(k, n, message, header)
        val otherHeader = header.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFailsWith<DecryptionFailedException> { crypto.decrypt(k, n, sealed, otherHeader) }
        assertFailsWith<DecryptionFailedException> { crypto.decrypt(k, n, sealed, ByteArray(0)) }
    }

    @Test
    fun aWrongKeyOrNonceIsRejected() {
        val k = freshKey()
        val n = freshNonce()
        val sealed = crypto.encrypt(k, n, message, header)
        val otherKey = k.copyOf().also { it[31] = (it[31].toInt() xor 1).toByte() }
        val otherNonce = n.copyOf().also { it[11] = (it[11].toInt() xor 1).toByte() }
        assertFailsWith<DecryptionFailedException> { crypto.decrypt(otherKey, n, sealed, header) }
        assertFailsWith<DecryptionFailedException> { crypto.decrypt(k, otherNonce, sealed, header) }
    }

    @Test
    fun dataTooShortToHoldATagIsRejected() {
        assertFailsWith<DecryptionFailedException> { crypto.decrypt(key, nonce, ByteArray(15), header) }
    }

    @Test
    fun keysAndNoncesOfTheWrongSizeAreRefused() {
        assertFailsWith<IllegalArgumentException> { crypto.encrypt(ByteArray(16), nonce, message, header) }
        assertFailsWith<IllegalArgumentException> { crypto.encrypt(key, ByteArray(16), message, header) }
        assertFailsWith<IllegalArgumentException> { crypto.decrypt(ByteArray(31), nonce, ByteArray(32), header) }
    }

    // PBKDF2-HMAC-SHA256: the test vectors in RFC 7914, section 11.

    @Test
    fun pbkdf2Rfc7914OneIteration() {
        val derived = crypto.deriveKey("passwd".encodeToByteArray(), "salt".encodeToByteArray(), iterations = 1, size = 64)
        assertContentEquals(
            hex(
                "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                    "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            ),
            derived,
        )
    }

    @Test
    fun pbkdf2Rfc7914EightyThousandIterations() {
        val derived = crypto.deriveKey("Password".encodeToByteArray(), "NaCl".encodeToByteArray(), iterations = 80_000, size = 64)
        assertContentEquals(
            hex(
                "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                    "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
            ),
            derived,
        )
    }

    @Test
    fun keyDerivationRefusesSecretsThatAreNotText() {
        // The JDK takes PBKDF2 passwords as characters; the wrapper refuses bytes that are not UTF-8 everywhere alike.
        val notText = byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte())
        assertFailsWith<IllegalArgumentException> { crypto.deriveKey(notText, ByteArray(16), iterations = 1) }
    }

    @Test
    fun derivedKeysAreAesKeySizedByDefault() {
        val derived = crypto.deriveKey("123456".encodeToByteArray(), crypto.randomBytes(16), iterations = 1_000)
        assertEquals(Crypto.KEY_SIZE, derived.size)
    }

    // SHA-256: the examples in FIPS 180-2.

    @Test
    fun sha256Fips180Examples() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            crypto.sha256("abc".encodeToByteArray()).toHexString(),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            crypto.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHexString(),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            crypto.sha256(ByteArray(0)).toHexString(),
        )
    }

    @Test
    fun randomBytesHaveTheRequestedSizeAndDiffer() {
        val first = crypto.randomBytes(32)
        val second = crypto.randomBytes(32)
        assertEquals(32, first.size)
        assertFalse(first.contentEquals(second), "two 32-byte random values should never match")
        assertEquals(0, crypto.randomBytes(0).size)
    }
}
