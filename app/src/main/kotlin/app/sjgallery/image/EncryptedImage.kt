package app.sjgallery.image

import app.sjgallery.data.crypto.CryptoManager
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import okio.Buffer

/**
 * Coil model for an image stored encrypted on disk. Pass one as the `model`
 * of an `AsyncImage` and the app's image loader takes care of the rest.
 *
 * @param version bump when the file's contents change in place (e.g. a
 *   regenerated cover) so cached copies are not reused.
 */
data class EncryptedImage(val file: File, val version: Long = 0)

/**
 * Teaches Coil to load an [EncryptedImage]: the file is decrypted straight
 * into memory and handed to the regular decoders, so plaintext never touches
 * disk. Runs on Coil's background fetcher dispatcher.
 */
class EncryptedImageFetcher(
    private val image: EncryptedImage,
    private val options: Options,
    private val crypto: CryptoManager,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val plaintext = crypto.decrypt(image.file.readBytes())
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(plaintext),
                fileSystem = options.fileSystem,
            ),
            mimeType = null, // Decoders sniff the format from the bytes.
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val crypto: CryptoManager) : Fetcher.Factory<EncryptedImage> {
        override fun create(data: EncryptedImage, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedImageFetcher(data, options, crypto)
    }
}

/** Memory-cache identity for an [EncryptedImage]. */
class EncryptedImageKeyer : Keyer<EncryptedImage> {
    override fun key(data: EncryptedImage, options: Options): String =
        "${data.file.path}#${data.version}"
}
