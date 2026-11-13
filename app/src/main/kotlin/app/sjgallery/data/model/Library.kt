package app.sjgallery.data.model

import kotlinx.serialization.Serializable

/** Root of the persisted (encrypted) catalogue of galleries. */
@Serializable
data class Library(
    val schemaVersion: Int = 1,
    val galleries: List<Gallery> = emptyList(),
)

@Serializable
data class Gallery(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val photos: List<Photo> = emptyList(),
    /** Changes whenever the cover collage is regenerated; used to bust image caches. */
    val coverVersion: Long = 0,
)

@Serializable
data class Photo(
    val id: String,
    /** File name inside the gallery's private directory. */
    val fileName: String,
    /** True for animated GIF/WebP/HEIF, which are stored untouched. */
    val animated: Boolean = false,
)
