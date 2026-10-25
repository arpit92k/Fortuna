package io.github.arpit92k.fortuna.core.phrase

import io.github.arpit92k.fortuna.core.crypto.Crypto

/**
 * A recovery phrase: twelve words from the BIP39 English list that stand for
 * sixteen random bytes, the secret the user writes down at setup.
 *
 * The twelve words hold 132 bits: the 128 bits of the secret, then a 4-bit
 * checksum taken from its SHA-256 hash. The checksum catches most writing
 * mistakes, including a word swapped for another valid one, before any key
 * is derived from a wrong phrase.
 *
 * This class turns bytes into words and back. Where the random bytes come
 * from, and how a key is derived from them, belong to later steps.
 */
class RecoveryPhrase private constructor(
    /** The twelve words in order, in lower case. */
    val words: List<String>,
    private val secret: ByteArray,
) {
    /** The words separated by single spaces, for showing to the user. */
    fun toText(): String = words.joinToString(" ")

    /** The sixteen bytes the phrase stands for. Each call returns a fresh copy. */
    fun toSecret(): ByteArray = secret.copyOf()

    override fun equals(other: Any?): Boolean = other is RecoveryPhrase && other.words == words

    override fun hashCode(): Int = words.hashCode()

    /** Never shows the words, so a phrase cannot leak into a log by accident. */
    override fun toString(): String = "RecoveryPhrase($WORD_COUNT words)"

    companion object {
        /** How many words a phrase has. */
        const val WORD_COUNT: Int = 12

        /** How many bytes of secret a phrase stands for. */
        const val SECRET_SIZE: Int = 16

        /** The 2048 words a phrase is made from, in order, for suggesting words as the user types one. */
        val wordList: List<String> get() = bip39EnglishWords

        private const val BITS_PER_WORD = 11
        private const val CHECKSUM_BITS = 4

        /** The phrase for [secret], which must be [SECRET_SIZE] random bytes. */
        fun fromSecret(secret: ByteArray, crypto: Crypto): RecoveryPhrase {
            require(secret.size == SECRET_SIZE) { "A recovery phrase stands for $SECRET_SIZE bytes, not ${secret.size}" }
            val bits = secret + byteArrayOf(checksumOf(secret, crypto).toByte())
            val words = List(WORD_COUNT) { index -> bip39EnglishWords[readBits(bits, index * BITS_PER_WORD, BITS_PER_WORD)] }
            return RecoveryPhrase(words, secret.copyOf())
        }

        /**
         * Reads a phrase the user typed. Case and extra spaces, tabs or line
         * breaks between words do not matter.
         *
         * The words are checked in order, so the first word not in the list is
         * reported by its position. Then the count is checked, then the checksum.
         */
        fun parse(text: String, crypto: Crypto): PhraseParseResult {
            val typed = text.trim().lowercase().split(whitespace).filter { it.isNotEmpty() }

            val indices = typed.mapIndexed { position, word ->
                val index = wordIndex[word] ?: return PhraseParseResult.Failure(PhraseProblem.UnknownWord(position + 1, word))
                index
            }
            if (indices.size != WORD_COUNT) return PhraseParseResult.Failure(PhraseProblem.WrongWordCount(indices.size))

            // 12 words of 11 bits: 16 bytes of secret, then the checksum in the top 4 bits of byte 17.
            val bits = ByteArray(SECRET_SIZE + 1)
            indices.forEachIndexed { position, index -> writeBits(bits, position * BITS_PER_WORD, BITS_PER_WORD, index) }
            val secret = bits.copyOf(SECRET_SIZE)
            val checksum = (bits[SECRET_SIZE].toInt() and 0xFF) shr (8 - CHECKSUM_BITS)
            if (checksum != checksumOf(secret, crypto) shr (8 - CHECKSUM_BITS)) {
                return PhraseParseResult.Failure(PhraseProblem.ChecksumMismatch)
            }
            return PhraseParseResult.Success(RecoveryPhrase(typed, secret))
        }

        private val whitespace = Regex("""\s+""")

        private val wordIndex: Map<String, Int> by lazy {
            bip39EnglishWords.withIndex().associate { (index, word) -> word to index }
        }

        // The first byte of the secret's SHA-256 hash, of which the top 4 bits are the checksum.
        private fun checksumOf(secret: ByteArray, crypto: Crypto): Int = crypto.sha256(secret)[0].toInt() and 0xF0

        private fun readBits(bytes: ByteArray, start: Int, count: Int): Int {
            var value = 0
            for (offset in 0 until count) {
                val bit = start + offset
                value = (value shl 1) or ((bytes[bit / 8].toInt() shr (7 - bit % 8)) and 1)
            }
            return value
        }

        private fun writeBits(bytes: ByteArray, start: Int, count: Int, value: Int) {
            for (offset in 0 until count) {
                val bit = start + offset
                if ((value shr (count - 1 - offset)) and 1 == 1) {
                    bytes[bit / 8] = (bytes[bit / 8].toInt() or (1 shl (7 - bit % 8))).toByte()
                }
            }
        }
    }
}

/** The outcome of [RecoveryPhrase.parse]. */
sealed interface PhraseParseResult {
    data class Success(val phrase: RecoveryPhrase) : PhraseParseResult

    data class Failure(val problem: PhraseProblem) : PhraseParseResult
}

/** Why typed text is not a valid recovery phrase. */
sealed interface PhraseProblem {
    /** The word at [position], counting from 1, is not in the BIP39 English list. */
    data class UnknownWord(val position: Int, val word: String) : PhraseProblem

    /** Every word is in the list, but there are [count] of them instead of twelve. */
    data class WrongWordCount(val count: Int) : PhraseProblem

    /** Every word is in the list, but the checksum does not match: a word is wrong or out of order. */
    data object ChecksumMismatch : PhraseProblem
}
