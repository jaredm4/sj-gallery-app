package app.sjgallery.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.settings.Settings
import app.sjgallery.data.settings.SettingsRepository
import app.sjgallery.image.EncryptedImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SlideshowUiState(
    /** The gallery's photos in this session's play order. */
    val slides: List<EncryptedImage>,
    val settings: Settings,
)

class SlideshowViewModel(
    galleryId: String,
    repository: GalleryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val slides = MutableStateFlow<List<EncryptedImage>?>(null)

    /** Null until both the gallery and the settings have loaded. */
    val uiState: StateFlow<SlideshowUiState?> =
        combine(slides.filterNotNull(), settingsRepository.settings) { slides, settings ->
            SlideshowUiState(slides, settings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    init {
        // Shuffle exactly once per launch. A new view model is created every
        // time a slideshow is opened, so each viewing gets a fresh order, while
        // the order stays stable across loops and screen rotations.
        viewModelScope.launch {
            val photos = repository.gallery(galleryId).first()?.photos.orEmpty()
            slides.value = photos.shuffled().map { EncryptedImage(repository.photoFile(galleryId, it)) }
        }
    }
}
