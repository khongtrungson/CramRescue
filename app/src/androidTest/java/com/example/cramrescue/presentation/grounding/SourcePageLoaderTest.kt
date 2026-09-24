package com.example.cramrescue.presentation.grounding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.pdf.AndroidPdfPageRenderer
import com.example.cramrescue.domain.grounding.GroundedBlock
import com.example.cramrescue.domain.grounding.GroundedSource
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.pdf.PdfDocumentSession
import com.example.cramrescue.domain.pdf.PdfPageRenderer
import com.example.cramrescue.domain.pdf.RenderedPage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Records which pages are rendered, and on which thread, around the real M6 renderer. */
private class RecordingRenderer(private val delegate: PdfPageRenderer) : PdfPageRenderer {
    val renderedPages = mutableListOf<Int>()
    var renderedOnMainThread = false
    var lastRendered: RenderedPage? = null
    override suspend fun <T> withDocument(localPath: String, block: suspend (PdfDocumentSession) -> T): T =
        delegate.withDocument(localPath) { session ->
            block(object : PdfDocumentSession {
                override val pageCount: Int get() = session.pageCount
                override suspend fun renderPage(pageIndex: Int): RenderedPage {
                    renderedPages += pageIndex
                    renderedOnMainThread = renderedOnMainThread || Looper.myLooper() == Looper.getMainLooper()
                    return session.renderPage(pageIndex).also { lastRendered = it }
                }
                override fun deleteRenderedPage(page: RenderedPage) = session.deleteRenderedPage(page)
            })
        }
}

@RunWith(AndroidJUnit4::class)
class SourcePageLoaderTest {

    private lateinit var context: Context
    private lateinit var renderer: RecordingRenderer
    private lateinit var loader: SourcePageLoader
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        renderer = RecordingRenderer(AndroidPdfPageRenderer(MaterialFileStore(context)))
        loader = SourcePageLoader(renderer)
    }

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    /** A 3-page A4-ish PDF (595×842 pt); page i has a black square at a page-specific x. */
    private fun threePagePdf(): File {
        val pdf = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK }
        repeat(3) { i ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            val left = 50f + i * 200f
            page.canvas.drawRect(left, 100f, left + 100f, 200f, paint)
            pdf.finishPage(page)
        }
        return File(context.cacheDir, "grounding-test.pdf").also { file ->
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            files += file
        }
    }

    private fun source(path: String, type: SourceType, page: Int, width: Int, height: Int) = GroundedSource(
        campaignId = 1, documentId = 1, displayName = "t", sourceType = type, localPath = path,
        pageIndex = page, pageCount = 3, sourceWidthPx = width, sourceHeightPx = height,
        blocks = listOf(GroundedBlock(0, "x", BoundingBox(0, 0, 10, 10))), missingBlockCount = 0, cardStale = false
    )

    /** Fraction along the width where the page's black square is centred. */
    private fun darkSquareCenterX(bitmap: Bitmap): Float {
        val y = (bitmap.height * 150f / 842f).toInt()
        val dark = (0 until bitmap.width).filter { x -> Color.red(bitmap.getPixel(x, y)) < 80 }
        return (dark.first() + dark.last()) / 2f / bitmap.width
    }

    @Test
    fun rendersOnlyTheRequestedPageOffTheMainThreadAndReleasesItsTempFile() = runBlocking {
        val pdf = threePagePdf()
        // Source dims = the M6 OCR render size of an A4 page (longest side capped at 2000).
        val ocrWidth = (595 * (2000f / 842f)).toInt()
        listOf(0, 2).forEach { page ->
            renderer.renderedPages.clear()
            val result = loader.load(source(pdf.absolutePath, SourceType.PDF, page, ocrWidth, 2000), maxDimension = 600)
            val bitmap = (result as SourceLoadResult.Loaded).bitmap

            assertEquals(listOf(page), renderer.renderedPages) // exactly the requested page, once
            // The viewer bitmap is smaller than the OCR render, and still the right page:
            assertTrue(bitmap.height < 2000)
            val expectedCenter = (100f + page * 200f) / 595f
            assertEquals(expectedCenter, darkSquareCenterX(bitmap), 0.02f)
            assertFalse(File(renderer.lastRendered!!.imagePath).exists()) // temp render deleted
        }
        assertFalse(renderer.renderedOnMainThread)
    }

    @Test
    fun invalidPageAndBrokenPdfAreControlledFailures() = runBlocking {
        val pdf = threePagePdf()
        assertEquals(
            SourceLoadResult.Failed(SourceLoadFailure.PAGE_OUT_OF_RANGE),
            loader.load(source(pdf.absolutePath, SourceType.PDF, 7, 1413, 2000), 600)
        )
        val broken = File(context.cacheDir, "broken.pdf").apply { writeText("not a pdf") }.also { files += it }
        assertEquals(
            SourceLoadResult.Failed(SourceLoadFailure.RENDER_FAILED),
            loader.load(source(broken.absolutePath, SourceType.PDF, 0, 1413, 2000), 600)
        )
    }

    private fun jpeg(name: String, width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val file = File(context.cacheDir, name).also { files += it }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            .let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    @Test
    fun cameraAndGalleryImagesLoadUprightInTheOcrPixelSpaceWithoutBeingModified() = runBlocking {
        // A raw 1200×800 photo tagged "rotate 90": ML Kit (and the persisted dims) see 800×1200.
        val photo = jpeg("camera.jpg", 1200, 800, ExifInterface.ORIENTATION_ROTATE_90)
        val before = photo.length() to photo.lastModified()

        listOf(SourceType.CAMERA, SourceType.GALLERY).forEach { type ->
            val bitmap = (loader.load(source(photo.absolutePath, type, 0, 800, 1200), 600) as SourceLoadResult.Loaded).bitmap
            assertTrue("portrait after EXIF", bitmap.height > bitmap.width)
            assertEquals(800f / 1200f, bitmap.width.toFloat() / bitmap.height, 0.01f)
        }
        assertEquals("source file untouched", before, photo.length() to photo.lastModified())

        // Different aspect ratios load fine when they match their OCR dims…
        val wide = jpeg("wide.jpg", 1600, 400)
        assertTrue(loader.load(source(wide.absolutePath, SourceType.GALLERY, 0, 1600, 400), 600) is SourceLoadResult.Loaded)
        // …but pixels that can't be the OCR space (e.g. dims swapped) are refused, not mis-highlighted.
        assertEquals(
            SourceLoadResult.Failed(SourceLoadFailure.GEOMETRY_MISMATCH),
            loader.load(source(wide.absolutePath, SourceType.GALLERY, 0, 400, 1600), 600)
        )
    }

    @Test
    fun missingOrUndecodableFilesAreControlledFailures() = runBlocking {
        assertEquals(
            SourceLoadResult.Failed(SourceLoadFailure.FILE_MISSING),
            loader.load(source("/data/nonexistent/photo.jpg", SourceType.CAMERA, 0, 800, 600), 600)
        )
        val junk = File(context.cacheDir, "junk.jpg").apply { writeText("not an image") }.also { files += it }
        assertEquals(
            SourceLoadResult.Failed(SourceLoadFailure.DECODE_FAILED),
            loader.load(source(junk.absolutePath, SourceType.GALLERY, 0, 800, 600), 600)
        )
    }
}
