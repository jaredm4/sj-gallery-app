package app.sjgallery.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sjgallery.data.settings.ScaleMode
import app.sjgallery.data.settings.Settings
import app.sjgallery.data.settings.SettingsRepository
import app.sjgallery.data.settings.SlideTransition
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /** Null until the stored settings have been read. */
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun setSlideSeconds(seconds: Int) {
        viewModelScope.launch { repository.setSlideSeconds(seconds) }
    }

    fun setScaleMode(mode: ScaleMode) {
        viewModelScope.launch { repository.setScaleMode(mode) }
    }

    fun setTransition(transition: SlideTransition) {
        viewModelScope.launch { repository.setTransition(transition) }
    }

    fun setOptimizeOnImport(enabled: Boolean) {
        viewModelScope.launch { repository.setOptimizeOnImport(enabled) }
    }
}
