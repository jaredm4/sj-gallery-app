package app.sjgallery.data.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import app.sjgallery.data.crypto.CryptoManager
import app.sjgallery.data.model.Photo
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * A picked file that has been read into memory but not yet added to a gallery,
 * so the user can choose how to crop it while it is still at full resolution.
 */
class PickedImage(
    /** The file exactly as picked. */
    val bytes: ByteArray,
    /** A screen-sized rendering for the crop editor (first frame if animated). */
    val preview: Bitmap,
    val animated: Boolean,
)

/**
 * Copies picked images into private storage, cropping and/or shrinking them to
 * the device's screen on the way in, and encrypts the result.
 *
 * All methods block; call them from a background dispatcher. They throw
 * [IOException] if the source cannot be read or is not a decodable image.
 *
 * @param maxEdgePx the longest edge of the device display. Larger images carry
 *   detail this screen cannot show.
 */
class PhotoImporter(
    private val contentResolver: ContentResolver,
    private val crypto: CryptoManager,
    private val maxEdgePx: Int,
) {
    /** Imports the whole image, downscaled to fit the screen if [optimize] is set. */
    fun import(source: Uri, destinationDir: File, optimize: Boolean): Photo {
        val original = read(source)
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
        return store(payload, destinationDir, animated)
    }

    /** First half of a reviewed import: loads [source] so it can be shown in the crop editor. */
    fun open(source: Uri): PickedImage {
        val bytes = read(source)
        var animated = false
        val preview = decodeSoftwareBitmap(bytes) { decoder, info ->
            animated = info.isAnimated
            val target = fitWithin(info.size.width, info.size.height, maxEdgePx)
            if (target.width != info.size.width || target.height != info.size.height) {
                decoder.setTargetSize(target.width, target.height)
            }
        }
        return PickedImage(bytes, preview, animated)
    }

    /**
     * Second half of a reviewed import: stores the chosen [region] of [image].
     * The crop is taken from the full-resolution original, so zooming in costs
     * no sharpness beyond what the source file itself has.
     */
    fun importCropped(image: PickedImage, destinationDir: File, region: CropRegion, shrinkToScreen: Boolean): Photo =
        storeCropped(image.bytes, destinationDir, shrinkToScreen) { _, _ -> region }

    /** Stores [image] byte-for-byte. Used for animated images, which cannot be cropped. */
    fun importUnchanged(image: PickedImage, destinationDir: File): Photo =
        store(image.bytes, destinationDir, image.animated)

    /**
     * Imports without review, keeping the largest centred part of the image
     * that has the given [aspectRatio] (width / height). Animated images are
     * stored unchanged.
     */
    fun importCenterCropped(source: Uri, destinationDir: File, aspectRatio: Float, shrinkToScreen: Boolean): Photo {
        val bytes = read(source)
        if (isAnimated(bytes)) return store(bytes, destinationDir, animated = true)
        return storeCropped(bytes, destinationDir, shrinkToScreen) { width, height ->
            centeredCropRegion(width, height, aspectRatio)
        }
    }

    private fun storeCropped(
        bytes: ByteArray,
        destinationDir: File,
        shrinkToScreen: Boolean,
        region: (width: Int, height: Int) -> CropRegion,
    ): Photo {
        val cropped = decodeCropped(bytes, maxEdgePx.takeIf { shrinkToScreen }, region)
        return try {
            store(cropped.encodeForStorage(), destinationDir, animated = false)
        } finally {
            cropped.recycle()
        }
    }

    private fun store(payload: ByteArray, destinationDir: File, animated: Boolean): Photo {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.bin"
        destinationDir.mkdirs()
        File(destinationDir, fileName).writeBytes(crypto.encrypt(payload))
        return Photo(id = id, fileName = fileName, animated = animated)
    }

    private fun read(source: Uri): ByteArray =
        contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Unable to open $source")

    private fun isAnimated(bytes: ByteArray): Boolean {
        var animated = false
        decodeSoftwareBitmap(bytes) { decoder, info ->
            animated = info.isAnimated
            decoder.setTargetSampleSize(VALIDATION_SAMPLE_SIZE)
        }.recycle()
        return animated
    }

    private companion object {
        const val VALIDATION_SAMPLE_SIZE = 8
    }
}
