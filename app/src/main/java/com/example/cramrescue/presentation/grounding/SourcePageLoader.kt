package com.example.cramrescue.presentation.grounding

import android.graphics.Bitmap
import com.example.cramrescue.domain.grounding.GroundedSource
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.pdf.PdfPageRenderer
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SourceLoadFailure { FILE_MISSING, PAGE_OUT_OF_RANGE, RENDER_FAILED, DECODE_FAILED, GEOMETRY_MISMATCH }

sealed interface SourceLoadResult {
    data class Loaded(val bitmap: Bitmap) : SourceLoadResult
    data class Failed(val failure: SourceLoadFailure) : SourceLoadResult
}

// Beyond this relative aspect-ratio difference the displayed pixels can't be the OCR pixel space
// (e.g. a rotated/replaced file), so highlights would be wrong: refuse instead of drawing them.
private const val MAX_ASPECT_MISMATCH = 0.02

/**
 * Loads the displayable image for a [GroundedSource], read-only, off the main thread.
 *  - PDF: reuses the M6 [PdfPageRenderer] to render ONLY the requested page (at its OCR
 *    resolution, into a temp file that is deleted right after decoding), then decodes it
 *    downsampled — one bitmap in memory, renderer and file descriptor always released.
 *  - Camera/Gallery: decodes the app-owned file upright (EXIF applied), exactly as ML Kit saw it.
 * The source file itself is never modified, moved or deleted.
 */
class SourcePageLoader(private val pdfPageRenderer: PdfPageRenderer) {

    suspend fun load(source: GroundedSource, maxDimension: Int): SourceLoadResult = withContext(Dispatchers.IO) {
        if (!File(source.localPath).isFile) return@withContext SourceLoadResult.Failed(SourceLoadFailure.FILE_MISSING)

        val bitmap = when (source.sourceType) {
            SourceType.PDF -> {
                val rendered = try {
                    renderPdfPage(source.localPath, source.pageIndex, maxDimension)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext SourceLoadResult.Failed(SourceLoadFailure.RENDER_FAILED)
                }
                rendered ?: return@withContext SourceLoadResult.Failed(SourceLoadFailure.PAGE_OUT_OF_RANGE)
            }
            SourceType.CAMERA, SourceType.GALLERY ->
                decodeUprightSampledBitmap(source.localPath, maxDimension)
                    ?: return@withContext SourceLoadResult.Failed(SourceLoadFailure.DECODE_FAILED)
            // Word has no page image (FR-14: text snippet); it never reaches this image loader.
            SourceType.WORD -> return@withContext SourceLoadResult.Failed(SourceLoadFailure.DECODE_FAILED)
        }

        val sourceAspect = source.sourceWidthPx.toDouble() / source.sourceHeightPx
        val bitmapAspect = bitmap.width.toDouble() / bitmap.height
        if (abs(bitmapAspect - sourceAspect) / sourceAspect > MAX_ASPECT_MISMATCH) {
            bitmap.recycle()
            return@withContext SourceLoadResult.Failed(SourceLoadFailure.GEOMETRY_MISMATCH)
        }
        SourceLoadResult.Loaded(bitmap)
    }

    /** Null if [pageIndex] doesn't exist in the PDF. */
    private suspend fun renderPdfPage(path: String, pageIndex: Int, maxDimension: Int): Bitmap? =
        pdfPageRenderer.withDocument(path) { session ->
            if (pageIndex !in 0 until session.pageCount) return@withDocument null
            val page = session.renderPage(pageIndex)
            try {
                decodeUprightSampledBitmap(page.imagePath, maxDimension)
            } finally {
                session.deleteRenderedPage(page)
            }
        }
}
