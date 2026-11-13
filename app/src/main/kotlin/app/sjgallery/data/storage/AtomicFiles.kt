package app.sjgallery.data.storage

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Replaces the file's contents all-or-nothing, so a crash or power loss
 * mid-write can never leave a half-written file behind.
 */
internal fun File.writeAtomically(bytes: ByteArray) {
    parentFile?.mkdirs()
    val atomicFile = AtomicFile(this)
    val stream = atomicFile.startWrite()
    try {
        stream.write(bytes)
        atomicFile.finishWrite(stream)
    } catch (e: IOException) {
        atomicFile.failWrite(stream)
        throw e
    }
}

/** Reads a file written by [writeAtomically], or returns null if it does not exist. */
internal fun File.readAtomicallyOrNull(): ByteArray? =
    try {
        AtomicFile(this).readFully()
    } catch (_: FileNotFoundException) {
        null
    }
