package app.sjgallery.data

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Hands the files chosen in the system picker from the gallery screen to the
 * import review screen. A list of hundreds of URIs is too large to pass as a
 * navigation argument, so it is parked here in memory instead.
 */
class PendingImports {
    private val sourcesByGallery = ConcurrentHashMap<String, List<Uri>>()

    fun put(galleryId: String, sources: List<Uri>) {
        sourcesByGallery[galleryId] = sources
    }

    /** Returns and forgets the files waiting for [galleryId]; empty if there are none. */
    fun take(galleryId: String): List<Uri> = sourcesByGallery.remove(galleryId).orEmpty()
}
