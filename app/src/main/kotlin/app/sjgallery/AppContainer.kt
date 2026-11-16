package app.sjgallery

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import app.sjgallery.data.GalleryRepository
import app.sjgallery.data.PendingImports
import app.sjgallery.data.crypto.CryptoManager
import app.sjgallery.data.media.CoverGenerator
import app.sjgallery.data.media.PhotoCropper
import app.sjgallery.data.media.PhotoImporter
import app.sjgallery.data.settings.SettingsRepository
import app.sjgallery.data.settings.settingsDataStore
import app.sjgallery.data.storage.LibraryStore
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The physical resolution of the device's built-in display. */
data class DisplayEdges(val longPx: Int, val shortPx: Int) {
    /** Width divided by height when the device is held upright. */
    val portraitAspectRatio: Float get() = shortPx.toFloat() / longPx
}

/**
 * Hand-rolled dependency container. The object graph is small enough that a
 * DI framework would add more ceremony than it removes.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Lives as long as the process; used for work that must outlive a screen. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val displayEdges: DisplayEdges = readDisplayEdges()

    val crypto = CryptoManager(wrappedKeyFile = File(appContext.filesDir, "keys/data.key"))

    val settings = SettingsRepository(appContext.settingsDataStore)

    val pendingImports = PendingImports()

    val galleries = GalleryRepository(
        galleriesDir = File(appContext.filesDir, "galleries"),
        store = LibraryStore(File(appContext.filesDir, "library.bin"), crypto),
        importer = PhotoImporter(
            contentResolver = appContext.contentResolver,
            crypto = crypto,
            maxEdgePx = displayEdges.longPx,
        ),
        cropper = PhotoCropper(crypto = crypto, maxEdgePx = displayEdges.longPx),
        coverGenerator = CoverGenerator(crypto),
        settings = settings,
        scope = applicationScope,
    )

    private fun readDisplayEdges(): DisplayEdges {
        val mode = appContext.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
            ?.mode
            ?: return FALLBACK_DISPLAY
        return DisplayEdges(
            longPx = max(mode.physicalWidth, mode.physicalHeight),
            shortPx = min(mode.physicalWidth, mode.physicalHeight),
        )
    }

    private companion object {
        val FALLBACK_DISPLAY = DisplayEdges(longPx = 2560, shortPx = 1440)
    }
}
