package app.sjgallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.sjgallery.AppContainer
import app.sjgallery.SjGalleryApp

/**
 * Obtains a screen-scoped [ViewModel], constructing it from the app's
 * [AppContainer] the first time it is requested.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as SjGalleryApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
