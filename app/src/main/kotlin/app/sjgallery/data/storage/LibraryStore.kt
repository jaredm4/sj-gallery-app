package app.sjgallery.data.storage

import app.sjgallery.data.crypto.CryptoManager
import app.sjgallery.data.model.Library
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Persists the [Library] as a single encrypted JSON document.
 *
 * The catalogue is tiny (roughly 100 bytes per photo), so rewriting it whole
 * is simpler and safer than a database, and it lets the metadata share the
 * exact same encryption as the photos.
 */
class LibraryStore(
    private val file: File,
    private val crypto: CryptoManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Library {
        val blob = file.readAtomicallyOrNull() ?: return Library()
        return json.decodeFromString(Library.serializer(), crypto.decrypt(blob).decodeToString())
    }

    fun save(library: Library) {
        val plaintext = json.encodeToString(Library.serializer(), library).encodeToByteArray()
        file.writeAtomically(crypto.encrypt(plaintext))
    }
}
