package io.github.arpit92k.fortuna.core.file

import io.github.arpit92k.fortuna.core.crypto.Crypto

/**
 * The one encrypted file format, used by both the store and backups.
 *
 * A file is a header followed by its contents encrypted with AES-256-GCM
 * under the data key. The whole header is the encryption's associated data,
 * so changing any byte of it makes the file fail to open, just like changing
 * the contents. A backup is the same file with one addition to its header:
 * the copy of the data key locked by the recovery phrase, so the file opens
 * on any device with the phrase.
 *
 * Version 1 layout, with numbers big-endian:
 *
 * ```
 * marker       4 bytes   "FRTN"
 * version      2 bytes   1
 * kind         1 byte    1 = store, 2 = backup
 * locked key   backups only:
 *   algorithm  1 byte    1 = PBKDF2-HMAC-SHA256
 *   iterations 4 bytes
 *   salt       1 byte length, then the salt
 *   nonce      12 bytes
 *   sealed key 1 byte length, then the data key encrypted with AES-256-GCM (48 bytes)
 * nonce        12 bytes  fresh and random for every file written
 * contents     the rest: ciphertext and its 16-byte tag
 * ```
 *
 * The version is read straight after the marker, so a file of a version this
 * code does not know is refused before anything else is read or decrypted.
 */
object EncryptedFile {
    /** The version this code writes, and the only one it reads so far. */
    const val FORMAT_VERSION: Int = 1

    private val MARKER = "FRTN".encodeToByteArray()

    /**
     * Encrypts [contents] under [dataKey] with a fresh random nonce. A backup
     * passes [lockedKey], the data key locked by the recovery phrase.
     */
    fun seal(contents: ByteArray, dataKey: ByteArray, crypto: Crypto, lockedKey: LockedKey? = null): ByteArray {
        val nonce = crypto.randomBytes(Crypto.NONCE_SIZE)
        val header = writeHeader(if (lockedKey == null) FileKind.STORE else FileKind.BACKUP, lockedKey, nonce)
        return header + crypto.encrypt(dataKey, nonce, contents, header)
    }

    /**
     * Reads a file's header without decrypting anything, for example to find
     * a backup's locked key before the data key is known.
     *
     * @throws NotAnEncryptedFileException if the file does not start with the marker.
     * @throws UnsupportedFileVersionException if it is of a version this code does not read.
     * @throws DamagedFileException if the header is cut short or malformed.
     */
    fun readHeader(file: ByteArray): FileHeader {
        val reader = Reader(file)
        if (file.size < MARKER.size || !reader.bytes(MARKER.size).contentEquals(MARKER)) throw NotAnEncryptedFileException()
        val version = reader.u16()
        if (version != FORMAT_VERSION) throw UnsupportedFileVersionException(version)

        val kind = when (val code = reader.u8()) {
            1 -> FileKind.STORE
            2 -> FileKind.BACKUP
            else -> throw DamagedFileException("Unknown file kind $code")
        }
        val lockedKey = if (kind == FileKind.BACKUP) readLockedKey(reader) else null
        val nonce = reader.bytes(Crypto.NONCE_SIZE)
        if (file.size - reader.position < Crypto.TAG_SIZE) throw DamagedFileException("The file is cut short")
        return FileHeader(kind, lockedKey, nonce, headerSize = reader.position)
    }

    /**
     * Decrypts a file's contents with [dataKey].
     *
     * @throws io.github.arpit92k.fortuna.core.crypto.DecryptionFailedException
     *   if the key is wrong or any byte of the file has changed.
     * @throws NotAnEncryptedFileException, UnsupportedFileVersionException or
     *   DamagedFileException as [readHeader] does.
     */
    fun open(file: ByteArray, dataKey: ByteArray, crypto: Crypto): ByteArray {
        val header = readHeader(file)
        val headerBytes = file.copyOfRange(0, header.headerSize)
        val ciphertext = file.copyOfRange(header.headerSize, file.size)
        return crypto.decrypt(dataKey, header.nonce, ciphertext, headerBytes)
    }

    private fun writeHeader(kind: FileKind, lockedKey: LockedKey?, nonce: ByteArray): ByteArray {
        val out = Writer()
        out.bytes(MARKER)
        out.u16(FORMAT_VERSION)
        out.u8(if (kind == FileKind.STORE) 1 else 2)
        if (lockedKey != null) {
            out.u8(lockedKey.derivation.algorithm.code)
            out.u32(lockedKey.derivation.iterations)
            out.u8(lockedKey.salt.size)
            out.bytes(lockedKey.salt)
            out.bytes(lockedKey.nonce)
            out.u8(lockedKey.sealedKey.size)
            out.bytes(lockedKey.sealedKey)
        }
        out.bytes(nonce)
        return out.toByteArray()
    }

    private fun readLockedKey(reader: Reader): LockedKey {
        val code = reader.u8()
        val algorithm = KeyDerivationAlgorithm.entries.firstOrNull { it.code == code }
            ?: throw DamagedFileException("Unknown key derivation $code")
        val iterations = reader.u32()
        val salt = reader.bytes(reader.u8())
        val nonce = reader.bytes(Crypto.NONCE_SIZE)
        val sealedKey = reader.bytes(reader.u8())
        return try {
            LockedKey(KeyDerivation(algorithm, iterations), salt, nonce, sealedKey)
        } catch (e: IllegalArgumentException) {
            throw DamagedFileException(e.message ?: "Malformed locked key")
        }
    }
}

enum class FileKind { STORE, BACKUP }

/** A file's header, read without decrypting anything. */
class FileHeader(
    val kind: FileKind,
    /** The data key locked by the recovery phrase, in backups only. */
    val lockedKey: LockedKey?,
    /** The nonce the contents were encrypted with. */
    val nonce: ByteArray,
    /** How many bytes the header takes; the rest of the file is the encrypted contents. */
    val headerSize: Int,
)

/** How a key is derived from a secret such as the recovery phrase. Stored with it, so it can change later. */
data class KeyDerivation(val algorithm: KeyDerivationAlgorithm, val iterations: Int) {
    init {
        require(iterations > 0) { "Key derivation needs at least one iteration, not $iterations" }
    }
}

enum class KeyDerivationAlgorithm(internal val code: Int) {
    PBKDF2_HMAC_SHA256(1),
}

/**
 * The data key, encrypted with AES-256-GCM under a key derived from a secret.
 * How it is made and opened is step 11's job; this format only carries it.
 */
class LockedKey(
    val derivation: KeyDerivation,
    val salt: ByteArray,
    val nonce: ByteArray,
    /** The 32-byte data key and its 16-byte tag. */
    val sealedKey: ByteArray,
) {
    init {
        require(salt.size in 16..255) { "A salt is 16 to 255 bytes, not ${salt.size}" }
        require(nonce.size == Crypto.NONCE_SIZE) { "A nonce is ${Crypto.NONCE_SIZE} bytes, not ${nonce.size}" }
        require(sealedKey.size == Crypto.KEY_SIZE + Crypto.TAG_SIZE) {
            "A sealed key is ${Crypto.KEY_SIZE + Crypto.TAG_SIZE} bytes, not ${sealedKey.size}"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is LockedKey && other.derivation == derivation && other.salt.contentEquals(salt) &&
            other.nonce.contentEquals(nonce) && other.sealedKey.contentEquals(sealedKey)

    override fun hashCode(): Int =
        ((derivation.hashCode() * 31 + salt.contentHashCode()) * 31 + nonce.contentHashCode()) * 31 + sealedKey.contentHashCode()
}

/** The file does not start with Fortuna's marker: it is some other kind of file. */
class NotAnEncryptedFileException : Exception("This is not a Fortuna file")

/** The file is of a version this code does not read, perhaps written by a newer app. */
class UnsupportedFileVersionException(val version: Int) :
    Exception("File version $version is not supported; this app reads version ${EncryptedFile.FORMAT_VERSION}")

/** The file's header is cut short or malformed. */
class DamagedFileException(message: String) : Exception(message)

private class Writer {
    private val bytes = mutableListOf<Byte>()

    fun u8(value: Int) {
        require(value in 0..0xFF) { "Does not fit in one byte: $value" }
        bytes += value.toByte()
    }

    fun u16(value: Int) {
        require(value in 0..0xFFFF) { "Does not fit in two bytes: $value" }
        bytes += (value shr 8).toByte()
        bytes += value.toByte()
    }

    fun u32(value: Int) {
        require(value >= 0) { "Does not fit in four unsigned bytes: $value" }
        for (shift in intArrayOf(24, 16, 8, 0)) bytes += (value shr shift).toByte()
    }

    fun bytes(value: ByteArray) {
        bytes += value.toList()
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}

private class Reader(private val file: ByteArray) {
    var position: Int = 0
        private set

    fun bytes(count: Int): ByteArray {
        if (count < 0 || file.size - position < count) throw DamagedFileException("The header is cut short")
        return file.copyOfRange(position, position + count).also { position += count }
    }

    fun u8(): Int = bytes(1)[0].toInt() and 0xFF

    fun u16(): Int = bytes(2).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }

    fun u32(): Int {
        val b = bytes(4)
        val value = b.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
        if (value > Int.MAX_VALUE) throw DamagedFileException("Iteration count out of range")
        return value.toInt()
    }
}
