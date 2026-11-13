package app.sjgallery.ui.slideshow

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sjgallery.R
import app.sjgallery.data.settings.ScaleMode
import app.sjgallery.data.settings.Settings
import app.sjgallery.data.settings.SlideTransition
import app.sjgallery.image.EncryptedImage
import app.sjgallery.ui.appViewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import kotlinx.coroutines.delay

private const val CROSSFADE_MILLIS = 600

/**
 * Full-screen slideshow. Tap the right half of the screen to skip ahead, the
 * left half to go back; use the system back gesture to leave.
 */
@Composable
fun SlideshowScreen(galleryId: String) {
    val viewModel = appViewModel(key = galleryId) {
        SlideshowViewModel(galleryId, it.galleries, it.settings)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FullScreenAndAwake()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val state = uiState
        when {
            state == null -> Unit
            state.slides.isEmpty() -> Text(stringResource(R.string.slideshow_empty), color = Color.White)
            else -> Slideshow(state.slides, state.settings)
        }
    }
}

@Composable
private fun Slideshow(slides: List<EncryptedImage>, settings: Settings) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(slides.size) {
                detectTapGestures { tap ->
                    val step = if (tap.x < size.width / 2) -1 else 1
                    index = (index + step).mod(slides.size)
                }
            },
    ) {
        val screenSize = IntSize(constraints.maxWidth, constraints.maxHeight)

        // The slide timer. Keyed on the index, so it restarts whenever the
        // slide changes for any reason (including a manual skip), and it never
        // waits on the image itself: an animated GIF is cut off mid-loop when
        // its time is up.
        LaunchedEffect(index, settings.slideSeconds, slides.size) {
            if (slides.size > 1) {
                delay(settings.slideSeconds * 1_000L)
                index = (index + 1).mod(slides.size)
            }
        }

        // Decode the upcoming slide ahead of time so the swap is instant.
        LaunchedEffect(index, screenSize, settings.scaleMode) {
            if (slides.size > 1) {
                val next = slides[(index + 1).mod(slides.size)]
                context.imageLoader.enqueue(slideRequest(context, next, screenSize, settings.scaleMode))
            }
        }

        AnimatedContent(
            targetState = index,
            transitionSpec = { settings.transition.toContentTransform() },
            modifier = Modifier.fillMaxSize(),
            label = "slide",
        ) { slideIndex ->
            // Guard against a stale saved index if the gallery shrank meanwhile.
            val slide = slides[slideIndex.coerceIn(slides.indices)]
            AsyncImage(
                model = slideRequest(context, slide, screenSize, settings.scaleMode),
                contentDescription = null,
                contentScale = settings.scaleMode.toContentScale(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The single place where a [SlideTransition] becomes an actual animation.
 * Add new transitions (slides, zooms, ...) as extra branches here.
 */
private fun SlideTransition.toContentTransform(): ContentTransform = when (this) {
    SlideTransition.NONE -> EnterTransition.None togetherWith ExitTransition.None
    SlideTransition.CROSSFADE ->
        fadeIn(tween(CROSSFADE_MILLIS)) togetherWith fadeOut(tween(CROSSFADE_MILLIS))
}

private fun ScaleMode.toContentScale(): ContentScale = when (this) {
    ScaleMode.FIT -> ContentScale.Fit
    ScaleMode.FILL -> ContentScale.Crop
}

/**
 * Builds the request for a slide. Preloading and display share this function
 * so that both produce the same cache key and the preloaded bitmap is reused.
 */
private fun slideRequest(
    context: Context,
    slide: EncryptedImage,
    screenSize: IntSize,
    scaleMode: ScaleMode,
): ImageRequest =
    ImageRequest.Builder(context)
        .data(slide)
        .size(screenSize.width, screenSize.height)
        .scale(if (scaleMode == ScaleMode.FILL) Scale.FILL else Scale.FIT)
        .build()

/** Hides the system bars and stops the display sleeping while on screen. */
@Composable
private fun FullScreenAndAwake() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
