package io.github.arpit92k.fortuna.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fails if a floating-point type appears in the packages that handle money:
 * floating point cannot hold amounts like 0.10 exactly. This is the "done
 * when" of plan steps 01 and 02, and applies to the domain model too.
 *
 * It reads the source files as text, so it runs on the JVM only. It catches
 * the type names and conversions to them, not bare literals such as 1.5.
 */
class NoFloatingPointTest {
    private val guardedPackages = listOf("money", "rates", "domain")

    @Test
    fun moneyRatesAndTheDomainUseNoFloatingPointTypes() {
        val floatingPoint = Regex("""\b(Double|Float)\b|\.to(Double|Float)\(""")

        val offenders = guardedPackages.flatMap { name ->
            val dir = File("src/commonMain/kotlin/io/github/arpit92k/fortuna/core/$name")
            assertTrue(dir.isDirectory, "Expected the $name sources at ${dir.absolutePath}")
            val sources = dir.walk().filter { it.isFile && it.extension == "kt" }.toList()
            assertTrue(sources.isNotEmpty(), "No sources found in ${dir.absolutePath}")
            sources.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (floatingPoint.containsMatchIn(line)) "$name/${file.name}:${index + 1}: ${line.trim()}" else null
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Floating point found:\n" + offenders.joinToString("\n"))
    }
}
