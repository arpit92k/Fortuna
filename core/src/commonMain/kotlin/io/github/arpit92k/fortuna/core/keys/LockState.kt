package io.github.arpit92k.fortuna.core.keys

import io.github.arpit92k.fortuna.core.file.KeyDerivation
import io.github.arpit92k.fortuna.core.file.KeyDerivationAlgorithm
import io.github.arpit92k.fortuna.core.file.LockedKey
import io.github.arpit92k.fortuna.core.ports.Moment
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * What is needed before the store can be opened: the two locked copies of
 * the data key and the count of wrong PINs. It holds no financial data and
 * is not encrypted as a whole; each copy of the key is locked on its own.
 */
data class LockState(
    val pin: PinLockedKey,
    /** The copy the recovery phrase opens. The same record travels in backups. */
    val phrase: LockedKey,
    val failedAttempts: Int,
    /** When the latest wrong PIN was entered, or when the wait began again after a restart. */
    val lastFailure: Moment?,
) {
    init {
        require(failedAttempts >= 0) { "The count of wrong PINs cannot be negative" }
        require((failedAttempts == 0) == (lastFailure == null)) { "A wrong PIN is recorded with its time" }
    }
}

/**
 * The PIN's copy of the data key: encrypted with a key derived from the PIN,
 * then wrapped by the device key, so it can only be opened on this device.
 */
class PinLockedKey(
    val iterations: Int,
    val salt: ByteArray,
    val nonce: ByteArray,
    /** The 48-byte encrypted data key, wrapped by the device key. */
    val wrapped: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is PinLockedKey && other.iterations == iterations && other.salt.contentEquals(salt) &&
            other.nonce.contentEquals(nonce) && other.wrapped.contentEquals(wrapped)

    override fun hashCode(): Int =
        ((iterations * 31 + salt.contentHashCode()) * 31 + nonce.contentHashCode()) * 31 + wrapped.contentHashCode()
}

/**
 * The saved form of a [LockState]: JSON with its own format version, written
 * in the clear because it must be read before anything is unlocked.
 */
object LockStateJson {
    const val FORMAT_VERSION: Int = 1

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { explicitNulls = false }

    fun encode(state: LockState): String = json.encodeToString(LockStateV1.serializer(), state.toV1())

    /**
     * @throws UnsupportedLockStateVersionException for a version this code does not read.
     * @throws LockStateFormatException if the text is not a well-formed lock state.
     */
    fun decode(text: String): LockState {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw LockStateFormatException("The lock state is not a JSON object", e)
        }
        val version = root["formatVersion"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            ?: throw LockStateFormatException("The lock state has no format version")
        if (version != FORMAT_VERSION) throw UnsupportedLockStateVersionException(version)
        return try {
            json.decodeFromJsonElement(LockStateV1.serializer(), root).toDomain()
        } catch (e: SerializationException) {
            throw LockStateFormatException("The lock state does not match version $FORMAT_VERSION: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw LockStateFormatException("The lock state does not match version $FORMAT_VERSION: ${e.message}", e)
        }
    }
}

open class LockStateFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedLockStateVersionException(val version: Int) :
    LockStateFormatException("Lock state version $version is not supported; this app reads version ${LockStateJson.FORMAT_VERSION}")

// ---- Format version 1. Once released, these classes must never change. ----

@Serializable
private class LockStateV1(
    val formatVersion: Int,
    val pin: PinCopyV1,
    val phrase: PhraseCopyV1,
    val failedAttempts: Int,
    val lastFailure: MomentV1?,
)

@Serializable
private class PinCopyV1(val iterations: Int, val salt: String, val nonce: String, val wrapped: String)

@Serializable
private class PhraseCopyV1(val algorithm: String, val iterations: Int, val salt: String, val nonce: String, val sealedKey: String)

@Serializable
private class MomentV1(val startUp: Long, val sinceStartMillis: Long)

private const val PBKDF2_NAME = "pbkdf2-hmac-sha256"

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.base64() = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.unbase64() = Base64.decode(this)

private fun LockState.toV1() = LockStateV1(
    formatVersion = LockStateJson.FORMAT_VERSION,
    pin = PinCopyV1(pin.iterations, pin.salt.base64(), pin.nonce.base64(), pin.wrapped.base64()),
    phrase = PhraseCopyV1(
        algorithm = when (phrase.derivation.algorithm) {
            KeyDerivationAlgorithm.PBKDF2_HMAC_SHA256 -> PBKDF2_NAME
        },
        iterations = phrase.derivation.iterations,
        salt = phrase.salt.base64(),
        nonce = phrase.nonce.base64(),
        sealedKey = phrase.sealedKey.base64(),
    ),
    failedAttempts = failedAttempts,
    lastFailure = lastFailure?.let { MomentV1(it.startUp, it.sinceStartMillis) },
)

private fun LockStateV1.toDomain(): LockState {
    require(phrase.algorithm == PBKDF2_NAME) { "Unknown key derivation \"${phrase.algorithm}\"" }
    require(pin.iterations > 0) { "The PIN's rounds must be positive" }
    return LockState(
        pin = PinLockedKey(pin.iterations, pin.salt.unbase64(), pin.nonce.unbase64(), pin.wrapped.unbase64()),
        phrase = LockedKey(
            KeyDerivation(KeyDerivationAlgorithm.PBKDF2_HMAC_SHA256, phrase.iterations),
            phrase.salt.unbase64(),
            phrase.nonce.unbase64(),
            phrase.sealedKey.unbase64(),
        ),
        failedAttempts = failedAttempts,
        lastFailure = lastFailure?.let { Moment(it.startUp, it.sinceStartMillis) },
    )
}
