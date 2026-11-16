package app.sjgallery.ui.importreview

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.media.CropRegion
import app.sjgallery.data.media.PickedImage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportReviewUiState(
    val total: Int,
    /** 1-based position of the photo being shown; 0 before the first is ready. */
    val position: Int = 0,
    /** The photo to crop, or null while the next one is loading. */
    val preview: ImageBitmap? = null,
    /** True once there is nothing left to review and the screen should close. */
    val finished: Boolean = false,
)

/**
 * Walks the user through the files they picked, one at a time, so each can be
 * cropped at full resolution before it is stored.
 *
 * Accepted photos are handed to the repository and saved in the background, so
 * the next photo appears without waiting. The following file is always loaded
 * ahead of time for the same reason. Animated images cannot be cropped and are
 * added automatically; unreadable files are skipped.
 */
class ImportReviewViewModel(
    private val galleryId: String,
    private val sources: List<Uri>,
    private val repository: GalleryRepository,
    /** Width / height of the device screen held upright; the crop frame's shape. */
    val screenPortraitAspectRatio: Float,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportReviewUiState(total = sources.size))
    val uiState: StateFlow<ImportReviewUiState> = _uiState.asStateFlow()

    private var index = -1
    private var current: PickedImage? = null
    private var readAhead: Deferred<PickedImage?>? = null

    init {
        showNext()
    }

    /** Adds the [region] of the current photo to the gallery and moves on. */
    fun accept(region: CropRegion) {
        val image = current ?: return
        current = null
        repository.enqueueReviewedImport(galleryId, image, region)
        showNext()
    }

    /** Moves on without adding the current photo. */
    fun skip() {
        if (current == null) return
        current = null
        showNext()
    }

    /**
     * Adds the current photo and every one after it without further review,
     * each cropped to the centred area matching [frameAspectRatio].
     */
    fun acceptRemaining(frameAspectRatio: Float) {
        if (current == null) return
        current = null
        readAhead?.cancel()
        repository.importPhotos(galleryId, sources.drop(index), centerCropAspectRatio = frameAspectRatio)
        _uiState.update { it.copy(preview = null, finished = true) }
    }

    private fun showNext() {
        _uiState.update { it.copy(preview = null) }
        viewModelScope.launch {
            while (true) {
                index++
                if (index >= sources.size) {
                    _uiState.update { it.copy(finished = true) }
                    return@launch
                }
                val image = (readAhead ?: load(index)).await()
                readAhead = if (index + 1 < sources.size) load(index + 1) else null

                when {
                    image == null -> Unit // Not a readable image; try the next file.
                    image.animated -> repository.enqueueReviewedImport(galleryId, image, region = null)
                    else -> {
                        current = image
                        _uiState.update {
                            it.copy(position = index + 1, preview = image.preview.asImageBitmap())
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun load(sourceIndex: Int): Deferred<PickedImage?> =
        viewModelScope.async { repository.openForImport(sources[sourceIndex]) }

    override fun onCleared() {
        // However the review ended, the gallery's cover needs to catch up.
        repository.onImportReviewFinished(galleryId)
    }
}
