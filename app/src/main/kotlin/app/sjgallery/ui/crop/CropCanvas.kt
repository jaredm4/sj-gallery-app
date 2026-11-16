package app.sjgallery.ui.crop

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.sjgallery.data.media.CropRegion
import kotlin.math.max
import kotlin.math.roundToInt

/** How far past "just covers the frame" the photo may be zoomed. */
private const val MAX_ZOOM = 8f
private val FRAME_MARGIN = 24.dp

/**
 * Draws [image] beneath a centred, fixed frame and lets the user pan and zoom
 * it. The image is always kept large enough, and close enough, to cover the
 * whole frame, so every possible position is a valid crop. It starts zoomed
 * out and centred, which is exactly what "fill the screen" would show.
 *
 * Coordinates: `offset` is how far the image's centre sits from the frame's
 * centre, in pixels; `scale` is screen pixels per image pixel.
 *
 * @param frameAspectRatio width / height of the frame.
 * @param onRegionChange reports which part of the image is inside the frame.
 */
@Composable
internal fun CropCanvas(
    image: ImageBitmap,
    frameAspectRatio: Float,
    onRegionChange: (CropRegion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val marginPx = with(LocalDensity.current) { FRAME_MARGIN.toPx() }
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val frame = remember(constraints.maxWidth, constraints.maxHeight, frameAspectRatio, marginPx) {
            frameSize(
                available = Size(
                    width = constraints.maxWidth - 2 * marginPx,
                    height = constraints.maxHeight - 2 * marginPx,
                ),
                aspectRatio = frameAspectRatio,
            )
        }
        val minScale = max(frame.width / image.width, frame.height / image.height)

        // Start (and restart, if the frame or image changes) zoomed out and centred.
        var scale by remember(frame, image) { mutableFloatStateOf(minScale) }
        var offset by remember(frame, image) { mutableStateOf(Offset.Zero) }

        LaunchedEffect(frame, image, scale, offset) {
            onRegionChange(cropRegion(image, frame, scale, offset))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(frame, image) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, minScale * MAX_ZOOM)
                        // Zoom around the fingers rather than around the centre.
                        val pivot = centroid - Offset(size.width / 2f, size.height / 2f)
                        val moved = (offset - pivot) * (newScale / scale) + pivot + pan
                        scale = newScale
                        offset = clampToCoverFrame(moved, image, newScale, frame)
                    }
                },
        ) {
            val drawnWidth = image.width * scale
            val drawnHeight = image.height * scale
            val imageTopLeft = center + offset - Offset(drawnWidth / 2f, drawnHeight / 2f)
            drawImage(
                image = image,
                dstOffset = IntOffset(imageTopLeft.x.roundToInt(), imageTopLeft.y.roundToInt()),
                dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
            )

            // Dim everything outside the frame, then outline it.
            val frameTopLeft = center - Offset(frame.width / 2f, frame.height / 2f)
            val frameRight = frameTopLeft.x + frame.width
            val frameBottom = frameTopLeft.y + frame.height
            val scrim = Color.Black.copy(alpha = 0.65f)
            drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, frameTopLeft.y))
            drawRect(scrim, topLeft = Offset(0f, frameBottom), size = Size(size.width, size.height - frameBottom))
            drawRect(scrim, topLeft = Offset(0f, frameTopLeft.y), size = Size(frameTopLeft.x, frame.height))
            drawRect(
                scrim,
                topLeft = Offset(frameRight, frameTopLeft.y),
                size = Size(size.width - frameRight, frame.height),
            )
            drawRect(Color.White, topLeft = frameTopLeft, size = frame, style = Stroke(width = strokePx))
        }
    }
}

/** The largest frame of the given aspect ratio (width / height) that fits in [available]. */
private fun frameSize(available: Size, aspectRatio: Float): Size {
    val width = available.width.coerceAtLeast(1f)
    val height = available.height.coerceAtLeast(1f)
    return if (width / height > aspectRatio) {
        Size(height * aspectRatio, height)
    } else {
        Size(width, width / aspectRatio)
    }
}

/** Limits [offset] so that no edge of the scaled image comes inside the frame. */
private fun clampToCoverFrame(offset: Offset, image: ImageBitmap, scale: Float, frame: Size): Offset {
    val maxX = ((image.width * scale - frame.width) / 2f).coerceAtLeast(0f)
    val maxY = ((image.height * scale - frame.height) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Which part of the image currently lies inside the frame. */
private fun cropRegion(image: ImageBitmap, frame: Size, scale: Float, offset: Offset): CropRegion {
    val drawnWidth = image.width * scale
    val drawnHeight = image.height * scale
    val left = ((drawnWidth - frame.width) / 2f - offset.x) / drawnWidth
    val top = ((drawnHeight - frame.height) / 2f - offset.y) / drawnHeight
    return CropRegion(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = (left + frame.width / drawnWidth).coerceIn(0f, 1f),
        bottom = (top + frame.height / drawnHeight).coerceIn(0f, 1f),
    )
}

/**
 * Frame orientation state shared by the crop screens: starts matching how the
 * device is being held, and can be flipped by the user.
 */
@Composable
internal fun rememberPortraitFrame(): MutableState<Boolean> {
    val heldUpright = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    return rememberSaveable { mutableStateOf(heldUpright) }
}
