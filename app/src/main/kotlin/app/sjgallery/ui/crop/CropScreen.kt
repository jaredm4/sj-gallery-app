package app.sjgallery.ui.crop

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sjgallery.R
import app.sjgallery.data.media.CropRegion
import app.sjgallery.ui.appViewModel
import kotlin.math.max
import kotlin.math.roundToInt

/** How far past "just covers the frame" the photo may be zoomed. */
private const val MAX_ZOOM = 8f
private val FRAME_MARGIN = 24.dp

/**
 * Crop editor. A fixed frame in the shape of the device's screen is drawn over
 * the photo; the user drags and pinches the photo underneath to choose what the
 * frame contains. Saving permanently crops the gallery's copy of the photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(galleryId: String, photoId: String, onDone: () -> Unit) {
    val viewModel = appViewModel(key = photoId) {
        CropViewModel(galleryId, photoId, it.galleries, it.displayEdges.portraitAspectRatio)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Start with the frame matching how the device is being held right now.
    val heldUpright = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
    var portraitFrame by rememberSaveable { mutableStateOf(heldUpright) }
    val frameAspectRatio =
        if (portraitFrame) viewModel.screenPortraitAspectRatio else 1f / viewModel.screenPortraitAspectRatio

    // Only read when Save is tapped, so updating it never recomposes this screen.
    var region by remember { mutableStateOf(CropRegion.Full) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_photo)) },
                navigationIcon = {
                    IconButton(onClick = onDone, enabled = !uiState.saving) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (uiState.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.save(region, onFinished = onDone) },
                            enabled = uiState.preview != null,
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                uiState.preview?.let { preview ->
                    CropCanvas(
                        image = preview,
                        frameAspectRatio = frameAspectRatio,
                        onRegionChange = { region = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { portraitFrame = !portraitFrame }) {
                    Text(
                        stringResource(
                            if (portraitFrame) R.string.frame_portrait else R.string.frame_landscape,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Draws [image] beneath a centred, fixed frame and lets the user pan and zoom
 * it. The image is always kept large enough, and close enough, to cover the
 * whole frame, so every possible position is a valid crop.
 *
 * Coordinates: `offset` is how far the image's centre sits from the frame's
 * centre, in pixels; `scale` is screen pixels per image pixel.
 */
@Composable
private fun CropCanvas(
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

        // Start (and restart, if the frame changes shape) zoomed out and centred.
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
