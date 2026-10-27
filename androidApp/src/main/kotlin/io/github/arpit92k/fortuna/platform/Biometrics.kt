package io.github.arpit92k.fortuna.platform

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import io.github.arpit92k.fortuna.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.lang.ref.WeakReference
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/** How a biometric check ended. */
sealed interface BiometricResult<out T> {
    data class Done<T>(val value: T) : BiometricResult<T>

    /** The user chose the PIN instead, or closed the prompt. */
    data object Cancelled : BiometricResult<Nothing>

    /** Fingerprints or faces were added or removed, so Android voided the key; biometric unlock is now off. */
    data object Invalidated : BiometricResult<Nothing>

    /** Anything else: too many tries, no sensor, or the copy could not be read. */
    data object Failed : BiometricResult<Nothing>
}

/**
 * The Biometric gate port: biometric unlock's copy of the data key, released
 * only after a biometric check. The core never sees how; it gets the key, or
 * nothing.
 */
interface Biometrics {
    /** Whether the phone has a strong biometric set up: a fingerprint, or a face where Android counts it as strong. */
    fun available(): Boolean

    /** Whether biometric unlock is on: its copy of the key exists. */
    fun enabled(): Boolean

    /** Locks [dataKey] behind a biometric check, after one now to confirm. */
    suspend fun enable(dataKey: ByteArray): BiometricResult<Unit>

    /** Asks for a biometric check and returns the data key. */
    suspend fun unlock(): BiometricResult<ByteArray>

    /** Turns biometric unlock off: its copy and its key are deleted. */
    fun disable()

    /**
     * Checks, without asking for a fingerprint, that biometric unlock still
     * works: Android voids its key when fingerprints or faces are added or
     * removed. If it was voided, biometric unlock is turned off and
     * [turnedOffByChange] is set, so the lock screen can say why.
     */
    fun checkStillValid()

    /** Biometric unlock was turned off because fingerprints or faces changed, and the user has not yet been told. Kept across restarts. */
    val turnedOffByChange: Boolean

    /** The user has seen why: unlocked, or turned biometric unlock on again. */
    fun acknowledgeChange()
}

/**
 * [Biometrics] on the Android Keystore and BiometricPrompt.
 *
 * The data key is encrypted with AES-GCM under a Keystore key that needs a
 * strong biometric check for every use and is voided when fingerprints or
 * faces change. The encrypted copy is a small file of its own, beside the lock
 * state but not in it, so turning biometric unlock off is deleting that file.
 */
class AndroidBiometrics(context: Context) : Biometrics {
    private val app = context.applicationContext
    private val file = File(app.filesDir, FILE)
    private val changedMarker = File(app.filesDir, CHANGED_FILE)
    private var host: WeakReference<FragmentActivity>? = null

    /** The screen that shows the prompt. The activity calls this as it is created. */
    fun attach(activity: FragmentActivity) {
        host = WeakReference(activity)
    }

    override fun available(): Boolean =
        BiometricManager.from(app).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun enabled(): Boolean = file.exists()

    override suspend fun enable(dataKey: ByteArray): BiometricResult<Unit> {
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, newKey()) }
        } catch (e: GeneralSecurityException) {
            return BiometricResult.Failed
        }
        return when (val checked = prompt(cipher, R.string.biometric_enable_title)) {
            is BiometricResult.Done -> try {
                val sealed = checked.value.doFinal(dataKey)
                write(checked.value.iv, sealed)
                BiometricResult.Done(Unit)
            } catch (e: GeneralSecurityException) {
                BiometricResult.Failed
            }
            BiometricResult.Cancelled -> BiometricResult.Cancelled
            BiometricResult.Invalidated, BiometricResult.Failed -> BiometricResult.Failed
        }
    }

    override suspend fun unlock(): BiometricResult<ByteArray> {
        val (iv, sealed) = read() ?: return BiometricResult.Failed
        val cipher = try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return invalidated()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            return invalidated()
        } catch (e: GeneralSecurityException) {
            return BiometricResult.Failed
        }
        return when (val checked = prompt(cipher, R.string.biometric_unlock_title)) {
            is BiometricResult.Done -> try {
                BiometricResult.Done(checked.value.doFinal(sealed))
            } catch (e: GeneralSecurityException) {
                BiometricResult.Failed
            }
            BiometricResult.Cancelled -> BiometricResult.Cancelled
            BiometricResult.Invalidated -> invalidated()
            BiometricResult.Failed -> BiometricResult.Failed
        }
    }

    override fun disable() {
        file.delete()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    override fun checkStillValid() {
        if (!enabled()) return
        try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey
            // Initialising a cipher needs no fingerprint, but fails at once on a voided key.
            if (key == null) invalidated() else Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
        } catch (e: KeyPermanentlyInvalidatedException) {
            invalidated()
        } catch (e: GeneralSecurityException) {
            // Something else, such as the keystore being busy: tried again at the next unlock.
        }
    }

    override val turnedOffByChange: Boolean get() = changedMarker.exists()

    override fun acknowledgeChange() {
        changedMarker.delete()
    }

    /** The key was voided: biometric unlock is off, and a marker remembers why until the user has been told. */
    private fun invalidated(): BiometricResult<Nothing> {
        disable()
        runCatching { changedMarker.createNewFile() }
        return BiometricResult.Invalidated
    }

    /** Shows the prompt for [cipher]; the cipher it returns is unlocked for one use. */
    private suspend fun prompt(cipher: Cipher, title: Int): BiometricResult<Cipher> {
        val activity = host?.get() ?: return BiometricResult.Failed
        // The prompt is a fragment: showing it while the screen is not in front would crash the app.
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return BiometricResult.Cancelled
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (continuation.isActive) {
                        continuation.resume(if (unlocked != null) BiometricResult.Done(unlocked) else BiometricResult.Failed)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (continuation.isActive) continuation.resume(if (cancelled) BiometricResult.Cancelled else BiometricResult.Failed)
                }

                // A finger not recognised: the prompt says so and lets the user try again.
                override fun onAuthenticationFailed() {}
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(app), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(app.getString(title))
                .setNegativeButtonText(app.getString(R.string.biometric_use_pin))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    /** A new Keystore key for this copy, replacing any earlier one. */
    private fun newKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Adding or removing a fingerprint or face voids the key, so a new one cannot open the data.
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    /** Version 1 of the file: the version, the nonce's length, the nonce, then the encrypted key and its tag. */
    private fun write(iv: ByteArray, sealed: ByteArray) {
        val partial = File(app.filesDir, "$FILE.partial")
        partial.writeBytes(byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed)
        if (!partial.renameTo(file)) throw GeneralSecurityException("Could not save the biometric copy")
    }

    private fun read(): Pair<ByteArray, ByteArray>? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 2 || bytes[0] != FORMAT_VERSION) return null
        val ivSize = bytes[1].toInt()
        if (bytes.size <= 2 + ivSize) return null
        return bytes.copyOfRange(2, 2 + ivSize) to bytes.copyOfRange(2 + ivSize, bytes.size)
    }

    private companion object {
        const val FILE = "biometric.key"
        const val CHANGED_FILE = "biometric.changed"
        const val ALIAS = "fortuna-biometric"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
