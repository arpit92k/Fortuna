package io.github.arpit92k.fortuna.core.ports

// Replacing a file so that an interruption at any point leaves either the old
// contents or the new, never a mix. Used for both the store and the lock state.
//
// 1. Write the new contents to "<name>.partial", forced to disk.
// 2. Move it to "<name>.new", which marks it as complete.
// 3. Move that onto "<name>".
//
// After an interruption, a partial file may be incomplete, so it is thrown
// away; a new file is complete and the latest, so it is moved into place.
// That also covers platforms where the last move deletes the old file before
// failing to rename the new one.

/** Replaces [name] with [contents], safely against interruption. */
fun PrivateFiles.replaceSafely(name: String, contents: ByteArray) {
    write(partialName(name), contents)
    move(partialName(name), newName(name))
    move(newName(name), name)
}

/** Tidies whatever an interrupted [replaceSafely] of [name] left. Call once on start. */
fun PrivateFiles.finishInterruptedReplace(name: String) {
    delete(partialName(name))
    if (exists(newName(name))) move(newName(name), name)
}

/** Deletes [name] and anything an interrupted replacement of it left. */
fun PrivateFiles.deleteWithLeftovers(name: String) {
    delete(partialName(name))
    delete(newName(name))
    delete(name)
}

internal fun partialName(name: String) = "$name.partial"

internal fun newName(name: String) = "$name.new"
