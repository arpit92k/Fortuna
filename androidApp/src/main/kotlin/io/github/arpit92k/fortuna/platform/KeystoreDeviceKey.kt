package io.github.arpit92k.fortuna.platform

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import io.github.arpit92k.fortuna.core.ports.DeviceKey
import io.github.arpit92k.fortuna.core.ports.DeviceKeyException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Device key port on the Android Keystore: an AES-256 key that never
 * leaves the phone's secure hardware. It lives in StrongBox, the separate
 * security chip, where the phone has one, and in the trusted execution
 * environment otherwise.
 *
 * Decided: the key does not require the phone itself to be unlocked. The
 * app PIN and this key already guard the data, and on some phones such a key
 * is destroyed when the phone's own screen lock is removed or changed, which
 * would force a recovery with the phrase.
 *
 * A wrapped secret is the 12-byte nonce the Keystore chose, then the
 * ciphertext and its 16-byte tag.
 */
class KeystoreDeviceKey(private val alias: String = ALIAS) : DeviceKey {
    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        val nonce = cipher.iv
        check(nonce.size == NONCE_SIZE) { "The Keystore chose a nonce of ${nonce.size} bytes" }
        return nonce + cipher.doFinal(secret)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.size < NONCE_SIZE + TAG_SIZE) throw DeviceKeyException("Too short to be a wrapped secret")
        val key = existingKey() ?: throw DeviceKeyException("This device has no Fortuna key")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE * 8, wrapped, 0, NONCE_SIZE))
            cipher.doFinal(wrapped, NONCE_SIZE, wrapped.size - NONCE_SIZE)
        } catch (e: GeneralSecurityException) {
            // A wrong tag, or a key the system has invalidated.
            throw DeviceKeyException("The device key could not unwrap this secret", e)
        } catch (e: ProviderException) {
            throw DeviceKeyException("The Keystore failed to unwrap this secret", e)
        }
    }

    private fun existingKey(): SecretKey? = keyStore.getKey(alias, null) as SecretKey?

    @Synchronized
    private fun createKey(): SecretKey {
        existingKey()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generate(strongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                // No StrongBox on this phone: the trusted execution environment it is.
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val ALIAS = "fortuna-device-key"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
    }
}
