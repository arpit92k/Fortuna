package io.github.arpit92k.fortuna.core.crypto

/**
 * The cryptography Fortuna uses, behind a small interface of its own.
 *
 * Nothing here is written by hand: [PlatformCrypto] passes each call to the
 * platform's own audited implementation through cryptography-kotlin, which
 * nothing else in the core may import. Keeping the library behind this
 * interface means it can be replaced without touching the rest of the core,
 * and tests can stand in for it where real cryptography would be slow.
 *
 * All sizes are in bytes.
 */
interface Crypto {
    /** [size] bytes from a cryptographically secure random source. */
    fun randomBytes(size: Int): ByteArray

    /**
     * Encrypts [plaintext] with AES-256-GCM and returns the ciphertext with
     * its 16-byte authentication tag appended.
     *
     * [associatedData] is authenticated but not encrypted: decryption fails
     * unless the same bytes are given again. [nonce] must be 12 bytes and must
     * never be used twice with the same key; take it from [randomBytes].
     *
     * @throws IllegalStateException if the platform refuses, as the JDK does
     *   when the previous encryption used the same key and nonce.
     */
    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /**
     * Reverses [encrypt].
     *
     * @throws DecryptionFailedException if the key, nonce or associated data
     *   is wrong, or any byte of [ciphertext] has changed.
     */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray

    /**
     * A key of [size] bytes derived from [secret] with PBKDF2-HMAC-SHA256.
     *
     * [secret] must be UTF-8 text, as a PIN or a passphrase is: the JDK takes
     * PBKDF2 passwords as characters, so arbitrary bytes are refused on every
     * platform alike with IllegalArgumentException. Encode binary secrets as text first.
     */
    fun deriveKey(secret: ByteArray, salt: ByteArray, iterations: Int, size: Int = KEY_SIZE): ByteArray

    /** The 32-byte SHA-256 hash of [data]. */
    fun sha256(data: ByteArray): ByteArray

    companion object {
        /** The size of an AES-256 key. */
        const val KEY_SIZE: Int = 32

        /** The size of an AES-GCM nonce. */
        const val NONCE_SIZE: Int = 12

        /** The size of the authentication tag [encrypt] appends. */
        const val TAG_SIZE: Int = 16
    }
}

/**
 * Thrown when decryption fails. The cause is deliberately not distinguished:
 * a wrong key and tampered data must look the same to whoever is trying.
 */
class DecryptionFailedException : Exception("Decryption failed: wrong key, or the data has been changed")
