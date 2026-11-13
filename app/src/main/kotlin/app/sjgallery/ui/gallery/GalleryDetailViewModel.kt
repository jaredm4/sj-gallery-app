package app.sjgallery.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.ImportProgress
import app.sjgallery.data.ImportResult
import app.sjgallery.data.model.Gallery
import app.sjgallery.data.model.Photo
import app.sjgallery.image.EncryptedImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryDetailUiState(
    /** Null if the gallery has been deleted. */
    val gallery: Gallery?,
    /** Non-null while photos are being imported. */
    val importProgress: ImportProgress?,
    val selectedPhotoIds: Set<String>,
)

class GalleryDetailViewModel(
    private val galleryId: String,
    private val repository: GalleryRepository,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<String>>(emptySet())

    /** Null until the gallery has been loaded. */
    val uiState: StateFlow<GalleryDetailUiState?> = combine(
        repository.gallery(galleryId),
        repository.imports.map { it[galleryId] },
        selection,
    ) { gallery, importProgress, selected ->
        GalleryDetailUiState(gallery, importProgress, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val importResults: Flow<ImportResult> =
        repository.importResults.filter { it.galleryId == galleryId }

    fun imageOf(photo: Photo): EncryptedImage = EncryptedImage(repository.photoFile(galleryId, photo))

    fun addPhotos(sources: List<Uri>) = repository.importPhotos(galleryId, sources)

    fun toggleSelection(photoId: String) {
        selection.update { if (photoId in it) it - photoId else it + photoId }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun deleteSelectedPhotos() {
        val photoIds = selection.value
        selection.value = emptySet()
        viewModelScope.launch { repository.removePhotos(galleryId, photoIds) }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.renameGallery(galleryId, name) }
    }

    fun deleteGallery(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteGallery(galleryId)
            onDeleted()
        }
    }
}
