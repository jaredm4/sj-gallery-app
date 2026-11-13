package app.sjgallery.data.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
