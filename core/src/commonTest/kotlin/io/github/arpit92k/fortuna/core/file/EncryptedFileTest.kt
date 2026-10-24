@file:OptIn(ExperimentalStdlibApi::class)

package io.github.arpit92k.fortuna.core.file

import io.github.arpit92k.fortuna.core.crypto.Crypto
import io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedFileTest {
    private val crypto = PlatformCrypto

    private val contents = """{"formatVersion":1,"note":"a dataset would go here"}""".encodeToByteArray()

    private fun freshKey() = crypto.randomBytes(Crypto.KEY_SIZE)

    private val lockedKey = LockedKey(
        derivation = KeyDerivation(KeyDerivationAlgorithm.PBKDF2_HMAC_SHA256, iterations = 600_000),
        salt = ByteArray(16) { it.toByte() },
        nonce = ByteArray(12) { (it + 100).toByte() },
        sealedKey = ByteArray(48) { 7 },
    )

    // From the plan: seal then open returns the original bytes.

    @Test
    fun aStoreFileOpensToItsOriginalContents() {
        val key = freshKey()
        val file = EncryptedFile.seal(contents, key, crypto)
        assertContentEquals(contents, EncryptedFile.open(file, key, crypto))
        assertEquals(FileKind.STORE, EncryptedFile.readHeader(file).kind)
        assertNull(EncryptedFile.readHeader(file).lockedKey)
    }

    @Test
    fun aBackupCarriesItsLockedKeyInTheHeader() {
        val key = freshKey()
        val file = EncryptedFile.seal(contents, key, crypto, lockedKey)
        val header = EncryptedFile.readHeader(file)
        assertEquals(FileKind.BACKUP, header.kind)
        assertEquals(lockedKey, header.lockedKey)
        assertContentEquals(contents, EncryptedFile.open(file, key, crypto))
    }

    @Test
    fun emptyContentsSealAndOpen() {
        val key = freshKey()
        assertContentEquals(ByteArray(0), EncryptedFile.open(EncryptedFile.seal(ByteArray(0), key, crypto), key, crypto))
    }

    @Test
    fun everyFileGetsAFreshNonce() {
        val key = freshKey()
        val first = EncryptedFile.seal(contents, key, crypto)
        val second = EncryptedFile.seal(contents, key, crypto)
        assertFalse(EncryptedFile.readHeader(first).nonce.contentEquals(EncryptedFile.readHeader(second).nonce))
        assertFalse(first.contentEquals(second))
    }

    // From the plan: changing any single byte of the header or the body is rejected.
    @Test
    fun changingAnySingleByteIsRejected() {
        val key = freshKey()
        for (file in listOf(EncryptedFile.seal(contents, key, crypto), EncryptedFile.seal(contents, key, crypto, lockedKey))) {
            for (index in file.indices) {
                val changed = file.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
                assertFails("byte $index of ${file.size} changed") { EncryptedFile.open(changed, key, crypto) }
            }
        }
    }

    @Test
    fun changingTheLockedKeyIsCaughtByTheContentsCheck() {
        // The locked key is not decrypted here, but the header is authenticated
        // with the contents, so the file still refuses to open.
        val key = freshKey()
        val file = EncryptedFile.seal(contents, key, crypto, lockedKey)
        val sealedKeyAt = 4 + 2 + 1 + 1 + 4 + 1 + 16 + 12 + 1
        file[sealedKeyAt] = (file[sealedKeyAt].toInt() xor 0x01).toByte()
        assertFailsWith<DecryptionFailedException> { EncryptedFile.open(file, key, crypto) }
    }

    // From the plan: a wrong key is rejected.
    @Test
    fun aWrongKeyIsRejected() {
        val file = EncryptedFile.seal(contents, freshKey(), crypto)
        assertFailsWith<DecryptionFailedException> { EncryptedFile.open(file, freshKey(), crypto) }
    }

    // From the plan: an unknown format version is rejected with its own error, before anything is decrypted.
    @Test
    fun anUnknownVersionIsRejectedBeforeAnythingIsDecrypted() {
        val file = EncryptedFile.seal(contents, freshKey(), crypto)
        file[5] = 2
        val failure = assertFailsWith<UnsupportedFileVersionException> {
            // A key of the wrong size would fail any decryption: this proves none was tried.
            EncryptedFile.open(file, ByteArray(1), crypto)
        }
        assertEquals(2, failure.version)
    }

    @Test
    fun otherFilesAreRecognisedAsNotFortunaFiles() {
        assertFailsWith<NotAnEncryptedFileException> { EncryptedFile.readHeader("PK\u0003\u0004 a zip file".encodeToByteArray()) }
        assertFailsWith<NotAnEncryptedFileException> { EncryptedFile.readHeader(ByteArray(0)) }
    }

    @Test
    fun aFileCutShortIsDamaged() {
        val file = EncryptedFile.seal(contents, freshKey(), crypto, lockedKey)
        for (size in listOf(5, 7, 20, 60, 102, 117)) {
            assertFailsWith<DamagedFileException>("cut to $size bytes") { EncryptedFile.readHeader(file.copyOf(size)) }
        }
    }

    @Test
    fun anUnknownKindOrKeyDerivationIsDamaged() {
        val file = EncryptedFile.seal(contents, freshKey(), crypto, lockedKey)
        assertFailsWith<DamagedFileException> { EncryptedFile.readHeader(file.copyOf().also { it[6] = 9 }) }
        assertFailsWith<DamagedFileException> { EncryptedFile.readHeader(file.copyOf().also { it[7] = 9 }) }
    }

    // From the plan: a file kept in the repository opens with a known key.
    // These were written by a separate implementation (Python's cryptography
    // library) following the layout in EncryptedFile's documentation, so they
    // also check that the code matches the documented format.

    private val fixtureKey = ByteArray(32) { it.toByte() }

    private val storeV1 = (
        "4652544e000101404142434445464748494a4ba4d6dc575352e623beb07844fe44753201b2272ed2192f451e2b2b" +
            "c5e1769fa8f1679d8cef17f0d748e3e0aa3cf1b114f9e130a515"
        ).hexToByteArray()

    private val backupV1 = (
        "4652544e00010201000927c010a0a1a2a3a4a5a6a7a8a9aaabacadaeaf606162636465666768696a6b30eeeeeeee" +
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee5051" +
            "52535455565758595a5bd4484400913ed8c63ed75e0eb891a3b38a57899e5014b60242e08cbed036f0062286e5b3" +
            "4b17e25c9d6ae4bd66876777c9a799a4e7a9"
        ).hexToByteArray()

    @Test
    fun theVersion1StoreFixtureOpensWithItsKey() {
        assertEquals("Fortuna store fixture, file version 1", EncryptedFile.open(storeV1, fixtureKey, crypto).decodeToString())
    }

    @Test
    fun theVersion1BackupFixtureOpensWithItsKeyAndHeader() {
        val header = EncryptedFile.readHeader(backupV1)
        assertEquals(FileKind.BACKUP, header.kind)
        val locked = header.lockedKey!!
        assertEquals(KeyDerivation(KeyDerivationAlgorithm.PBKDF2_HMAC_SHA256, 600_000), locked.derivation)
        assertContentEquals(ByteArray(16) { (0xa0 + it).toByte() }, locked.salt)
        assertContentEquals(ByteArray(12) { (0x60 + it).toByte() }, locked.nonce)
        assertTrue(locked.sealedKey.all { it == 0xee.toByte() })
        assertEquals("Fortuna backup fixture, file version 1", EncryptedFile.open(backupV1, fixtureKey, crypto).decodeToString())
    }
}
