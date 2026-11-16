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
    /** Blocking. Decodes a screen-sized copy of [source] to show in the crop editor. */
    fun loadPreview(source: File): Bitmap =
        decodeSoftwareBitmap(crypto.decrypt(source.readBytes())) { decoder, info ->
            val target = fitWithin(info.size.width, info.size.height, maxEdgePx)
            if (target.width != info.size.width || target.height != info.size.height) {
                decoder.setTargetSize(target.width, target.height)
            }
        }

    /**
     * Blocking. Writes the [region] of [source] to a new encrypted file in
     * [destinationDir] and leaves [source] untouched.
     *
     * @param shrinkToScreen downscale the result if it exceeds the display size.
     * @return the new file's name.
     */
    fun crop(source: File, destinationDir: File, region: CropRegion, shrinkToScreen: Boolean): String {
        val cropped = decodeCropped(
            bytes = crypto.decrypt(source.readBytes()),
            maxOutputEdge = maxEdgePx.takeIf { shrinkToScreen },
        ) { _, _ -> region }

        val fileName = "${UUID.randomUUID()}.bin"
        try {
            File(destinationDir, fileName).writeBytes(crypto.encrypt(cropped.encodeForStorage()))
        } finally {
            cropped.recycle()
        }
        return fileName
    }
}
