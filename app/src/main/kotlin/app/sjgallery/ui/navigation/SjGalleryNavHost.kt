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
import app.sjgallery.ui.crop.CropScreen
import app.sjgallery.ui.galleries.GalleryListScreen
import app.sjgallery.ui.gallery.GalleryDetailScreen
import app.sjgallery.ui.importreview.ImportReviewScreen
import app.sjgallery.ui.settings.SettingsScreen
import app.sjgallery.ui.slideshow.SlideshowScreen

private object Routes {
    const val ARG_GALLERY_ID = "galleryId"
    const val ARG_PHOTO_ID = "photoId"

    const val GALLERIES = "galleries"
    const val SETTINGS = "settings"
    const val GALLERY = "gallery/{$ARG_GALLERY_ID}"
    const val SLIDESHOW = "slideshow/{$ARG_GALLERY_ID}"
    const val CROP = "crop/{$ARG_GALLERY_ID}/{$ARG_PHOTO_ID}"
    const val IMPORT_REVIEW = "import/{$ARG_GALLERY_ID}"

    fun gallery(id: String) = "gallery/$id"
    fun slideshow(id: String) = "slideshow/$id"
    fun crop(galleryId: String, photoId: String) = "crop/$galleryId/$photoId"
    fun importReview(galleryId: String) = "import/$galleryId"
}

private fun NavBackStackEntry.requireArg(name: String): String =
    checkNotNull(arguments?.getString(name)) { "Missing navigation argument: $name" }

private val NavBackStackEntry.galleryId: String
    get() = requireArg(Routes.ARG_GALLERY_ID)

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
                    onCropPhoto = { photoId -> navController.navigate(Routes.crop(entry.galleryId, photoId)) },
                    onReviewImport = { navController.navigate(Routes.importReview(entry.galleryId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SLIDESHOW) { entry ->
                SlideshowScreen(galleryId = entry.galleryId)
            }
            composable(Routes.CROP) { entry ->
                CropScreen(
                    galleryId = entry.galleryId,
                    photoId = entry.requireArg(Routes.ARG_PHOTO_ID),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.IMPORT_REVIEW) { entry ->
                ImportReviewScreen(
                    galleryId = entry.galleryId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
