package app.sjgallery.ui.crop

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.media.CropRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CropUiState(
    /** Null while the photo is still being decoded. */
    val preview: ImageBitmap? = null,
    val saving: Boolean = false,
)

class CropViewModel(
    private val galleryId: String,
    private val photoId: String,
    private val repository: GalleryRepository,
    /** Width / height of the device screen held upright; the crop frame's shape. */
    val screenPortraitAspectRatio: Float,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CropUiState())
    val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val preview = try {
                repository.loadCropPreview(galleryId, photoId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not load photo for cropping", e)
                null
            }
            _uiState.update { it.copy(preview = preview?.asImageBitmap()) }
        }
    }

    /** Replaces the stored photo with [region] of itself, then calls [onFinished]. */
    fun save(region: CropRegion, onFinished: () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repository.cropPhoto(galleryId, photoId, region)
            } catch (e: Exception) {
                // Nothing was replaced; the original stored photo is still intact.
                Log.w(TAG, "Could not crop photo", e)
            }
            onFinished()
        }
    }

    private companion object {
        const val TAG = "CropViewModel"
    }
}
