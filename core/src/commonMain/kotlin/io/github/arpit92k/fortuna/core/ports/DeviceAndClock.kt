package io.github.arpit92k.fortuna.core.ports

import kotlinx.datetime.LocalDate

/**
 * The Device key port: a key that never leaves this device's secure hardware.
 *
 * It wraps the PIN-locked copy of the data key, so every PIN guess has to be
 * made on this phone, where the growing delay applies. On Android it is a
 * key in the Android Keystore (step 14).
 */
interface DeviceKey {
    /** [secret] encrypted with the device's key. */
    fun wrap(secret: ByteArray): ByteArray

    /**
     * Reverses [wrap].
     *
     * @throws DeviceKeyException if [wrapped] was not made by this device's key.
     */
    fun unwrap(wrapped: ByteArray): ByteArray
}

/** The device key could not unwrap something: it was made on another device, or has been changed. */
class DeviceKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The Clock port.
 *
 * Besides today's date, it supplies a clock the user cannot change: the time
 * since the device started. That clock starts again at zero after a restart,
 * so [startUp] tells start-ups apart. On Android these are the system clock,
 * `SystemClock.elapsedRealtime` and the boot count (step 14).
 */
interface Clock {
    /** Today's calendar date where the user is. */
    fun today(): LocalDate

    /** Milliseconds since the device started. Changing the time or date does not affect it. */
    fun sinceStartMillis(): Long

    /** Which start-up of the device this is. Changes on every restart. */
    fun startUp(): Long
}

/** A point in time on the device's unchangeable clock. */
data class Moment(val startUp: Long, val sinceStartMillis: Long)

fun Clock.now(): Moment = Moment(startUp(), sinceStartMillis())
