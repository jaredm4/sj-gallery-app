package app.sjgallery.ui.galleries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.model.Gallery
import app.sjgallery.image.EncryptedImage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GalleryListViewModel(private val repository: GalleryRepository) : ViewModel() {

    /** Null while the catalogue is still loading. */
    val galleries: StateFlow<List<Gallery>?> = repository.galleries

    fun coverOf(gallery: Gallery): EncryptedImage =
        EncryptedImage(repository.coverFile(gallery.id), gallery.coverVersion)

    fun createGallery(name: String, onCreated: (galleryId: String) -> Unit) {
        viewModelScope.launch { onCreated(repository.createGallery(name)) }
    }
}
