package com.example.cramrescue.data.word

import android.util.Xml
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.word.ExtractedTextBlock
import com.example.cramrescue.domain.word.WordExtractionFailure
import com.example.cramrescue.domain.word.WordExtractionResult
import com.example.cramrescue.domain.word.WordTextExtractor
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val MARKUP_COMPATIBILITY = "http://schemas.openxmlformats.org/markup-compatibility/2006"
private const val DEFAULT_MAIN_PART = "word/document.xml"

// Safety limits for untrusted input (decompression bombs, absurd packages).
private const val MAX_ZIP_ENTRIES = 5_000
private const val MAX_MAIN_PART_BYTES = 30L * 1024 * 1024
private const val MAX_RELS_BYTES = 1L * 1024 * 1024

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())
private val WHITESPACE = Regex("\\s+")
private val HEADING_STYLE = Regex("(?i)^(heading\\d*|title|subtitle)$")

/** Elements whose content is not body reading text (drawings, text boxes) or would duplicate it. */
private val SKIPPED_W_ELEMENTS = setOf("txbxContent", "drawing", "pict", "object")

/**
 * Local .docx (Office Open XML) text extractor using only platform APIs: java.util.zip plus a
 * streaming XmlPullParser. Output: paragraph-level blocks in body reading order (FR-3).
 *
 * Scope: body paragraphs (headings detected from their style id) and tables (one block per row,
 * cells joined with " | ", nested tables folded into their cell). Excluded: headers/footers,
 * footnotes, comments, tracked deletions and field codes (they aren't body w:t text), embedded
 * images, drawings and text boxes. Legacy .doc / encrypted .docx (OLE files) are reported, not parsed.
 * XML DOCTYPE declarations are never processed, so external entities can't be resolved.
 */
class DocxTextExtractor(
    private val newParser: () -> XmlPullParser = { Xml.newPullParser() },
    private val maxMainPartBytes: Long = MAX_MAIN_PART_BYTES
) : WordTextExtractor {

    override suspend fun extract(localPath: String): WordExtractionResult = withContext(Dispatchers.IO) {
        val file = File(localPath)
        if (!file.isFile || file.length() == 0L) return@withContext WordExtractionResult.Failure(WordExtractionFailure.EMPTY_FILE)

        val magic = ByteArray(4)
        val read = file.inputStream().use { it.read(magic) }
        when {
            read == 4 && magic.contentEquals(OLE_MAGIC) ->
                return@withContext WordExtractionResult.Failure(WordExtractionFailure.LEGACY_DOC_OR_ENCRYPTED)
            read < 4 || !magic.contentEquals(ZIP_MAGIC) ->
                return@withContext WordExtractionResult.Failure(WordExtractionFailure.NOT_DOCX)
        }

        try {
            ZipFile(file).use { zip ->
                if (zip.size() > MAX_ZIP_ENTRIES) return@withContext WordExtractionResult.Failure(WordExtractionFailure.TOO_LARGE)
                val mainPartName = zip.getEntry("_rels/.rels")
                    ?.let { entry -> zip.getInputStream(entry).use { findMainPart(LimitedInputStream(it, MAX_RELS_BYTES)) } }
                    ?: DEFAULT_MAIN_PART
                val mainPart = zip.getEntry(mainPartName)
                    ?: return@withContext WordExtractionResult.Failure(WordExtractionFailure.MALFORMED)
                zip.getInputStream(mainPart).use { parseDocument(LimitedInputStream(it, maxMainPartBytes)) }
            }
        } catch (e: TooLargeException) {
            WordExtractionResult.Failure(WordExtractionFailure.TOO_LARGE)
        } catch (e: NotWordException) {
            WordExtractionResult.Failure(WordExtractionFailure.NOT_DOCX)
        } catch (e: ZipException) {
            WordExtractionResult.Failure(WordExtractionFailure.MALFORMED)
        } catch (e: XmlPullParserException) {
            WordExtractionResult.Failure(WordExtractionFailure.MALFORMED)
        } catch (e: IOException) {
            WordExtractionResult.Failure(WordExtractionFailure.MALFORMED)
        } catch (e: RuntimeException) {
            // Parser implementations may throw unchecked exceptions on hostile input.
            WordExtractionResult.Failure(WordExtractionFailure.MALFORMED)
        }
    }

    /** Target of the package's officeDocument relationship (transitional or strict), if any. */
    private fun findMainPart(input: InputStream): String? {
        val parser = newParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val type = parser.getAttributeValue(null, "Type").orEmpty()
                if (type.endsWith("/officeDocument")) {
                    return parser.getAttributeValue(null, "Target")?.removePrefix("/")
                }
            }
        }
        return null
    }

    private fun parseDocument(input: InputStream): WordExtractionResult {
        val parser = newParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
        val blocks = mutableListOf<ExtractedTextBlock>()
        var sawRoot = false
        var closedRoot = false
        var skipDepth = 0
        var tableDepth = 0
        var paragraph = StringBuilder()
        var paragraphStyle: String? = null
        var cell = StringBuilder()
        var rowCells = mutableListOf<String>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (!sawRoot) {
                        // The main part of a Word package must be <w:document>; anything else
                        // (e.g. a renamed spreadsheet) is not a Word document.
                        if (parser.namespace != W || parser.name != "document") throw NotWordException()
                        sawRoot = true
                        continue
                    }
                    if (skipDepth > 0) {
                        skipDepth++
                        continue
                    }
                    val isW = parser.namespace == W
                    when {
                        parser.namespace == MARKUP_COMPATIBILITY && parser.name == "Fallback" -> skipDepth = 1
                        isW && parser.name in SKIPPED_W_ELEMENTS -> skipDepth = 1
                        isW && parser.name == "tbl" -> tableDepth++
                        isW && parser.name == "tr" && tableDepth == 1 -> rowCells = mutableListOf()
                        isW && parser.name == "tc" && tableDepth == 1 -> cell = StringBuilder()
                        isW && parser.name == "p" -> {
                            paragraph = StringBuilder()
                            paragraphStyle = null
                        }
                        isW && parser.name == "pStyle" -> paragraphStyle = parser.getAttributeValue(W, "val")
                        // nextText() consumes the element, so no END_TAG follows for w:t.
                        isW && parser.name == "t" -> paragraph.append(parser.nextText())
                        isW && parser.name in setOf("tab", "ptab", "br", "cr") -> paragraph.append(' ')
                        isW && parser.name == "noBreakHyphen" -> paragraph.append('-')
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (skipDepth > 0) {
                        skipDepth--
                        continue
                    }
                    if (parser.namespace != W) continue
                    when (parser.name) {
                        "document" -> if (parser.depth == 1) closedRoot = true
                        "p" -> {
                            val text = normalize(paragraph)
                            if (tableDepth >= 1) {
                                if (text.isNotEmpty()) cell.append(if (cell.isEmpty()) text else " $text")
                            } else if (text.isNotEmpty()) {
                                val kind = if (paragraphStyle?.let(HEADING_STYLE::matches) == true) TextBlockKind.HEADING else TextBlockKind.PARAGRAPH
                                blocks += ExtractedTextBlock(kind, text)
                            }
                        }
                        "tc" -> if (tableDepth == 1) rowCells += normalize(cell)
                        "tr" -> if (tableDepth == 1) {
                            val cells = rowCells.filter { it.isNotEmpty() }
                            if (cells.isNotEmpty()) blocks += ExtractedTextBlock(TextBlockKind.TABLE_ROW, cells.joinToString(" | "))
                        }
                        "tbl" -> tableDepth--
                    }
                }
            }
        }
        if (!sawRoot) throw NotWordException()
        // Some pull parsers report END_DOCUMENT on a truncated stream; never import partial text.
        if (!closedRoot) throw XmlPullParserException("main document part is truncated")
        return WordExtractionResult.Success(blocks)
    }

    private fun normalize(text: CharSequence): String = text.toString().replace(' ', ' ').replace(WHITESPACE, " ").trim()
}

private class TooLargeException : IOException()
private class NotWordException : IOException()

/** Fails once more than [limit] decompressed bytes are read — guards against zip bombs. */
private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) track(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) track(it.toLong()) }

    private fun track(bytes: Long) {
        count += bytes
        if (count > limit) throw TooLargeException()
    }
}
