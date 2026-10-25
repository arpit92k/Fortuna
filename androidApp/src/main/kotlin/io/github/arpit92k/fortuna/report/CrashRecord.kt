package io.github.arpit92k.fortuna.report

import java.io.File

// A saved record of the app's crashes, to be offered for reporting the next
// time it opens (UF-18). A record holds where in the code the crash happened:
// the kind of each error and its code locations. Never an error's message,
// which could hold a figure or a name.

/** How many code locations are kept for each error in a crash: enough to find it, without pages of framework calls. */
const val FRAMES_KEPT = 25

/** [throwable] as a crash record: each error's kind and code locations, causes included, and no messages. */
fun crashRecordOf(throwable: Throwable, appVersion: String): String = buildString {
    appendLine("Fortuna $appVersion crashed")
    var error: Throwable? = throwable
    var first = true
    val seen = mutableSetOf<Throwable>()
    while (error != null && seen.add(error)) {
        appendLine(if (first) error.javaClass.name else "Caused by: ${error.javaClass.name}")
        val frames = error.stackTrace
        for (frame in frames.take(FRAMES_KEPT)) {
            val place = if (frame.fileName != null && frame.lineNumber >= 0) "${frame.fileName}:${frame.lineNumber}" else "unknown"
            appendLine("    at ${frame.className}.${frame.methodName}($place)")
        }
        if (frames.size > FRAMES_KEPT) appendLine("    … ${frames.size - FRAMES_KEPT} more")
        error = error.cause
        first = false
    }
}.trimEnd()

/**
 * The crash records on this phone, in the app's own storage, newest last.
 * Only the latest [keep] are kept.
 */
class CrashStore(private val directory: File, private val keep: Int = 3) {
    /** Saves [record]. Called as the app crashes, so it never throws. */
    fun save(record: String) {
        runCatching {
            directory.mkdirs()
            // The name only orders the records; it is never part of a report.
            File(directory, "crash-${System.currentTimeMillis()}-${System.nanoTime()}.txt").writeText(record)
            files().dropLast(keep).forEach { it.delete() }
        }
    }

    fun records(): List<String> = files().mapNotNull { runCatching { it.readText() }.getOrNull() }

    /** Deletes every record: after reporting, or when the user chooses to instead. */
    fun clear() {
        files().forEach { it.delete() }
    }

    private fun files(): List<File> =
        directory.listFiles { file -> file.name.startsWith("crash-") }
            ?.sortedWith(compareBy({ part(it.name, 0) }, { part(it.name, 1) }))
            .orEmpty()

    /** The clock reading and the tie-breaker in a record's name, crash-<millis>-<nanos>.txt. */
    private fun part(name: String, index: Int): Long =
        name.removePrefix("crash-").removeSuffix(".txt").split('-').getOrNull(index)?.toLongOrNull() ?: 0
}

/**
 * Saves a crash record for any crash, then lets the crash go on as before, so
 * the phone still closes the app and the system still sees the crash.
 */
fun recordCrashes(store: CrashStore, appVersion: String) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        store.save(crashRecordOf(throwable, appVersion))
        previous?.uncaughtException(thread, throwable)
    }
}
