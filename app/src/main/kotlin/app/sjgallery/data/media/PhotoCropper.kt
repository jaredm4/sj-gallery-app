package app.sjgallery.data.media

import android.graphics.Bitmap
import app.sjgallery.data.crypto.CryptoManager
import java.io.File
import java.util.UUID

/**
 * Crops photos that are already in the (encrypted) library.
 *
 * @param maxEdgePx the longest edge of the device display, see [PhotoImporter].
 */
class PhotoCropper(
    private val crypto: CryptoManager,
    private val maxEdgePx: Int,
) {
    /** Blocking. Decodes a reduced-size copy of [source] to show in the crop editor. */
    fun loadPreview(source: File): Bitmap = decode(source, PREVIEW_MAX_EDGE_PX)

    /**
     * Blocking. Writes the [region] of [source] to a new encrypted file in
     * [destinationDir] and leaves [source] untouched.
     *
     * @param shrinkToScreen downscale the result if it exceeds the display size.
     * @return the new file's name.
     */
    fun crop(source: File, destinationDir: File, region: CropRegion, shrinkToScreen: Boolean): String {
        val decoded = decode(source, WORKING_MAX_EDGE_PX)
        val rect = region.toPixelRect(decoded.width, decoded.height)
        var result = Bitmap.createBitmap(decoded, rect.left, rect.top, rect.width, rect.height)
        // createBitmap may hand back the very same object when nothing was cut off.
        if (result !== decoded) decoded.recycle()

        if (shrinkToScreen) {
            val target = fitWithin(result.width, result.height, maxEdgePx)
            if (target.width != result.width || target.height != result.height) {
                val scaled = Bitmap.createScaledBitmap(result, target.width, target.height, true)
                if (scaled !== result) result.recycle()
                result = scaled
            }
        }

        val fileName = "${UUID.randomUUID()}.bin"
        try {
            File(destinationDir, fileName).writeBytes(crypto.encrypt(result.encodeForStorage()))
        } finally {
            result.recycle()
        }
        return fileName
    }

    private fun decode(source: File, maxEdge: Int): Bitmap =
        decodeSoftwareBitmap(crypto.decrypt(source.readBytes())) { decoder, info ->
            val target = fitWithin(info.size.width, info.size.height, maxEdge)
            if (target.width != info.size.width || target.height != info.size.height) {
                decoder.setTargetSize(target.width, target.height)
            }
        }

    private companion object {
        const val PREVIEW_MAX_EDGE_PX = 2048

        /** Bounds memory use when a photo was imported without shrinking. */
        const val WORKING_MAX_EDGE_PX = 4096
    }
}
