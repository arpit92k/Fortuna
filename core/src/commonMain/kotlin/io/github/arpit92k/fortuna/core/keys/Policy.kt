package io.github.arpit92k.fortuna.core.keys

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.ports.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

// The numbers behind the PIN and the recovery phrase, decided for the first version.

/** A PIN is digits only, at least this many. */
const val MIN_PIN_LENGTH: Int = 6

/** A sanity limit, far above any PIN anyone types. */
const val MAX_PIN_LENGTH: Int = 64

/** The recovery phrase's PBKDF2 rounds. Fixed, so a backup opens equally on any device. */
const val PHRASE_ITERATIONS: Int = 100_000

/** The PIN's PBKDF2 rounds never fall below this, however slow the device. */
const val MIN_PIN_ITERATIONS: Int = 100_000

/** And never rise above this, however fast it seems. */
const val MAX_PIN_ITERATIONS: Int = 10_000_000

/** How long deriving the PIN's key should take on this device. */
val PIN_DERIVATION_TARGET: Duration = 250.milliseconds

/** Why a PIN is not acceptable. */
enum class PinProblem { TOO_SHORT, TOO_LONG, NOT_DIGITS }

/** The problem with [pin], or null if it is acceptable. */
fun pinProblem(pin: String): PinProblem? = when {
    !pin.all { it in '0'..'9' } -> PinProblem.NOT_DIGITS
    pin.length < MIN_PIN_LENGTH -> PinProblem.TOO_SHORT
    pin.length > MAX_PIN_LENGTH -> PinProblem.TOO_LONG
    else -> null
}

/**
 * How long to wait before the next attempt after [failedAttempts] wrong PINs
 * in a row: the first three attempts are free, then 1, 5, 15 and 60 minutes,
 * then 24 hours for every attempt after that. The data is never erased.
 */
fun waitAfter(failedAttempts: Int): Duration = when {
    failedAttempts < 3 -> Duration.ZERO
    failedAttempts == 3 -> 1.minutes
    failedAttempts == 4 -> 5.minutes
    failedAttempts == 5 -> 15.minutes
    failedAttempts == 6 -> 1.hours
    else -> 24.hours
}

/**
 * The PIN's PBKDF2 rounds for this device: as many as take about
 * [PIN_DERIVATION_TARGET], measured by running a trial derivation, and never
 * fewer than [MIN_PIN_ITERATIONS]. Measured again whenever a PIN is set.
 */
fun calibratePinIterations(crypto: Crypto, clock: Clock, trialIterations: Int = 50_000): Int {
    val start = clock.sinceStartMillis()
    crypto.deriveKey("Fortuna calibration".encodeToByteArray(), ByteArray(16), trialIterations)
    val elapsed = (clock.sinceStartMillis() - start).coerceAtLeast(1)
    val scaled = trialIterations.toLong() * PIN_DERIVATION_TARGET.inWholeMilliseconds / elapsed
    val rounded = (scaled + 500) / 1_000 * 1_000
    return rounded.coerceIn(MIN_PIN_ITERATIONS.toLong(), MAX_PIN_ITERATIONS.toLong()).toInt()
}
