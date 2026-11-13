package app.sjgallery.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sjgallery.R
import app.sjgallery.data.ImportProgress
import app.sjgallery.data.model.Gallery
import app.sjgallery.data.model.Photo
import app.sjgallery.image.EncryptedImage
import app.sjgallery.ui.appViewModel
import app.sjgallery.ui.components.ConfirmDeleteDialog
import app.sjgallery.ui.components.EmptyState
import app.sjgallery.ui.components.GalleryNameDialog
import coil3.compose.AsyncImage

private enum class DetailDialog { RENAME, DELETE_GALLERY, DELETE_PHOTOS }

/**
 * Manage one gallery: add photos, select and remove photos, rename or delete
 * the gallery. Tapping a photo toggles its selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDetailScreen(
    galleryId: String,
    onPlay: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = appViewModel(key = galleryId) { GalleryDetailViewModel(galleryId, it.galleries) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState ?: return
    val gallery = state.gallery ?: return // Deleted; navigation is already on its way back.

    val selectedCount = state.selectedPhotoIds.size
    val selecting = selectedCount > 0
    val importing = state.importProgress != null
    var dialog by rememberSaveable { mutableStateOf<DetailDialog?>(null) }

    // The system file picker, rather than the photo picker, so any number of
    // images can be chosen at once from any storage provider.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addPhotos(uris)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.importResults.collect { result ->
            if (result.failed > 0) {
                snackbarHostState.showSnackbar(
                    resources.getQuantityString(R.plurals.import_failed, result.failed, result.failed),
                )
            }
        }
    }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected_count, selectedCount, selectedCount)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { dialog = DetailDialog.DELETE_PHOTOS }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(gallery.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (gallery.photos.isNotEmpty()) {
                            IconButton(onClick = onPlay) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_slideshow))
                            }
                        }
                        IconButton(onClick = { dialog = DetailDialog.RENAME }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_gallery))
                        }
                        IconButton(onClick = { dialog = DetailDialog.DELETE_GALLERY }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_gallery))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting && !importing) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_photos)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            state.importProgress?.let { ImportBanner(it) }

            if (gallery.photos.isEmpty() && !importing) {
                EmptyState(stringResource(R.string.no_photos))
            } else {
                PhotoGrid(
                    gallery = gallery,
                    selectedPhotoIds = state.selectedPhotoIds,
                    imageOf = viewModel::imageOf,
                    onToggle = viewModel::toggleSelection,
                )
            }
        }
    }

    when (dialog) {
        DetailDialog.RENAME -> GalleryNameDialog(
            title = stringResource(R.string.rename_gallery),
            confirmLabel = stringResource(R.string.save),
            initialName = gallery.name,
            onConfirm = { name ->
                dialog = null
                viewModel.rename(name)
            },
            onDismiss = { dialog = null },
        )
        DetailDialog.DELETE_GALLERY -> ConfirmDeleteDialog(
            title = stringResource(R.string.delete_gallery),
            message = stringResource(R.string.delete_gallery_message, gallery.name),
            onConfirm = {
                dialog = null
                viewModel.deleteGallery(onDeleted = onBack)
            },
            onDismiss = { dialog = null },
        )
        DetailDialog.DELETE_PHOTOS -> ConfirmDeleteDialog(
            title = stringResource(R.string.delete_selected),
            message = pluralStringResource(R.plurals.delete_photos_message, selectedCount, selectedCount),
            onConfirm = {
                dialog = null
                viewModel.deleteSelectedPhotos()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun ImportBanner(progress: ImportProgress) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.importing_progress, progress.completed, progress.total),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { progress.completed.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun PhotoGrid(
    gallery: Gallery,
    selectedPhotoIds: Set<String>,
    imageOf: (Photo) -> EncryptedImage,
    onToggle: (photoId: String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(gallery.photos, key = { it.id }) { photo ->
            PhotoTile(
                image = imageOf(photo),
                selected = photo.id in selectedPhotoIds,
                onClick = { onToggle(photo.id) },
            )
        }
    }
}

@Composable
private fun PhotoTile(image: EncryptedImage, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = Color.White,
                )
            }
        }
    }
}
