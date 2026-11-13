package app.sjgallery

import android.app.Application
import app.sjgallery.image.EncryptedImageFetcher
import app.sjgallery.image.EncryptedImageKeyer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder

class SjGalleryApp : Application(), SingletonImageLoader.Factory {

    val container: AppContainer by lazy { AppContainer(this) }

    /** The image loader behind every `AsyncImage` in the app. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(EncryptedImageFetcher.Factory(container.crypto))
                add(EncryptedImageKeyer())
                // Animated GIF / WebP / HEIF, looping forever.
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
