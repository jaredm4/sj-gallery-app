package app.sjgallery.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageScalingTest {

    @Test
    fun `fitWithin leaves small images untouched`() {
        assertEquals(PixelSize(800, 600), fitWithin(800, 600, maxEdge = 2400))
    }

    @Test
    fun `fitWithin scales the longest edge down to the limit`() {
        assertEquals(PixelSize(2400, 1800), fitWithin(4800, 3600, maxEdge = 2400))
        assertEquals(PixelSize(1800, 2400), fitWithin(3600, 4800, maxEdge = 2400))
    }

    @Test
    fun `fitWithin never collapses an edge to zero`() {
        assertEquals(PixelSize(100, 1), fitWithin(10_000, 10, maxEdge = 100))
    }

    @Test
    fun `sampleSizeFor picks the largest power of two that keeps enough pixels`() {
        assertEquals(1, sampleSizeFor(500, 400, minEdge = 320))
        assertEquals(2, sampleSizeFor(1000, 640, minEdge = 320))
        assertEquals(8, sampleSizeFor(4000, 3000, minEdge = 320))
    }

    @Test
    fun `sampleSizeFor handles images smaller than the target`() {
        assertEquals(1, sampleSizeFor(50, 50, minEdge = 320))
    }
}
