package app.sjgallery.ui.importreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.sjgallery.ui.crop.CropCanvas
import app.sjgallery.ui.crop.FrameOrientationButton
import app.sjgallery.ui.crop.rememberPortraitFrame

/**
 * Crop-as-you-import. Each picked photo is shown behind a frame in the shape
 * of the device's screen, starting with the centred "fill" crop. The user can
 * reposition it, then add it, skip it, or add everything that is left as-is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewScreen(galleryId: String, onDone: () -> Unit) {
    val viewModel = appViewModel(key = "import-review") {
        ImportReviewViewModel(
            galleryId = galleryId,
            sources = it.pendingImports.take(galleryId),
            repository = it.galleries,
            screenPortraitAspectRatio = it.displayEdges.portraitAspectRatio,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) currentOnDone()
    }

    var portraitFrame by rememberPortraitFrame()
    val frameAspectRatio =
        if (portraitFrame) viewModel.screenPortraitAspectRatio else 1f / viewModel.screenPortraitAspectRatio

    // Only read when a button is tapped, so updating it never recomposes this screen.
    var region by remember { mutableStateOf(CropRegion.Full) }

    val preview = uiState.preview
    val ready = preview != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.position > 0) {
                        Text(stringResource(R.string.import_review_title, uiState.position, uiState.total))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.stop_importing))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    CropCanvas(
                        image = preview,
                        frameAspectRatio = frameAspectRatio,
                        onRegionChange = { region = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (!uiState.finished) {
                    CircularProgressIndicator()
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
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = viewModel::skip, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.skip_photo))
                }
                Button(onClick = { viewModel.accept(region) }, enabled = ready, modifier = Modifier.weight(2f)) {
                    Text(stringResource(R.string.add_photo))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrameOrientationButton(portrait = portraitFrame, onToggle = { portraitFrame = !portraitFrame })
                TextButton(onClick = { viewModel.acceptRemaining(frameAspectRatio) }, enabled = ready) {
                    Text(stringResource(R.string.add_remaining))
                }
            }
        }
    }
}
