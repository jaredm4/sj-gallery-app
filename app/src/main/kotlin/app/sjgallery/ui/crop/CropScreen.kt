package app.sjgallery.ui.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sjgallery.R
import app.sjgallery.data.media.CropRegion
import app.sjgallery.ui.appViewModel

/**
 * Crop editor for a photo that is already in a gallery. A fixed frame in the
 * shape of the device's screen is drawn over the photo; the user drags and
 * pinches the photo underneath to choose what the frame contains. Saving
 * permanently crops the gallery's copy of the photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(galleryId: String, photoId: String, onDone: () -> Unit) {
    val viewModel = appViewModel(key = photoId) {
        CropViewModel(galleryId, photoId, it.galleries, it.displayEdges.portraitAspectRatio)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var portraitFrame by rememberPortraitFrame()
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
                FrameOrientationButton(portrait = portraitFrame, onToggle = { portraitFrame = !portraitFrame })
            }
        }
    }
}

/** Flips the crop frame between the portrait and landscape shape of the screen. */
@Composable
internal fun FrameOrientationButton(portrait: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(stringResource(if (portrait) R.string.frame_portrait else R.string.frame_landscape))
    }
}
