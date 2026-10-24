package io.github.arpit92k.fortuna.core.money

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The plan's "done when" for step 01: no floating-point type appears anywhere
 * in the money package. Floating point cannot hold amounts like 0.10 exactly.
 *
 * This runs on the JVM only because it reads the source files from disk.
 */
class NoFloatingPointTest {
    @Test
    fun moneyPackageUsesNoFloatingPointTypes() {
        val packageDir = File("src/commonMain/kotlin/io/github/arpit92k/fortuna/core/money")
        assertTrue(packageDir.isDirectory, "Expected the money sources at ${packageDir.absolutePath}")

        val floatingPoint = Regex("""\b(Double|Float)\b|\.to(Double|Float)\(""")
        val sources = packageDir.walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "No sources found in ${packageDir.absolutePath}")

        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (floatingPoint.containsMatchIn(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue(offenders.isEmpty(), "Floating point found in the money package:\n" + offenders.joinToString("\n"))
    }
}
