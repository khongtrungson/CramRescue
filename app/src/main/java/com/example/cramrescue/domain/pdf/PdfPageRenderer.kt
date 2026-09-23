package com.example.cramrescue.domain.pdf

/**
 * A PDF page rendered to a temporary local image.
 *
 * [imagePath] is only valid until [PdfDocumentSession.deleteRenderedPage] is called for it.
 * [widthPx]/[heightPx] are the rendered bitmap's dimensions — the pixel space any OCR bounding
 * box taken from this page will be relative to.
 */
data class RenderedPage(
    val pageIndex: Int,
    val imagePath: String,
    val widthPx: Int,
    val heightPx: Int
)

/** An open PDF. Pages are rendered one at a time so only one page's memory is held. */
interface PdfDocumentSession {
    val pageCount: Int

    /** Renders [pageIndex] (0-based) to a temporary image file. */
    suspend fun renderPage(pageIndex: Int): RenderedPage

    /** Deletes the temporary file backing [page]. Safe to call more than once. */
    fun deleteRenderedPage(page: RenderedPage)
}

/**
 * Local PDF rendering boundary. Kept separate from text recognition so orchestration can be
 * tested with a fake renderer, and so no PDF/Android type leaks into the domain.
 */
interface PdfPageRenderer {
    /**
     * Opens the PDF at [localPath], runs [block], and always releases the underlying file and
     * renderer handles. Throws if the PDF cannot be opened (malformed, encrypted, unreadable).
     */
    suspend fun <T> withDocument(localPath: String, block: suspend (PdfDocumentSession) -> T): T
}
