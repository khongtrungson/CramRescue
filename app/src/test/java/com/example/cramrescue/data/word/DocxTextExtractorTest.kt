package com.example.cramrescue.data.word

import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.word.ExtractedTextBlock
import com.example.cramrescue.domain.word.WordExtractionFailure
import com.example.cramrescue.domain.word.WordExtractionResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

private const val DEFAULT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

/** Wraps body XML in a real-shaped w:document with the namespaces Word emits. */
private fun documentXml(body: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$W_NS" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:v="urn:schemas-microsoft-com:vml"><w:body>$body<w:sectPr/></w:body></w:document>"""

private fun p(text: String, style: String? = null) =
    "<w:p>" + (style?.let { "<w:pPr><w:pStyle w:val=\"$it\"/></w:pPr>" } ?: "") + "<w:r><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"

/**
 * DOCX fixtures built in memory — the same ZIP + XML structure Word writes, no binary test assets.
 */
class DocxTextExtractorTest {

    private val extractor = DocxTextExtractor(newParser = { KXmlParser() })
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun file(bytes: ByteArray): String =
        File.createTempFile("docx-test", ".docx").also { it.writeBytes(bytes); files += it }.absolutePath

    private fun zip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun docx(body: String) = file(zip(mapOf("_rels/.rels" to DEFAULT_RELS, "word/document.xml" to documentXml(body))))

    private fun extract(path: String) = runBlocking { extractor.extract(path) }

    private fun blocks(path: String): List<ExtractedTextBlock> =
        (extract(path) as WordExtractionResult.Success).blocks

    private fun failure(path: String) = (extract(path) as WordExtractionResult.Failure).reason

    @Test
    fun paragraphsAndHeadingsKeepReadingOrderAndVietnameseText() {
        val result = blocks(
            docx(
                p("Chương 1: Tìm kiếm nhị phân", style = "Heading1") +
                    p("Mảng phải được sắp xếp trước khi tìm kiếm.") +
                    p("Tiêu đề phụ", style = "heading2") +
                    p("Độ phức tạp O(log n) — nhanh hơn tuyến tính.") +
                    p("Tài liệu", style = "Title")
            )
        )
        assertEquals(
            listOf(
                ExtractedTextBlock(TextBlockKind.HEADING, "Chương 1: Tìm kiếm nhị phân"),
                ExtractedTextBlock(TextBlockKind.PARAGRAPH, "Mảng phải được sắp xếp trước khi tìm kiếm."),
                ExtractedTextBlock(TextBlockKind.HEADING, "Tiêu đề phụ"),
                ExtractedTextBlock(TextBlockKind.PARAGRAPH, "Độ phức tạp O(log n) — nhanh hơn tuyến tính."),
                ExtractedTextBlock(TextBlockKind.HEADING, "Tài liệu")
            ),
            result
        )
    }

    @Test
    fun runsAreJoinedAndWhitespaceIsNormalized() {
        val result = blocks(
            docx(
                "<w:p><w:r><w:t>Định</w:t></w:r><w:r><w:t xml:space=\"preserve\"> nghĩa</w:t></w:r><w:r><w:tab/><w:t>A</w:t><w:br/><w:t>B</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t xml:space=\"preserve\">  khoảng trắng   thừa  </w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>non</w:t><w:noBreakHyphen/><w:t>break</w:t></w:r></w:p>" +
                    "<w:p/><w:p><w:r><w:t xml:space=\"preserve\">   </w:t></w:r></w:p>" // empty paragraphs dropped
            )
        )
        assertEquals(listOf("Định nghĩa A B", "khoảng trắng thừa", "non-break"), result.map { it.text })
    }

    @Test
    fun tableRowsBecomeOneBlockEachWithCellsJoinedAndNestedTablesFolded() {
        val cell = { text: String -> "<w:tc>${p(text)}</w:tc>" }
        val nested = "<w:tc><w:tbl><w:tr>${cell("trong")}${cell("bảng")}</w:tr></w:tbl></w:tc>"
        val result = blocks(
            docx(
                p("Trước bảng") +
                    "<w:tbl><w:tr>${cell("Khái niệm")}${cell("Ý nghĩa")}</w:tr>" +
                    "<w:tr>${cell("Mảng")}<w:tc>${p("Dãy phần tử")}${p("liên tiếp")}</w:tc></w:tr>" +
                    "<w:tr>${cell("")}${cell("")}</w:tr>" + // empty row dropped
                    "<w:tr>${cell("Lồng")}$nested</w:tr></w:tbl>" +
                    p("Sau bảng")
            )
        )
        assertEquals(
            listOf(
                ExtractedTextBlock(TextBlockKind.PARAGRAPH, "Trước bảng"),
                ExtractedTextBlock(TextBlockKind.TABLE_ROW, "Khái niệm | Ý nghĩa"),
                ExtractedTextBlock(TextBlockKind.TABLE_ROW, "Mảng | Dãy phần tử liên tiếp"),
                ExtractedTextBlock(TextBlockKind.TABLE_ROW, "Lồng | trong bảng"),
                ExtractedTextBlock(TextBlockKind.PARAGRAPH, "Sau bảng")
            ),
            result
        )
    }

    @Test
    fun trackedDeletionsAreExcludedAndInsertionsKept() {
        val result = blocks(
            docx("<w:p><w:r><w:t>Giữ </w:t></w:r><w:del><w:r><w:delText>bị xóa </w:delText></w:r></w:del><w:ins><w:r><w:t>được thêm</w:t></w:r></w:ins></w:p>")
        )
        assertEquals(listOf("Giữ được thêm"), result.map { it.text })
    }

    @Test
    fun textBoxesAndTheirVmlFallbackAreNotDuplicatedIntoBodyText() {
        val textBox = "<w:txbxContent>${p("Hộp văn bản")}</w:txbxContent>"
        val result = blocks(
            docx(
                "<w:p><w:r><w:t>Thân bài</w:t></w:r><w:r><mc:AlternateContent>" +
                    "<mc:Choice Requires=\"wps\"><w:drawing><wp:inline>$textBox</wp:inline></w:drawing></mc:Choice>" +
                    "<mc:Fallback><w:pict><v:shape>$textBox</v:shape></w:pict></mc:Fallback>" +
                    "</mc:AlternateContent></w:r></w:p>" + p("Đoạn tiếp")
            )
        )
        assertEquals(listOf("Thân bài", "Đoạn tiếp"), result.map { it.text })
    }

    @Test
    fun documentWithNoBodyTextIsASuccessWithNoBlocks() {
        assertEquals(emptyList<ExtractedTextBlock>(), blocks(docx("<w:p/><w:tbl><w:tr><w:tc><w:p/></w:tc></w:tr></w:tbl>")))
    }

    @Test
    fun mainPartIsFoundThroughThePackageRelationships() {
        val rels = DEFAULT_RELS.replace("Target=\"word/document.xml\"", "Target=\"/custom/main.xml\"")
        val path = file(zip(mapOf("_rels/.rels" to rels, "custom/main.xml" to documentXml(p("Đường dẫn tùy chỉnh")))))
        assertEquals(listOf("Đường dẫn tùy chỉnh"), blocks(path).map { it.text })

        // No rels at all → conventional location.
        val noRels = file(zip(mapOf("word/document.xml" to documentXml(p("Mặc định")))))
        assertEquals(listOf("Mặc định"), blocks(noRels).map { it.text })
    }

    @Test
    fun zeroByteFileIsEmpty() {
        assertEquals(WordExtractionFailure.EMPTY_FILE, failure(file(ByteArray(0))))
        assertEquals(WordExtractionFailure.EMPTY_FILE, failure("/does/not/exist.docx"))
    }

    @Test
    fun legacyDocOrEncryptedOleFileIsReportedNotParsed() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) + ByteArray(504)
        assertEquals(WordExtractionFailure.LEGACY_DOC_OR_ENCRYPTED, failure(file(ole)))
    }

    @Test
    fun renamedNonWordFilesAreNotDocx() {
        assertEquals(WordExtractionFailure.NOT_DOCX, failure(file("chỉ là văn bản thường".toByteArray())))
        assertEquals(WordExtractionFailure.NOT_DOCX, failure(file("%PDF-1.7\n".toByteArray())))
        // A valid ZIP/OOXML package whose main part is not w:document (e.g. a renamed .xlsx).
        val sheet = "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>"
        assertEquals(WordExtractionFailure.NOT_DOCX, failure(file(zip(mapOf("word/document.xml" to sheet)))))
    }

    @Test
    fun malformedPackagesAreMalformed() {
        // ZIP without the main part.
        assertEquals(WordExtractionFailure.MALFORMED, failure(file(zip(mapOf("_rels/.rels" to DEFAULT_RELS, "other.xml" to "<a/>")))))
        // Broken XML.
        assertEquals(WordExtractionFailure.MALFORMED, failure(file(zip(mapOf("word/document.xml" to "<w:document xmlns:w=\"$W_NS\"><w:body><w:p>")))))
        // Truncated ZIP (header only).
        val truncated = zip(mapOf("word/document.xml" to documentXml(p("x")))).copyOf(40)
        assertEquals(WordExtractionFailure.MALFORMED, failure(file(truncated)))
    }

    @Test
    fun externalEntitiesAreNeverResolved() {
        val secret = File.createTempFile("secret", ".txt").also { it.writeText("BÍ MẬT"); files += it }
        val xml = "<?xml version=\"1.0\"?><!DOCTYPE w:document [<!ENTITY xxe SYSTEM \"${secret.toURI()}\">]>" +
            "<w:document xmlns:w=\"$W_NS\"><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>"
        val result = extract(file(zip(mapOf("word/document.xml" to xml))))
        if (result is WordExtractionResult.Success) {
            assertFalse(result.blocks.any { "BÍ MẬT" in it.text })
        } else {
            assertEquals(WordExtractionFailure.MALFORMED, (result as WordExtractionResult.Failure).reason)
        }
    }

    @Test
    fun decompressedMainPartAboveTheCapIsTooLarge() {
        val capped = DocxTextExtractor(newParser = { KXmlParser() }, maxMainPartBytes = 4_096)
        val big = docx((1..200).joinToString("") { p("Đoạn số $it có nội dung lặp lại.") })
        assertTrue(runBlocking { extractor.extract(big) } is WordExtractionResult.Success)
        assertEquals(WordExtractionResult.Failure(WordExtractionFailure.TOO_LARGE), runBlocking { capped.extract(big) })
    }
}
