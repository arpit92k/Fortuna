package io.github.arpit92k.fortuna.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The plan's rule for step 05: only the cryptography wrapper uses
 * cryptography-kotlin, so the library can be replaced in one place.
 *
 * It reads the source files as text, so it runs on the JVM only.
 */
class CryptographyLibraryIsolationTest {
    @Test
    fun onlyTheCryptoPackageImportsTheLibrary() {
        val sourceRoot = File("src/commonMain/kotlin")
        val cryptoDir = File(sourceRoot, "io/github/arpit92k/fortuna/core/crypto")
        assertTrue(cryptoDir.isDirectory, "Expected the crypto sources at ${cryptoDir.absolutePath}")

        val offenders = sourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" && !it.startsWith(cryptoDir) }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import dev.whyoleg.cryptography") } }
            .map { it.relativeTo(sourceRoot).path }
            .toList()
        assertTrue(offenders.isEmpty(), "cryptography-kotlin used outside the crypto package:\n" + offenders.joinToString("\n"))
    }
}
