package app.sjgallery.data.media

import android.content.ContentResolver
import android.net.Uri
import app.sjgallery.data.crypto.CryptoManager
import app.sjgallery.data.model.Photo
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copies a picked image into private storage, optionally shrinking it to the
 * device's screen resolution, and encrypts it on the way in.
 *
 * @param maxEdgePx the longest edge of the device display. Larger photos carry
 *   detail this screen cannot show, so they are downscaled to fit within it.
 */
class PhotoImporter(
    private val contentResolver: ContentResolver,
    private val crypto: CryptoManager,
    private val maxEdgePx: Int,
) {
    /**
     * Blocking; call from a background dispatcher.
     *
     * @throws IOException if [source] cannot be read or is not a decodable image.
     */
    fun import(source: Uri, destinationDir: File, optimize: Boolean): Photo {
        val original = contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Unable to open $source")

        var animated = false
        var downscaled = false

        // Decoding doubles as validation: non-images throw here and are rejected.
        val bitmap = decodeSoftwareBitmap(original) { decoder, info ->
            animated = info.isAnimated
            val target = fitWithin(info.size.width, info.size.height, maxEdgePx)
            val tooLarge = target.width != info.size.width || target.height != info.size.height
            if (optimize && !animated && tooLarge) {
                decoder.setTargetSize(target.width, target.height)
                downscaled = true
            } else {
                // The original bytes will be stored; decode a cheap preview only.
                decoder.setTargetSampleSize(VALIDATION_SAMPLE_SIZE)
            }
        }

        val payload = try {
            if (downscaled) {
                // Guard against the rare case where re-encoding makes things bigger.
                bitmap.encodeForStorage().takeIf { it.size < original.size } ?: original
            } else {
                original
            }
        } finally {
            bitmap.recycle()
        }

        val id = UUID.randomUUID().toString()
        val fileName = "$id.bin"
        destinationDir.mkdirs()
        File(destinationDir, fileName).writeBytes(crypto.encrypt(payload))
        return Photo(id = id, fileName = fileName, animated = animated)
    }

    private companion object {
        const val VALIDATION_SAMPLE_SIZE = 8
    }
}
