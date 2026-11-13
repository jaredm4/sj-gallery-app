package app.sjgallery.data

import android.net.Uri
import android.util.Log
import app.sjgallery.data.media.CoverGenerator
import app.sjgallery.data.media.PhotoImporter
import app.sjgallery.data.model.Gallery
import app.sjgallery.data.model.Library
import app.sjgallery.data.model.Photo
import app.sjgallery.data.settings.SettingsRepository
import app.sjgallery.data.storage.LibraryStore
import app.sjgallery.data.storage.writeAtomically
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Live progress of a photo import into one gallery. */
data class ImportProgress(val completed: Int, val total: Int)

/** Emitted once when an import finishes. */
data class ImportResult(val galleryId: String, val imported: Int, val failed: Int)

/**
 * Single source of truth for galleries and their photos.
 *
 * On-disk layout under [galleriesDir] (every file is encrypted):
 * ```
 * <galleryId>/<photoId>.bin   one per photo
 * <galleryId>/cover.bin       collage thumbnail
 * ```
 * The catalogue itself (names, ordering, which photos exist) lives in [store].
 *
 * @param scope outlives any screen, so imports continue if the user navigates away.
 */
class GalleryRepository(
    private val galleriesDir: File,
    private val store: LibraryStore,
    private val importer: PhotoImporter,
    private val coverGenerator: CoverGenerator,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Serialises every read-modify-write of the catalogue. */
    private val mutex = Mutex()

    private val _galleries = MutableStateFlow<List<Gallery>?>(null)

    /** All galleries, oldest first; null until the catalogue has been loaded from disk. */
    val galleries: StateFlow<List<Gallery>?> = _galleries.asStateFlow()

    private val _imports = MutableStateFlow<Map<String, ImportProgress>>(emptyMap())

    /** Imports currently running, keyed by gallery id. */
    val imports: StateFlow<Map<String, ImportProgress>> = _imports.asStateFlow()

    private val _importResults = MutableSharedFlow<ImportResult>(extraBufferCapacity = 8)
    val importResults: SharedFlow<ImportResult> = _importResults.asSharedFlow()

    init {
        // Trigger the initial load.
        scope.launch { mutate { it } }
    }

    fun gallery(id: String): Flow<Gallery?> =
        galleries.filterNotNull()
            .map { all -> all.firstOrNull { it.id == id } }
            .distinctUntilChanged()

    fun photoFile(galleryId: String, photo: Photo): File = File(galleryDir(galleryId), photo.fileName)

    fun coverFile(galleryId: String): File = File(galleryDir(galleryId), COVER_FILE_NAME)

    /** @return the new gallery's id. */
    suspend fun createGallery(name: String): String {
        val gallery = Gallery(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAtMillis = System.currentTimeMillis(),
        )
        mutate { it + gallery }
        return gallery.id
    }

    suspend fun renameGallery(id: String, name: String) {
        mutateGallery(id) { it.copy(name = name.trim()) }
    }

    suspend fun deleteGallery(id: String) {
        // NonCancellable: never stop halfway and leave orphaned files behind
        // just because the screen that asked for this was closed.
        withContext(NonCancellable) {
            mutate { all -> all.filterNot { it.id == id } }
            withContext(ioDispatcher) { galleryDir(id).deleteRecursively() }
        }
    }

    suspend fun removePhotos(galleryId: String, photoIds: Set<String>) {
        withContext(NonCancellable) {
            var removed = emptyList<Photo>()
            mutateGallery(galleryId) { gallery ->
                val (drop, keep) = gallery.photos.partition { it.id in photoIds }
                removed = drop
                gallery.copy(photos = keep)
            }
            withContext(ioDispatcher) {
                removed.forEach { photoFile(galleryId, it).delete() }
            }
            refreshCover(galleryId)
        }
    }

    /**
     * Copies the picked images into the gallery in the background. Progress is
     * published through [imports] and the outcome through [importResults].
     * Photos are committed in batches so that an interrupted import keeps
     * everything finished so far.
     */
    fun importPhotos(galleryId: String, sources: List<Uri>) {
        if (sources.isEmpty()) return
        scope.launch(ioDispatcher) {
            val optimize = settings.settings.first().optimizeOnImport
            val directory = galleryDir(galleryId)
            var completed = 0
            var imported = 0
            _imports.update { it + (galleryId to ImportProgress(0, sources.size)) }
            try {
                for (batch in sources.chunked(IMPORT_BATCH_SIZE)) {
                    val photos = batch.mapNotNull { source ->
                        val photo = try {
                            importer.import(source, directory, optimize)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not import $source", e)
                            null
                        }
                        completed++
                        _imports.update { it + (galleryId to ImportProgress(completed, sources.size)) }
                        photo
                    }
                    if (!appendPhotos(galleryId, photos)) {
                        // The gallery was deleted mid-import; discard what was written.
                        directory.deleteRecursively()
                        return@launch
                    }
                    imported += photos.size
                }
                refreshCover(galleryId)
                _importResults.emit(ImportResult(galleryId, imported, failed = sources.size - imported))
            } finally {
                _imports.update { it - galleryId }
            }
        }
    }

    /** @return false if the gallery no longer exists. */
    private suspend fun appendPhotos(galleryId: String, photos: List<Photo>): Boolean {
        var found = false
        mutateGallery(galleryId) { gallery ->
            found = true
            gallery.copy(photos = gallery.photos + photos)
        }
        return found
    }

    /** Regenerates the collage thumbnail from the gallery's current photos. */
    private suspend fun refreshCover(galleryId: String) = withContext(ioDispatcher) {
        val gallery = _galleries.value?.firstOrNull { it.id == galleryId } ?: return@withContext
        val cover = coverGenerator.generate(gallery.photos.map { photoFile(galleryId, it) })
        val file = coverFile(galleryId)
        if (cover == null) file.delete() else file.writeAtomically(cover)
        mutateGallery(galleryId) { it.copy(coverVersion = System.currentTimeMillis()) }
    }

    private suspend fun mutateGallery(id: String, transform: (Gallery) -> Gallery) {
        mutate { all -> all.map { if (it.id == id) transform(it) else it } }
    }

    /** Applies [transform] to the catalogue, persists the result, then publishes it. */
    private suspend fun mutate(transform: (List<Gallery>) -> List<Gallery>) {
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = _galleries.value ?: store.load().galleries
                val updated = transform(current)
                if (updated != current) store.save(Library(galleries = updated))
                _galleries.value = updated
            }
        }
    }

    private fun galleryDir(galleryId: String): File = File(galleriesDir, galleryId)

    private companion object {
        const val TAG = "GalleryRepository"
        const val COVER_FILE_NAME = "cover.bin"
        const val IMPORT_BATCH_SIZE = 20
    }
}
