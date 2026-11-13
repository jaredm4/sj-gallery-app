package app.sjgallery.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.sjgallery.ui.galleries.GalleryListScreen
import app.sjgallery.ui.gallery.GalleryDetailScreen
import app.sjgallery.ui.settings.SettingsScreen
import app.sjgallery.ui.slideshow.SlideshowScreen

private object Routes {
    const val ARG_GALLERY_ID = "galleryId"

    const val GALLERIES = "galleries"
    const val SETTINGS = "settings"
    const val GALLERY = "gallery/{$ARG_GALLERY_ID}"
    const val SLIDESHOW = "slideshow/{$ARG_GALLERY_ID}"

    fun gallery(id: String) = "gallery/$id"
    fun slideshow(id: String) = "slideshow/$id"
}

private val NavBackStackEntry.galleryId: String
    get() = checkNotNull(arguments?.getString(Routes.ARG_GALLERY_ID)) { "Missing gallery id" }

@Composable
fun SjGalleryNavHost() {
    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = Routes.GALLERIES) {
            composable(Routes.GALLERIES) {
                GalleryListScreen(
                    onPlayGallery = { navController.navigate(Routes.slideshow(it)) },
                    onManageGallery = { navController.navigate(Routes.gallery(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.GALLERY) { entry ->
                GalleryDetailScreen(
                    galleryId = entry.galleryId,
                    onPlay = { navController.navigate(Routes.slideshow(entry.galleryId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SLIDESHOW) { entry ->
                SlideshowScreen(galleryId = entry.galleryId)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
