package io.github.arpit92k.fortuna.core.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * [Crypto] backed by each platform's own cryptography: the JDK provider on
 * Android and the JVM, OpenSSL 3 on Linux, and Apple's libraries on iOS later.
 *
 * This is the only file in the core that uses cryptography-kotlin.
 */
object PlatformCrypto : Crypto {
    private val provider get() = CryptographyProvider.Default

    override fun randomBytes(size: Int): ByteArray {
        require(size >= 0) { "Size must not be negative: $size" }
        return CryptographyRandom.nextBytes(size)
    }

    // The nonce is chosen by the caller because the file format stores it.
    // The library marks that as delicate: reusing a nonce with a key breaks GCM.
    @OptIn(DelicateCryptographyApi::class)
    override fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        checkKeyAndNonce(key, nonce)
        return try {
            gcmCipher(key).encryptWithIvBlocking(nonce, plaintext, associatedData)
        } catch (e: Exception) {
            // The JDK, for one, refuses to encrypt twice in a row with the same key and nonce.
            throw IllegalStateException("The platform refused to encrypt; was a nonce reused with this key?", e)
        }
    }

    @OptIn(DelicateCryptographyApi::class)
    override fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        checkKeyAndNonce(key, nonce)
        if (ciphertext.size < Crypto.TAG_SIZE) throw DecryptionFailedException()
        return try {
            gcmCipher(key).decryptWithIvBlocking(nonce, ciphertext, associatedData)
        } catch (e: Exception) {
            // Each platform reports a bad tag with its own exception type.
            throw DecryptionFailedException()
        }
    }

    override fun deriveKey(secret: ByteArray, salt: ByteArray, iterations: Int, size: Int): ByteArray {
        require(iterations > 0) { "Iterations must be positive: $iterations" }
        require(size > 0) { "Size must be positive: $size" }
        try {
            secret.decodeToString(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("A PBKDF2 secret must be UTF-8 text; encode binary secrets first", e)
        }
        return provider.get(PBKDF2)
            .secretDerivation(digest = SHA256, iterations = iterations, outputSize = size.bytes, salt = salt)
            .deriveSecretToByteArrayBlocking(secret)
    }

    override fun sha256(data: ByteArray): ByteArray = provider.get(SHA256).hasher().hashBlocking(data)

    private fun gcmCipher(key: ByteArray) =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key).cipher()

    private fun checkKeyAndNonce(key: ByteArray, nonce: ByteArray) {
        require(key.size == Crypto.KEY_SIZE) { "An AES-256 key is ${Crypto.KEY_SIZE} bytes, not ${key.size}" }
        require(nonce.size == Crypto.NONCE_SIZE) { "A GCM nonce is ${Crypto.NONCE_SIZE} bytes, not ${nonce.size}" }
    }
}
