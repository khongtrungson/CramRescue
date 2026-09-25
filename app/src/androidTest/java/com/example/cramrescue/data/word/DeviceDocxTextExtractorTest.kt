package com.example.cramrescue.data.word

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The production extractor with the platform parser (android.util.Xml), which the JVM tests
 * replace with kxml2. Confirms namespace handling and failure mapping on the real runtime.
 */
@RunWith(AndroidJUnit4::class)
class DeviceDocxTextExtractorTest {

    private val extractor = DocxTextExtractor()
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun file(bytes: ByteArray) = File.createTempFile("docx-device", ".docx", dir).also { it.writeBytes(bytes); files += it }.absolutePath

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

    private val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    @Test
    fun extractsHeadingsParagraphsAndTablesWithThePlatformParser() {
        val body = "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Chương 1</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t xml:space=\"preserve\">Định nghĩa </w:t></w:r><w:del><w:r><w:delText>cũ</w:delText></w:r></w:del><w:r><w:t>mới</w:t></w:r></w:p>" +
            "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"$ns\"><w:body>$body</w:body></w:document>"
        val result = runBlocking { extractor.extract(file(zip(mapOf("word/document.xml" to xml)))) }

        assertEquals(
            WordExtractionResult.Success(
                listOf(
                    ExtractedTextBlock(TextBlockKind.HEADING, "Chương 1"),
                    ExtractedTextBlock(TextBlockKind.PARAGRAPH, "Định nghĩa mới"),
                    ExtractedTextBlock(TextBlockKind.TABLE_ROW, "A | B")
                )
            ),
            result
        )
    }

    @Test
    fun failuresMapOnDevice() = runBlocking {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()) + ByteArray(508)
        assertEquals(WordExtractionResult.Failure(WordExtractionFailure.LEGACY_DOC_OR_ENCRYPTED), extractor.extract(file(ole)))
        assertEquals(WordExtractionResult.Failure(WordExtractionFailure.NOT_DOCX), extractor.extract(file("plain".toByteArray())))
        assertEquals(WordExtractionResult.Failure(WordExtractionFailure.EMPTY_FILE), extractor.extract(file(ByteArray(0))))
        val truncated = "<w:document xmlns:w=\"$ns\"><w:body><w:p><w:r><w:t>cut"
        assertEquals(WordExtractionResult.Failure(WordExtractionFailure.MALFORMED), extractor.extract(file(zip(mapOf("word/document.xml" to truncated)))))
    }
}
