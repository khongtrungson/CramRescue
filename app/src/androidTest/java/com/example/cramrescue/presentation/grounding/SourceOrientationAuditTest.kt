package com.example.cramrescue.presentation.grounding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit for Source Grounding: which pixel space are persisted OCR boxes in for a camera/gallery
 * photo carrying EXIF orientation? M5 builds InputImage.fromFilePath and persists image.width /
 * image.height as sourceWidth/HeightPx. This checks that space against the viewer's decoder, so
 * highlights are drawn on an image oriented exactly as ML Kit saw it. No text recognition runs.
 *
 * Fixture: a raw, landscape 300×100 JPEG whose left third is red, then tagged with an EXIF orientation.
 */
@RunWith(AndroidJUnit4::class)
class SourceOrientationAuditTest {

    private lateinit var context: Context
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun taggedJpeg(orientation: Int): File {
        val bitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 0 until 100) for (y in 0 until 100) setPixel(x, y, Color.RED)
        }
        val file = File(context.cacheDir, "orientation-$orientation.jpg").also { files += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun mlKitSize(file: File): Pair<Int, Int> =
        InputImage.fromFilePath(context, Uri.fromFile(file)).let { it.width to it.height }

    private fun viewerSize(file: File): Pair<Int, Int> =
        decodeUprightSampledBitmap(file.absolutePath, 4096)!!.let { it.width to it.height }

    @Test
    fun mlKitAndViewerAgreeOnDimensionsForEveryExifOrientation() {
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to (300 to 100),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to (300 to 100),
            ExifInterface.ORIENTATION_ROTATE_180 to (300 to 100),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to (300 to 100),
            ExifInterface.ORIENTATION_TRANSPOSE to (100 to 300),
            ExifInterface.ORIENTATION_ROTATE_90 to (100 to 300),
            ExifInterface.ORIENTATION_TRANSVERSE to (100 to 300),
            ExifInterface.ORIENTATION_ROTATE_270 to (100 to 300)
        )
        expected.forEach { (orientation, size) ->
            val file = taggedJpeg(orientation)
            assertEquals("ML Kit, orientation $orientation", size, mlKitSize(file))
            assertEquals("viewer, orientation $orientation", size, viewerSize(file))
        }
    }

    @Test
    fun mlKitAndViewerAgreeOnPixelContentForRotations() {
        // Where does the red third end up? Compare ML Kit's bitmap with the viewer's for each rotation.
        listOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        ).forEach { orientation ->
            val file = taggedJpeg(orientation)
            val mlKit = InputImage.fromFilePath(context, Uri.fromFile(file)).bitmapInternal!!
            val viewer = decodeUprightSampledBitmap(file.absolutePath, 4096)!!
            fun isRed(bitmap: Bitmap, x: Int, y: Int) = Color.red(bitmap.getPixel(x, y)) > 200 && Color.green(bitmap.getPixel(x, y)) < 80
            // Probe the centres of the three thirds along the long axis.
            val probes = if (mlKit.width > mlKit.height) {
                listOf(50 to 50, 150 to 50, 250 to 50)
            } else {
                listOf(50 to 50, 50 to 150, 50 to 250)
            }
            probes.forEach { (x, y) ->
                assertEquals("orientation $orientation at ($x,$y)", isRed(mlKit, x, y), isRed(viewer, x, y))
            }
        }
    }
}
