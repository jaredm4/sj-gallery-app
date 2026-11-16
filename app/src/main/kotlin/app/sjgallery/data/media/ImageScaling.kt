package app.sjgallery.data.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PixelSize(val width: Int, val height: Int)

/**
 * Scales [width] x [height] down, preserving aspect ratio, so that its longest
 * edge is at most [maxEdge]. Never scales up.
 */
fun fitWithin(width: Int, height: Int, maxEdge: Int): PixelSize {
    require(width > 0 && height > 0 && maxEdge > 0) { "Dimensions must be positive" }
    val longest = max(width, height)
    if (longest <= maxEdge) return PixelSize(width, height)
    val scale = maxEdge.toFloat() / longest
    return PixelSize(
        width = max(1, (width * scale).roundToInt()),
        height = max(1, (height * scale).roundToInt()),
    )
}

/**
 * Largest power-of-two subsampling factor that still leaves the shortest edge
 * of a [width] x [height] image at least [minEdge] pixels long.
 */
fun sampleSizeFor(width: Int, height: Int, minEdge: Int): Int {
    require(minEdge > 0) { "minEdge must be positive" }
    val shortest = min(width, height)
    var sampleSize = 1
    while (shortest / (sampleSize * 2) >= minEdge) sampleSize *= 2
    return sampleSize
}

/**
 * A rectangular part of an image, expressed as fractions (0..1) of its width
 * and height so it is independent of the resolution it was chosen at.
 */
data class CropRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val Full = CropRegion(0f, 0f, 1f, 1f)
    }
}

data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** Maps the region onto an image of the given size; always at least 1x1 and in bounds. */
fun CropRegion.toPixelRect(imageWidth: Int, imageHeight: Int): PixelRect {
    require(imageWidth > 0 && imageHeight > 0) { "Dimensions must be positive" }
    val l = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
    val t = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
    val r = (right * imageWidth).roundToInt().coerceIn(l + 1, imageWidth)
    val b = (bottom * imageHeight).roundToInt().coerceIn(t + 1, imageHeight)
    return PixelRect(left = l, top = t, width = r - l, height = b - t)
}

/**
 * The largest centred part of a [width] x [height] image that has the given
 * [aspectRatio] (width / height): what "fill the screen" would show.
 */
fun centeredCropRegion(width: Int, height: Int, aspectRatio: Float): CropRegion {
    require(width > 0 && height > 0 && aspectRatio > 0f) { "Dimensions must be positive" }
    val imageAspect = width.toFloat() / height
    return if (imageAspect > aspectRatio) {
        // Image is wider than the target: keep full height, trim the sides.
        val kept = aspectRatio / imageAspect
        CropRegion(left = (1f - kept) / 2f, top = 0f, right = (1f + kept) / 2f, bottom = 1f)
    } else {
        val kept = imageAspect / aspectRatio
        CropRegion(left = 0f, top = (1f - kept) / 2f, right = 1f, bottom = (1f + kept) / 2f)
    }
}

/** Upper bound on decoded pixels (about 128 MB as ARGB) to keep memory use sane. */
const val MAX_DECODE_PIXELS = 32_000_000L

/**
 * How large to decode a [width] x [height] image so that, once [region] is cut
 * out of it, the cut-out has all the resolution it needs and no more.
 *
 * Decoding at full size and scaling afterwards would give the same picture,
 * but a 50-megapixel photo needs 200 MB that way.
 *
 * @param maxOutputEdge longest edge wanted for the cut-out, or null to keep
 *   every available pixel (still subject to [maxPixels]).
 */
fun decodeSizeForCrop(
    width: Int,
    height: Int,
    region: CropRegion,
    maxOutputEdge: Int?,
    maxPixels: Long = MAX_DECODE_PIXELS,
): PixelSize {
    require(width > 0 && height > 0) { "Dimensions must be positive" }
    val regionLongestEdge = max(
        max((region.right - region.left) * width, (region.bottom - region.top) * height),
        1f,
    )
    val scaleForOutput = if (maxOutputEdge == null) 1f else min(1f, maxOutputEdge / regionLongestEdge)
    val scaleForMemory = sqrt(maxPixels.toDouble() / (width.toDouble() * height)).toFloat()
    val scale = min(scaleForOutput, scaleForMemory)
    if (scale >= 1f) return PixelSize(width, height)
    return PixelSize(
        width = max(1, (width * scale).roundToInt()),
        height = max(1, (height * scale).roundToInt()),
    )
}
