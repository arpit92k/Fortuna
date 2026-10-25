@file:OptIn(ExperimentalStdlibApi::class)

package io.github.arpit92k.fortuna.core.phrase

import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RecoveryPhraseTest {
    private val crypto = PlatformCrypto

    // The 12-word English vectors published with the BIP39 reference
    // implementation (github.com/trezor/python-mnemonic, vectors.json).
    private val vectors = listOf(
        "00000000000000000000000000000000" to
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f" to
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
        "80808080808080808080808080808080" to
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
        "ffffffffffffffffffffffffffffffff" to
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "9e885d952ad362caeb4efe34a8e91bd2" to
            "ozone drill grab fiber curtain grace pudding thank cruise elder eight picnic",
        "c0ba5a8e914111210f2bd131f3d5e08d" to
            "scheme spot photo card baby mountain device kick cradle pact join borrow",
        "23db8160a31d3e0dca3688ed941adbf3" to
            "cat swing flag economy stadium alone churn speed unique patch report train",
        "f30f8c1da665478f49b001d94c5fc452" to
            "vessel ladder alter error federal sibling chat ability sun glass valve picture",
    )

    private fun parsed(text: String): RecoveryPhrase =
        assertIs<PhraseParseResult.Success>(RecoveryPhrase.parse(text, crypto), "parsing \"$text\"").phrase

    private fun problem(text: String): PhraseProblem =
        assertIs<PhraseParseResult.Failure>(RecoveryPhrase.parse(text, crypto), "parsing \"$text\"").problem

    // From the plan: the published BIP39 test vectors, in both directions.

    @Test
    fun bytesBecomeThePublishedWords() {
        for ((secret, words) in vectors) {
            assertEquals(words, RecoveryPhrase.fromSecret(secret.hexToByteArray(), crypto).toText(), secret)
        }
    }

    @Test
    fun thePublishedWordsBecomeTheBytesAgain() {
        for ((secret, words) in vectors) {
            assertContentEquals(secret.hexToByteArray(), parsed(words).toSecret(), words)
        }
    }

    // From the plan: a fixed example with one word swapped for another fails the checksum.
    @Test
    fun aWordSwappedForAnotherValidWordFailsTheChecksum() {
        val swapped = "about winner thank year wave sausage worth useful legal winner thank yellow"
        assertEquals(PhraseProblem.ChecksumMismatch, problem(swapped))
    }

    // From the plan: a word not in the list is named in the error, with its position.
    @Test
    fun aWordNotInTheListIsNamedWithItsPosition() {
        val typo = "legal winner thank year wave sausage worth usefull legal winner thank yellow"
        assertEquals(PhraseProblem.UnknownWord(8, "usefull"), problem(typo))
    }

    @Test
    fun theFirstUnknownWordIsTheOneReported() {
        assertEquals(PhraseProblem.UnknownWord(2, "winnr"), problem("legal winnr thank yer"))
    }

    // From the plan: typed input is tidied (case, extra spaces).
    @Test
    fun caseAndExtraSpacesDoNotMatter() {
        val messy = "  Legal WINNER thank\tyear  wave\nsausage worth useful legal winner thank Yellow \n"
        val phrase = parsed(messy)
        assertEquals("legal winner thank year wave sausage worth useful legal winner thank yellow", phrase.toText())
        assertEquals(parsed(vectors[1].second), phrase)
    }

    @Test
    fun onlyTwelveWordsAreAccepted() {
        assertEquals(PhraseProblem.WrongWordCount(0), problem(""))
        assertEquals(PhraseProblem.WrongWordCount(0), problem("   "))
        assertEquals(PhraseProblem.WrongWordCount(11), problem("legal winner thank year wave sausage worth useful legal winner thank"))
        // A valid 24-word BIP39 phrase is still refused: Fortuna's phrases have twelve.
        val twentyFour = (List(23) { "abandon" } + "art").joinToString(" ")
        assertEquals(PhraseProblem.WrongWordCount(24), problem(twentyFour))
    }

    @Test
    fun theSecretMustBeSixteenBytes() {
        assertFailsWith<IllegalArgumentException> { RecoveryPhrase.fromSecret(ByteArray(15), crypto) }
        assertFailsWith<IllegalArgumentException> { RecoveryPhrase.fromSecret(ByteArray(32), crypto) }
    }

    @Test
    fun randomSecretsSurviveTheRoundTrip() {
        repeat(200) {
            val secret = crypto.randomBytes(RecoveryPhrase.SECRET_SIZE)
            val phrase = RecoveryPhrase.fromSecret(secret, crypto)
            assertEquals(RecoveryPhrase.WORD_COUNT, phrase.words.size)
            assertContentEquals(secret, parsed(phrase.toText()).toSecret())
        }
    }

    @Test
    fun thePhraseIsNeverPrinted() {
        val phrase = parsed(vectors[1].second)
        assertFalse("legal" in phrase.toString(), phrase.toString())
    }

    @Test
    fun theSecretCannotBeChangedFromOutside() {
        val phrase = parsed(vectors[0].second)
        phrase.toSecret()[0] = 1
        assertContentEquals(ByteArray(16), phrase.toSecret())
    }

    // The word list itself: 2,048 distinct words, in order, exactly as published.

    @Test
    fun theWordListIsThePublishedOne() {
        assertEquals(2048, bip39EnglishWords.size)
        assertEquals(bip39EnglishWords.sorted(), bip39EnglishWords)
        assertEquals(2048, bip39EnglishWords.toSet().size)
        // english.txt is one word per line, each ending in a line break.
        val file = bip39EnglishWords.joinToString(separator = "\n", postfix = "\n").encodeToByteArray()
        assertEquals(
            "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda",
            crypto.sha256(file).toHexString(),
        )
    }

    @Test
    fun theWordListIsOfferedInOrderForSuggestions() {
        assertEquals(2048, RecoveryPhrase.wordList.size)
        assertEquals("abandon", RecoveryPhrase.wordList.first())
        assertEquals("zoo", RecoveryPhrase.wordList.last())
    }
}
