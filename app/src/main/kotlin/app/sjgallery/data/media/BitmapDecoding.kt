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
