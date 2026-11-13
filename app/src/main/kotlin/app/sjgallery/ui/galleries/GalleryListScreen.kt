package app.sjgallery.ui.galleries

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sjgallery.R
import app.sjgallery.data.model.Gallery
import app.sjgallery.image.EncryptedImage
import app.sjgallery.ui.appViewModel
import app.sjgallery.ui.components.EmptyState
import app.sjgallery.ui.components.GalleryNameDialog
import coil3.compose.AsyncImage

/** Home screen: a grid of galleries. Tapping one starts its slideshow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryListScreen(
    onPlayGallery: (galleryId: String) -> Unit,
    onManageGallery: (galleryId: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = appViewModel { GalleryListViewModel(it.galleries) }
    val galleries by viewModel.galleries.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_gallery)) },
            )
        },
    ) { innerPadding ->
        val loaded = galleries
        when {
            loaded == null -> Unit // Loading is near-instant; avoid flashing a spinner.
            loaded.isEmpty() -> EmptyState(
                message = stringResource(R.string.no_galleries),
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                // Extra bottom space so the last row can scroll clear of the button.
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(loaded, key = { it.id }) { gallery ->
                    GalleryCard(
                        gallery = gallery,
                        cover = viewModel.coverOf(gallery),
                        // An empty gallery has nothing to play, so go add photos instead.
                        onClick = {
                            if (gallery.photos.isEmpty()) onManageGallery(gallery.id) else onPlayGallery(gallery.id)
                        },
                        onManage = { onManageGallery(gallery.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        GalleryNameDialog(
            title = stringResource(R.string.new_gallery),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createGallery(name, onCreated = onManageGallery)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun GalleryCard(
    gallery: Gallery,
    cover: EncryptedImage,
    onClick: () -> Unit,
    onManage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (gallery.photos.isNotEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            FilledTonalIconButton(
                onClick = onManage,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.manage_gallery))
            }
        }
        Text(
            text = gallery.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.photo_count, gallery.photos.size, gallery.photos.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}
