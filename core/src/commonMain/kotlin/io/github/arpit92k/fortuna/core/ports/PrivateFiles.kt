package io.github.arpit92k.fortuna.core.ports

import okio.FileSystem
import okio.Path
import okio.use

/**
 * The Private files port: the app's own storage, which no other app can read.
 *
 * Files are named, not given paths, and live in one directory chosen by the
 * platform. These few operations are all the store needs to replace a file
 * safely: write a new copy beside the old one, then move it into place.
 */
interface PrivateFiles {
    fun exists(name: String): Boolean

    /** The whole file. Throws if it does not exist. */
    fun read(name: String): ByteArray

    /** Creates or overwrites the file, and returns only once it is written to disk. */
    fun write(name: String, contents: ByteArray)

    /** Moves [from] to [to] in one step, replacing [to] if it exists. */
    fun move(from: String, to: String)

    /** Deletes the file if it exists. */
    fun delete(name: String)
}

/**
 * [PrivateFiles] on an Okio [FileSystem], in [directory]. On Android this is
 * the app's private directory (step 14); tests use Okio's in-memory file system.
 */
class OkioPrivateFiles(private val fileSystem: FileSystem, private val directory: Path) : PrivateFiles {
    init {
        fileSystem.createDirectories(directory)
    }

    override fun exists(name: String): Boolean = fileSystem.exists(path(name))

    override fun read(name: String): ByteArray = fileSystem.read(path(name)) { readByteArray() }

    override fun write(name: String, contents: ByteArray) {
        fileSystem.openReadWrite(path(name)).use { handle ->
            handle.resize(0)
            handle.write(0, contents, 0, contents.size)
            // On Android and the JVM this forces the bytes to disk.
            handle.flush()
        }
    }

    override fun move(from: String, to: String) = fileSystem.atomicMove(path(from), path(to))

    override fun delete(name: String) = fileSystem.delete(path(name), mustExist = false)

    private fun path(name: String): Path {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") {
            "A private file is named, not given a path: \"$name\""
        }
        return directory / name
    }
}
