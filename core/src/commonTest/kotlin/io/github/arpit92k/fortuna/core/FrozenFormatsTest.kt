@file:OptIn(ExperimentalStdlibApi::class)

package io.github.arpit92k.fortuna.core

import io.github.arpit92k.fortuna.core.backup.BackupFixtures
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.domain.FormatFixtures
import io.github.arpit92k.fortuna.core.file.EncryptedFile
import io.github.arpit92k.fortuna.core.keys.LockStateFixtures
import io.github.arpit92k.fortuna.core.keys.LockStateJson
import io.github.arpit92k.fortuna.core.ports.Moment
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The saved formats were frozen as version 1 for the first release (plan
 * step 25). Phones keep these files for years, so every later version of the
 * app must read them exactly as written.
 *
 * Each fixture is locked by its SHA-256. If one of these fails, a fixture was
 * edited: put it back. A change to a format needs a new version number and a
 * new fixture beside the old one, never a changed old one.
 */
class FrozenFormatsTest {
    private fun sha256(bytes: ByteArray) = PlatformCrypto.sha256(bytes).toHexString()

    @Test
    fun theFormatsAreStillVersionOne() {
        assertEquals(1, EncryptedFile.FORMAT_VERSION, "the encrypted file and backup format")
        assertEquals(1, LockStateJson.FORMAT_VERSION, "the lock state format")
    }

    @Test
    fun theDatasetFixtureIsUnchanged() {
        assertEquals(
            "50b28a3034794f7d72e9343d94765a326c979f644af55473233095a60cb3e0d1",
            sha256(FormatFixtures.DATASET_V1.encodeToByteArray()),
        )
    }

    @Test
    fun theBackupFixtureIsUnchanged() {
        assertEquals("1daa1e5652d636d5dbc32a7ec5734c946c62a16681e9270971c0175b7fa6321c", sha256(BackupFixtures.BACKUP_V1))
    }

    @Test
    fun theLockStateFixtureIsUnchanged() {
        assertEquals(
            "1035f5fd0001cd5aefbe5fe950492bd13df8a6d975844e7cf23f2c7b18cc6285",
            sha256(LockStateFixtures.LOCK_STATE_V1.encodeToByteArray()),
        )
    }

    @Test
    fun theVersionOneLockStateIsRead() {
        val state = LockStateJson.decode(LockStateFixtures.LOCK_STATE_V1)
        fun run(from: Int, count: Int) = ByteArray(count) { (from + it).toByte() }
        assertEquals(310_000, state.pin.iterations)
        assertContentEquals(run(0x00, 16), state.pin.salt)
        assertContentEquals(run(0x10, 12), state.pin.nonce)
        assertContentEquals(run(0x40, 60), state.pin.wrapped)
        assertEquals(100_000, state.phrase.derivation.iterations)
        assertContentEquals(run(0x80, 16), state.phrase.salt)
        assertContentEquals(run(0x90, 12), state.phrase.nonce)
        assertContentEquals(run(0xa0, 48), state.phrase.sealedKey)
        assertEquals(2, state.failedAttempts)
        assertEquals(Moment(7, 123_456), state.lastFailure)
        // And written back, it reads the same.
        assertEquals(state, LockStateJson.decode(LockStateJson.encode(state)))
    }
}
