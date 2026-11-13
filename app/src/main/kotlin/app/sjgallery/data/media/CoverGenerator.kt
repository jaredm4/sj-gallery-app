package app.sjgallery.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import app.sjgallery.data.crypto.CryptoManager
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds a gallery's cover thumbnail: a square collage of up to four randomly
 * chosen photos, returned already encrypted and ready to write to disk.
 */
class CoverGenerator(private val crypto: CryptoManager) {

    /** Blocking. Returns null when there is nothing to make a cover from. */
    fun generate(photoFiles: List<File>): ByteArray? {
        val picks = photoFiles.shuffled().take(MAX_TILES)
        if (picks.isEmpty()) return null

        val cells = if (picks.size == 1) singleCell() else quadrantCells()
        val cover = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cover)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // With two or three photos the picks repeat to fill all four cells.
        val tiles = picks.map { decodeTile(it, minEdge = cells.first().width()) }
        cells.forEachIndexed { index, cell ->
            tiles[index % tiles.size]?.let { canvas.drawCenterCropped(it, cell, paint) }
        }
        tiles.forEach { it?.recycle() }

        val jpeg = ByteArrayOutputStream()
            .also { cover.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            .toByteArray()
        cover.recycle()
        return crypto.encrypt(jpeg)
    }

    private fun decodeTile(file: File, minEdge: Int): Bitmap? =
        try {
            decodeSoftwareBitmap(crypto.decrypt(file.readBytes())) { decoder, info ->
                decoder.setTargetSampleSize(sampleSizeFor(info.size.width, info.size.height, minEdge))
            }
        } catch (e: Exception) {
            // One unreadable photo should not cost the gallery its cover.
            Log.w(TAG, "Skipping ${file.name} while building cover", e)
            null
        }

    private fun Canvas.drawCenterCropped(bitmap: Bitmap, destination: Rect, paint: Paint) {
        val targetAspect = destination.width().toFloat() / destination.height()
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height
        val (cropWidth, cropHeight) = if (bitmapAspect > targetAspect) {
            (bitmap.height * targetAspect).toInt() to bitmap.height
        } else {
            bitmap.width to (bitmap.width / targetAspect).toInt()
        }
        val left = (bitmap.width - cropWidth) / 2
        val top = (bitmap.height - cropHeight) / 2
        drawBitmap(bitmap, Rect(left, top, left + cropWidth, top + cropHeight), destination, paint)
    }

    private fun singleCell() = listOf(Rect(0, 0, COVER_PX, COVER_PX))

    private fun quadrantCells(): List<Rect> {
        val nearEdge = COVER_PX / 2 - GAP_PX / 2
        val farEdge = COVER_PX / 2 + GAP_PX / 2
        return listOf(
            Rect(0, 0, nearEdge, nearEdge),
            Rect(farEdge, 0, COVER_PX, nearEdge),
            Rect(0, farEdge, nearEdge, COVER_PX),
            Rect(farEdge, farEdge, COVER_PX, COVER_PX),
        )
    }

    private companion object {
        const val TAG = "CoverGenerator"
        const val MAX_TILES = 4
        const val COVER_PX = 640
        const val GAP_PX = 4
        const val JPEG_QUALITY = 85
    }
}
