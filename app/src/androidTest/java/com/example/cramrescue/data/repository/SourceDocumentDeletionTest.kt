package com.example.cramrescue.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.StudyPriority
import com.example.cramrescue.domain.model.TriageLevel
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deletion against the real Room schema and the real app-owned file store: row, per-page OCR,
 * analysis refs and the app-owned copy go; everything else — other documents, the campaign, the
 * analysis itself and the user's original files — stays.
 */
@RunWith(AndroidJUnit4::class)
class SourceDocumentDeletionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var fileStore: MaterialFileStore
    private lateinit var documents: SourceDocumentRepositoryImpl
    private lateinit var analyses: StudyAnalysisRepositoryImpl
    private val databaseName = "test-deletion-cramrescue.db"
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        openDatabase()
        fileStore = MaterialFileStore(context)
        documents = SourceDocumentRepositoryImpl(database.sourceDocumentDao(), fileStore)
        analyses = StudyAnalysisRepositoryImpl(database.studyAnalysisDao())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        createdFiles.forEach { it.delete() }
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
    }

    /** A file outside app storage standing in for the user's original gallery image / PDF. */
    private fun externalOriginal(extension: String): File =
        File(context.cacheDir, "external-original-${UUID.randomUUID()}.$extension")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            .also { createdFiles += it }

    private fun campaign(name: String): Long = runBlocking {
        database.campaignDao().insert(
            CampaignEntity(name = name, subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)
        )
    }

    private fun addDocument(campaignId: Long, type: SourceType, localPath: String, pages: Int = 1): Long = runBlocking {
        createdFiles += File(localPath)
        val id = documents.saveDocument(
            SourceDocument(
                campaignId = campaignId,
                sourceType = type,
                localPath = localPath,
                createdAt = Instant.EPOCH,
                pageCount = pages
            )
        )
        database.ocrDao().replaceOcrResult(
            id,
            (0 until pages).map { OcrResultEntity(id, it, 1000, 1400, 100, false, 1L) },
            (0 until pages).flatMap { page ->
                (0 until 2).map { order -> OcrTextBlockEntity(documentId = id, pageIndex = page, orderIndex = order, text = "d$id p$page b$order", left = 0, top = 0, right = 1, bottom = 1) }
            }
        )
        id
    }

    private fun galleryCopy(original: File) = runBlocking { fileStore.copyFrom(Uri.fromFile(original)) }
    private fun pdfCopy(original: File) = runBlocking { fileStore.copyPdfFrom(Uri.fromFile(original)) }
    private fun cameraFile() = fileStore.createCameraOutputFile().apply { writeBytes(byteArrayOf(9)) }.absolutePath

    @Test
    fun deletingEachSourceTypeRemovesOnlyThatDocumentItsOcrAndItsOwnedCopy() = runBlocking {
        val campaignId = campaign("A")
        val galleryOriginal = externalOriginal("jpg")
        val pdfOriginal = externalOriginal("pdf")
        val galleryPath = galleryCopy(galleryOriginal)
        val cameraPath = cameraFile()
        val pdfPath = pdfCopy(pdfOriginal)
        val gallery = addDocument(campaignId, SourceType.GALLERY, galleryPath)
        val camera = addDocument(campaignId, SourceType.CAMERA, cameraPath)
        val pdf = addDocument(campaignId, SourceType.PDF, pdfPath, pages = 3)
        val ocr = database.ocrDao()

        // PDF: row, all three pages of OCR and all blocks go; the other documents are untouched.
        assertTrue(documents.deleteDocument(campaignId, pdf))
        assertNull(documents.getDocumentById(pdf))
        assertTrue(ocr.getPageResultsForDocument(pdf).isEmpty())
        assertTrue(ocr.getBlocksForDocument(pdf).isEmpty())
        assertFalse(File(pdfPath).exists())
        assertTrue("user's original PDF must never be deleted", pdfOriginal.exists())
        assertEquals(listOf(gallery, camera), documents.getDocumentsForCampaign(campaignId).map { it.id })
        assertEquals(2, ocr.getBlocksForDocument(gallery).size)

        // Gallery: the app's copy goes, the original stays.
        assertTrue(documents.deleteDocument(campaignId, gallery))
        assertFalse(File(galleryPath).exists())
        assertTrue("user's original image must never be deleted", galleryOriginal.exists())

        // Camera (last document): the campaign itself survives with no materials.
        assertTrue(documents.deleteDocument(campaignId, camera))
        assertFalse(File(cameraPath).exists())
        assertTrue(documents.getDocumentsForCampaign(campaignId).isEmpty())
        assertNotNull(database.campaignDao().getById(campaignId))
    }

    @Test
    fun campaignCannotDeleteAnotherCampaignsDocument() = runBlocking {
        val campaignA = campaign("A")
        val campaignB = campaign("B")
        val path = cameraFile()
        val documentOfB = addDocument(campaignB, SourceType.CAMERA, path)

        assertFalse(documents.deleteDocument(campaignA, documentOfB))

        assertNotNull(documents.getDocumentById(documentOfB))
        assertEquals(2, database.ocrDao().getBlocksForDocument(documentOfB).size)
        assertTrue(File(path).exists())
    }

    @Test
    fun analysisIsKeptButLosesOnlyTheRefsToTheDeletedDocument() = runBlocking {
        val campaignId = campaign("A")
        val kept = addDocument(campaignId, SourceType.CAMERA, cameraFile())
        val deleted = addDocument(campaignId, SourceType.PDF, cameraFile(), pages = 2)
        fun priority(index: Int, vararg refs: SourceRef) =
            StudyPriority(index, "P$index", TriageLevel.CODE_RED, listOf("b"), null, refs.toList())
        analyses.replaceAnalysis(
            StudyAnalysis(
                campaignId = campaignId,
                summary = "s",
                priorities = listOf(
                    priority(0, SourceRef(kept, 0, 0)),
                    priority(1, SourceRef(kept, 0, 1), SourceRef(deleted, 1, 0)),
                    priority(2, SourceRef(deleted, 0, 0))
                ),
                discardedGreenCount = 0,
                analyzedPageCount = 3,
                lowTextPageCount = 0,
                sourceFingerprint = "fp",
                modelId = "m",
                generatedAt = Instant.EPOCH
            )
        )

        assertTrue(documents.deleteDocument(campaignId, deleted))

        val analysis = analyses.getAnalysisForCampaign(campaignId)
        assertNotNull("the analysis is kept, to be shown as stale", analysis)
        assertEquals(listOf("P0", "P1", "P2"), analysis!!.priorities.map { it.title })
        assertEquals(listOf(SourceRef(kept, 0, 0)), analysis.priorities[0].sourceRefs)
        assertEquals(listOf(SourceRef(kept, 0, 1)), analysis.priorities[1].sourceRefs)
        assertTrue(analysis.priorities[2].sourceRefs.isEmpty())
    }

    @Test
    fun deletedDocumentDoesNotReturnAfterReopeningTheDatabase() = runBlocking {
        val campaignId = campaign("A")
        val keptId = addDocument(campaignId, SourceType.CAMERA, cameraFile())
        val deletedId = addDocument(campaignId, SourceType.PDF, cameraFile(), pages = 2)
        assertTrue(documents.deleteDocument(campaignId, deletedId))

        database.close()
        openDatabase()
        documents = SourceDocumentRepositoryImpl(database.sourceDocumentDao(), fileStore)

        assertEquals(listOf(keptId), documents.getDocumentsForCampaign(campaignId).map { it.id })
        assertTrue(database.ocrDao().getPageResultsForDocument(deletedId).isEmpty())
    }

    @Test
    fun fileStoreNeverDeletesFilesOutsideItsOwnDirectory() = runBlocking {
        val original = externalOriginal("pdf")
        assertFalse(fileStore.deleteOwnedFile(original.absolutePath))
        assertFalse(fileStore.deleteOwnedFile(File(context.filesDir, "materials/../${original.name}").absolutePath))
        assertTrue(original.exists())
        // An already-missing owned file counts as cleaned up.
        assertTrue(fileStore.deleteOwnedFile(File(context.filesDir, "materials/missing-${UUID.randomUUID()}.jpg").absolutePath))
    }
}
