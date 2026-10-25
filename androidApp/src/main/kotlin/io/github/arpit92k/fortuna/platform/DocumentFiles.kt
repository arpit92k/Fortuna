package io.github.arpit92k.fortuna.platform

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Reads and writes the files the user picks with the system file picker: where
 * a backup is saved, and the backup to restore. A file is named by the address
 * the picker gave, as text, so the screens' logic needs nothing from Android.
 *
 * The picker grants access to that one file, so the app asks for no storage
 * permission and sees nothing else.
 */
interface DocumentFiles {
    /** Replaces the file's contents with [bytes]. Throws [IOException] if it cannot. */
    fun write(location: String, bytes: ByteArray)

    /** The file's contents. Throws [IOException] if it cannot be read or is larger than [maxBytes]. */
    fun read(location: String, maxBytes: Int): ByteArray
}

/** Thrown by [DocumentFiles.read] for a file too large to be a backup. */
class FileTooLargeException : IOException("The file is too large")

/** [DocumentFiles] on the Storage Access Framework. */
class AndroidDocumentFiles(context: Context) : DocumentFiles {
    private val resolver = context.applicationContext.contentResolver

    override fun write(location: String, bytes: ByteArray) {
        // "wt" truncates, so a shorter file leaves nothing of a longer one behind.
        val stream = resolver.openOutputStream(Uri.parse(location), "wt") ?: throw IOException("Cannot open $location")
        stream.use { it.write(bytes) }
    }

    override fun read(location: String, maxBytes: Int): ByteArray {
        val stream = resolver.openInputStream(Uri.parse(location)) ?: throw IOException("Cannot open $location")
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > maxBytes) throw FileTooLargeException()
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }
}
