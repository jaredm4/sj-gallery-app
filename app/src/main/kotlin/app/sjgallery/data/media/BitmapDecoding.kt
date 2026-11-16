package app.sjgallery.data.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Decodes encoded image bytes (JPEG, PNG, GIF, WebP, HEIF, ...) into a
 * software-backed bitmap that can be drawn onto a canvas or re-compressed.
 * EXIF orientation is applied by the platform decoder. For animated images
 * only the first frame is decoded.
 *
 * @param configure called once the header is known, e.g. to request a smaller
 *   output size before any pixels are decoded.
 */
internal fun decodeSoftwareBitmap(
    bytes: ByteArray,
    configure: (decoder: ImageDecoder, info: ImageDecoder.ImageInfo) -> Unit = { _, _ -> },
): Bitmap {
    val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        configure(decoder, info)
    }
}

private const val JPEG_QUALITY = 90

/**
 * Compresses a bitmap for storage: PNG where there is transparency to keep,
 * otherwise JPEG, which is far smaller for photos.
 */
internal fun Bitmap.encodeForStorage(): ByteArray {
    val format = if (hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    return ByteArrayOutputStream().also { compress(format, JPEG_QUALITY, it) }.toByteArray()
}

/**
 * Decodes the part of an image selected by [region], reading only as much
 * resolution as that part needs (see [decodeSizeForCrop]).
 *
 * @param region chooses the part to keep once the image's dimensions are known.
 * @param maxOutputEdge longest edge wanted for the result, or null for no limit.
 */
internal fun decodeCropped(
    bytes: ByteArray,
    maxOutputEdge: Int?,
    region: (width: Int, height: Int) -> CropRegion,
): Bitmap {
    var chosen = CropRegion.Full
    val decoded = decodeSoftwareBitmap(bytes) { decoder, info ->
        val width = info.size.width
        val height = info.size.height
        chosen = region(width, height)
        val target = decodeSizeForCrop(width, height, chosen, maxOutputEdge)
        if (target.width != width || target.height != height) {
            decoder.setTargetSize(target.width, target.height)
        }
    }

    val rect = chosen.toPixelRect(decoded.width, decoded.height)
    var result = Bitmap.createBitmap(decoded, rect.left, rect.top, rect.width, rect.height)
    // createBitmap may hand back the very same object when nothing was cut off.
    if (result !== decoded) decoded.recycle()

    if (maxOutputEdge != null) {
        // Usually a no-op; only corrects rounding from the decode step.
        val target = fitWithin(result.width, result.height, maxOutputEdge)
        if (target.width != result.width || target.height != result.height) {
            val scaled = Bitmap.createScaledBitmap(result, target.width, target.height, true)
            if (scaled !== result) result.recycle()
            result = scaled
        }
    }
    return result
}
