package app.sjgallery

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.crypto.CryptoManager
import app.sjgallery.data.media.CoverGenerator
import app.sjgallery.data.media.PhotoImporter
import app.sjgallery.data.settings.SettingsRepository
import app.sjgallery.data.settings.settingsDataStore
import app.sjgallery.data.storage.LibraryStore
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container. The object graph is small enough that a
 * DI framework would add more ceremony than it removes.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Lives as long as the process; used for work that must outlive a screen. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val crypto = CryptoManager(wrappedKeyFile = File(appContext.filesDir, "keys/data.key"))

    val settings = SettingsRepository(appContext.settingsDataStore)

    val galleries = GalleryRepository(
        galleriesDir = File(appContext.filesDir, "galleries"),
        store = LibraryStore(File(appContext.filesDir, "library.bin"), crypto),
        importer = PhotoImporter(
            contentResolver = appContext.contentResolver,
            crypto = crypto,
            maxEdgePx = longestDisplayEdgePx(),
        ),
        coverGenerator = CoverGenerator(crypto),
        settings = settings,
        scope = applicationScope,
    )

    private fun longestDisplayEdgePx(): Int {
        val display = appContext.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode ?: return FALLBACK_MAX_EDGE_PX
        return max(mode.physicalWidth, mode.physicalHeight)
    }

    private companion object {
        const val FALLBACK_MAX_EDGE_PX = 2560
    }
}
