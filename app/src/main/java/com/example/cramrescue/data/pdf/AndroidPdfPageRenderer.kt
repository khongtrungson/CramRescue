package com.example.cramrescue.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.domain.pdf.PdfDocumentSession
import com.example.cramrescue.domain.pdf.PdfPageRenderer
import com.example.cramrescue.domain.pdf.RenderedPage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Render resolution: PdfRenderer reports page size in PostScript points (1/72"). Rendering at
// ~200 DPI keeps body text comfortably legible for ML Kit without producing huge bitmaps, and
// the longest side is capped so a large-format page can't blow up memory. Pages smaller than
// their natural size are never produced (scale is never below 1x).
private const val TARGET_DPI = 200f
private const val POINTS_PER_INCH = 72f
private const val MAX_RENDER_DIMENSION_PX = 2000
private const val JPEG_QUALITY = 90

/**
 * [PdfPageRenderer] backed by the Android platform's [PdfRenderer] — no third-party PDF library.
 *
 * Each page is rendered to a bitmap, written to a temporary JPEG, and the bitmap is recycled
 * immediately, so only one page's pixels are ever in memory. Writing a temp file (rather than
 * handing a Bitmap upward) is what lets PDF pages reuse the existing file-path-based OCR engine
 * unchanged, and keeps `android.graphics` out of the domain layer.
 */
class AndroidPdfPageRenderer(
    private val materialFileStore: MaterialFileStore
) : PdfPageRenderer {

    override suspend fun <T> withDocument(
        localPath: String,
        block: suspend (PdfDocumentSession) -> T
    ): T = withContext(Dispatchers.IO) {
        val descriptor = ParcelFileDescriptor.open(File(localPath), ParcelFileDescriptor.MODE_READ_ONLY)
        // PdfRenderer throws for malformed or password-protected PDFs; the caller maps that to a
        // user-facing error. Both handles are always released, including on failure.
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }

        try {
            block(AndroidPdfDocumentSession(renderer, materialFileStore))
        } finally {
            renderer.close()
            descriptor.close()
        }
    }
}

private class AndroidPdfDocumentSession(
    private val renderer: PdfRenderer,
    private val materialFileStore: MaterialFileStore
) : PdfDocumentSession {

    override val pageCount: Int get() = renderer.pageCount

    override suspend fun renderPage(pageIndex: Int): RenderedPage = withContext(Dispatchers.IO) {
        renderer.openPage(pageIndex).use { page ->
            val scale = renderScaleFor(page.width, page.height)
            val widthPx = (page.width * scale).toInt().coerceAtLeast(1)
            val heightPx = (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val destination = materialFileStore.createRenderedPageFile()
            try {
                // PdfRenderer composites onto whatever is already in the bitmap, and a fresh
                // bitmap is transparent — which OCRs terribly. Start from white paper.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                destination.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
            } finally {
                bitmap.recycle()
            }

            RenderedPage(
                pageIndex = pageIndex,
                imagePath = destination.absolutePath,
                widthPx = widthPx,
                heightPx = heightPx
            )
        }
    }

    override fun deleteRenderedPage(page: RenderedPage) {
        runCatching { File(page.imagePath).delete() }
    }

    private fun renderScaleFor(pageWidthPoints: Int, pageHeightPoints: Int): Float {
        val dpiScale = TARGET_DPI / POINTS_PER_INCH
        val longestSidePoints = maxOf(pageWidthPoints, pageHeightPoints).coerceAtLeast(1)
        val maxScale = MAX_RENDER_DIMENSION_PX.toFloat() / longestSidePoints
        return dpiScale.coerceAtMost(maxScale).coerceAtLeast(1f)
    }
}
