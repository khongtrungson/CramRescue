package com.example.cramrescue.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.domain.analysis.AnalysisDraft
import com.example.cramrescue.domain.analysis.DraftPriority
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.grounding.GroundingResult
import com.example.cramrescue.domain.grounding.GroundingUnavailableReason
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.campaign.DeleteCampaignResult
import com.example.cramrescue.domain.usecase.campaign.DeleteCampaignUseCase
import com.example.cramrescue.domain.usecase.grounding.GetGroundedSourceUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.domain.usecase.mission.GetStudyPlanUseCase
import com.example.cramrescue.domain.usecase.mission.ReadinessState
import com.example.cramrescue.domain.usecase.mission.StartMicroMissionUseCase
import com.example.cramrescue.domain.usecase.mission.StartMissionResult
import com.example.cramrescue.domain.usecase.recall.RecordRecallAssessmentUseCase
import com.example.cramrescue.domain.usecase.speedrun.GetSpeedRunCardsUseCase
import com.example.cramrescue.domain.usecase.speedrun.SpeedRunStart
import com.example.cramrescue.domain.word.ExtractedTextBlock
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M14 campaign isolation and deletion against the real v9 schema, real repositories/use cases and
 * the real app-owned file store. Two fully populated Campaigns (image + PDF + DOCX, OCR, Word text,
 * analysis, cards, attempts, mission); A never sees B; deleting A removes every A row and A's
 * app-owned copies, and leaves B — rows, files and behaviour — exactly as it was.
 */
@RunWith(AndroidJUnit4::class)
class CampaignDeletionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var fileStore: MaterialFileStore
    private val databaseName = "test-campaign-deletion-cramrescue.db"
    private val createdFiles = mutableListOf<File>()

    private lateinit var campaigns: CampaignRepositoryImpl
    private lateinit var documents: SourceDocumentRepositoryImpl
    private lateinit var texts: DocumentTextRepositoryImpl
    private lateinit var getSource: GetAnalysisSourceUseCase
    private lateinit var getCards: GetMicroCardsUseCase
    private lateinit var studyPlan: GetStudyPlanUseCase
    private lateinit var startMission: StartMicroMissionUseCase
    private lateinit var speedRun: GetSpeedRunCardsUseCase
    private lateinit var ground: GetGroundedSourceUseCase
    private lateinit var record: RecordRecallAssessmentUseCase
    private lateinit var createCards: CreateMicroCardsUseCase
    private lateinit var analyses: StudyAnalysisRepositoryImpl

    private val now = Instant.now()

    /** Everything one populated Campaign consists of. */
    private data class Fixture(
        val campaignId: Long,
        val imageId: Long, val pdfId: Long, val wordId: Long,
        val imagePath: String, val pdfPath: String, val wordPath: String,
        val imageOriginal: File, val pdfOriginal: File, val wordOriginal: File,
        val cards: List<MicroCard>
    ) {
        val documentIds get() = listOf(imageId, pdfId, wordId)
        fun card(title: String) = cards.first { it.title == title }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        fileStore = MaterialFileStore(context)
        campaigns = CampaignRepositoryImpl(database.campaignDao(), fileStore)
        documents = SourceDocumentRepositoryImpl(database.sourceDocumentDao(), fileStore)
        texts = DocumentTextRepositoryImpl(database.documentTextDao())
        val ocr = OcrRepositoryImpl(database.ocrDao())
        analyses = StudyAnalysisRepositoryImpl(database.studyAnalysisDao())
        val cardRepository = MicroCardRepositoryImpl(database.microCardDao())
        val attempts = StudyAttemptRepositoryImpl(database.studyAttemptDao())
        val missions = MicroMissionRepositoryImpl(database.microMissionDao())
        getSource = GetAnalysisSourceUseCase(documents, ocr, texts)
        val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
        getCards = GetMicroCardsUseCase(cardRepository, getAnalysis)
        studyPlan = GetStudyPlanUseCase(getCards, attempts, missions)
        startMission = StartMicroMissionUseCase(campaigns, getCards, attempts, missions)
        speedRun = GetSpeedRunCardsUseCase(campaigns, getCards, attempts)
        ground = GetGroundedSourceUseCase(getCards, documents, ocr, texts)
        record = RecordRecallAssessmentUseCase(attempts)
        createCards = CreateMicroCardsUseCase(getAnalysis, cardRepository)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        createdFiles.forEach { it.deleteRecursively() }
    }

    /** Stands in for the user's original file outside app storage. */
    private fun externalOriginal(extension: String): File =
        File(context.cacheDir, "external-original-${UUID.randomUUID()}.$extension")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            .also { createdFiles += it }

    private fun ownedCopy(original: File): String = runBlocking {
        val path = when (original.extension) {
            "pdf" -> fileStore.copyPdfFrom(Uri.fromFile(original))
            "docx" -> fileStore.copyWordFrom(Uri.fromFile(original))
            else -> fileStore.copyFrom(Uri.fromFile(original))
        }
        createdFiles += File(path)
        path
    }

    private fun ocrFor(documentId: Long, text: String) = runBlocking {
        database.ocrDao().replaceOcrResult(
            documentId,
            listOf(OcrResultEntity(documentId, 0, 1000, 1400, text.length, false, now.minusSeconds(60).toEpochMilli())),
            listOf(OcrTextBlockEntity(documentId = documentId, pageIndex = 0, orderIndex = 0, text = text, left = 10, top = 20, right = 300, bottom = 60))
        )
    }

    /** One fully populated Campaign whose exam is 5 h away (Speed-run window open). */
    private fun populate(name: String): Fixture = runBlocking {
        val campaignId = campaigns.createCampaign(
            Campaign(name = name, subjectArchetype = SubjectArchetype.PROGRAMMING_TECH, examDateTime = now.plus(Duration.ofHours(5)), createdAt = now)
        )
        val imageOriginal = externalOriginal("jpg")
        val pdfOriginal = externalOriginal("pdf")
        val wordOriginal = externalOriginal("docx")
        val imagePath = ownedCopy(imageOriginal)
        val pdfPath = ownedCopy(pdfOriginal)
        val wordPath = ownedCopy(wordOriginal)

        val imageId = documents.saveDocument(SourceDocument(campaignId = campaignId, sourceType = SourceType.GALLERY, localPath = imagePath, createdAt = now, pageCount = 1))
        val pdfId = documents.saveDocument(SourceDocument(campaignId = campaignId, sourceType = SourceType.PDF, localPath = pdfPath, createdAt = now, pageCount = 1, displayName = "$name.pdf"))
        ocrFor(imageId, "$name ảnh: tìm kiếm nhị phân yêu cầu mảng đã sắp xếp theo thứ tự tăng dần.")
        ocrFor(pdfId, "$name PDF: độ phức tạp thời gian của tìm kiếm nhị phân là O(log n) trên mảng.")
        val wordId = texts.saveWordDocument(
            SourceDocument(campaignId = campaignId, sourceType = SourceType.WORD, localPath = wordPath, createdAt = now, pageCount = 1, displayName = "$name.docx"),
            listOf(ExtractedTextBlock(TextBlockKind.HEADING, "$name Chương 1"), ExtractedTextBlock(TextBlockKind.PARAGRAPH, "$name đoạn Word: lỗi tràn số khi tính mid.")),
            now.minusSeconds(60)
        )

        // Source order: image (B1), PDF (B2), Word paragraphs (B3, B4).
        val draft = AnalysisDraft(
            summary = "Ôn $name.",
            priorities = listOf(
                DraftPriority("$name Red ảnh", "CODE_RED", listOf("Mảng phải sắp xếp"), listOf("B1")),
                DraftPriority("$name Red Word", "CODE_RED", listOf("Tránh tràn số"), listOf("B4")),
                DraftPriority("$name Yellow", "CODE_YELLOW", listOf("O(log n)"), listOf("B2", "B3"))
            )
        )
        val analysis = (StudyAnalysisValidator.validate(draft, getSource(campaignId), "test-model", now) as ValidationResult.Valid).analysis
        analyses.replaceAnalysis(analysis)
        createCards(campaignId)
        val cards = getCards(campaignId).cards
        assertEquals(3, cards.size)
        Fixture(campaignId, imageId, pdfId, wordId, imagePath, pdfPath, wordPath, imageOriginal, pdfOriginal, wordOriginal, cards)
    }

    private fun count(sql: String): Int = database.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    /** Row counts for everything a Campaign owns, directly or through its documents/analysis/cards. */
    private fun ownedRows(f: Fixture): Map<String, Int> {
        val docs = f.documentIds.joinToString(",")
        val c = f.campaignId
        return mapOf(
            "campaigns" to count("SELECT COUNT(*) FROM campaigns WHERE id = $c"),
            "source_documents" to count("SELECT COUNT(*) FROM source_documents WHERE campaignId = $c OR id IN ($docs)"),
            "ocr_results" to count("SELECT COUNT(*) FROM ocr_results WHERE documentId IN ($docs)"),
            "ocr_text_blocks" to count("SELECT COUNT(*) FROM ocr_text_blocks WHERE documentId IN ($docs)"),
            "document_text_blocks" to count("SELECT COUNT(*) FROM document_text_blocks WHERE documentId IN ($docs)"),
            "campaign_analyses" to count("SELECT COUNT(*) FROM campaign_analyses WHERE campaignId = $c"),
            "analysis_priorities" to count("SELECT COUNT(*) FROM analysis_priorities WHERE campaignId = $c"),
            "analysis_source_refs" to count("SELECT COUNT(*) FROM analysis_source_refs WHERE documentId IN ($docs)"),
            "micro_cards" to count("SELECT COUNT(*) FROM micro_cards WHERE campaignId = $c"),
            "micro_card_source_refs" to count("SELECT COUNT(*) FROM micro_card_source_refs WHERE documentId IN ($docs)"),
            "study_attempts" to count("SELECT COUNT(*) FROM study_attempts WHERE campaignId = $c"),
            "micro_missions" to count("SELECT COUNT(*) FROM micro_missions WHERE campaignId = $c")
        )
    }

    private fun readiness(campaignId: Long) = runBlocking { (studyPlan(campaignId).readiness as ReadinessState.Available).snapshot }

    @Test
    fun campaignsAreIsolatedAndDeletingOneRemovesExactlyItsDataAndFiles() = runBlocking {
        val a = populate("A")
        val b = populate("B")
        // A studies: one Red mastered, a mission started. B has no history.
        record(a.campaignId, a.card("A Red ảnh"), RecallAssessment.MASTERED)
        assertTrue(startMission(a.campaignId) is StartMissionResult.Started)

        // ---- isolation (50–57) ----
        assertEquals(b.documentIds, documents.getDocumentsForCampaign(b.campaignId).map { it.id })
        assertTrue(getSource(b.campaignId).blocks.none { it.sourceRef.documentId in a.documentIds })
        assertTrue(getCards(b.campaignId).cards.all { it.campaignId == b.campaignId && it.sourceRefs.none { r -> r.documentId in a.documentIds } })
        assertNotEquals(readiness(a.campaignId), readiness(b.campaignId))
        assertEquals(0, readiness(b.campaignId).percent)
        assertEquals(0, count("SELECT COUNT(*) FROM micro_missions WHERE campaignId = ${b.campaignId}"))
        val speedRunA = speedRun(a.campaignId) as SpeedRunStart.Ready
        assertEquals(listOf("A Red Word"), speedRunA.cards.map { it.title })
        assertTrue((speedRun(b.campaignId) as SpeedRunStart.Ready).cards.all { it.campaignId == b.campaignId })
        // Image/PDF grounding: A's card can't open B's page, and B can't open A's card.
        assertTrue(ground(a.campaignId, a.card("A Red ảnh").id, a.imageId, 0) is GroundingResult.Available)
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(a.campaignId, a.card("A Red ảnh").id, b.imageId, 0))
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.CARD_NOT_FOUND), ground(b.campaignId, a.card("A Red ảnh").id, a.imageId, 0))
        // DOCX paragraph grounding likewise.
        assertTrue(ground(a.campaignId, a.card("A Red Word").id, a.wordId, 0) is GroundingResult.AvailableText)
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(a.campaignId, a.card("A Red Word").id, b.wordId, 0))

        // ---- snapshot of B, then delete A ----
        val bRowsBefore = ownedRows(b)
        val bCampaignBefore = campaigns.getCampaignById(b.campaignId)
        val bCardsBefore = getCards(b.campaignId)
        val bPlanBefore = studyPlan(b.campaignId)
        assertTrue(ownedRows(a).values.all { it > 0 }) // every table really held A data

        assertEquals(DeleteCampaignResult.Deleted(0), DeleteCampaignUseCase(campaigns)(a.campaignId))

        // Every A row in every campaign-owned table is gone.
        assertEquals(ownedRows(a).mapValues { 0 }, ownedRows(a))
        assertNull(campaigns.getCampaignById(a.campaignId))
        // A's app-owned copies are gone; the user's originals are untouched.
        listOf(a.imagePath, a.pdfPath, a.wordPath).forEach { assertFalse("$it should be deleted", File(it).exists()) }
        listOf(a.imageOriginal, a.pdfOriginal, a.wordOriginal).forEach { assertTrue("original ${it.name} must stay", it.exists()) }
        // A is unreachable everywhere.
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.CARD_NOT_FOUND), ground(a.campaignId, a.card("A Red ảnh").id, a.imageId, 0))
        assertEquals(SpeedRunStart.CampaignNotFound, speedRun(a.campaignId))
        assertEquals(StartMissionResult.CampaignNotFound, startMission(a.campaignId))

        // ---- B is exactly as before, and still works (58) ----
        assertEquals(bRowsBefore, ownedRows(b))
        assertEquals(bCampaignBefore, campaigns.getCampaignById(b.campaignId))
        assertEquals(bCardsBefore, getCards(b.campaignId))
        assertEquals(bPlanBefore, studyPlan(b.campaignId))
        listOf(b.imagePath, b.pdfPath, b.wordPath).forEach { assertTrue("B file $it must stay", File(it).exists()) }
        assertTrue(ground(b.campaignId, b.card("B Red ảnh").id, b.imageId, 0) is GroundingResult.Available)
        assertTrue(ground(b.campaignId, b.card("B Red Word").id, b.wordId, 0) is GroundingResult.AvailableText)
        assertTrue(speedRun(b.campaignId) is SpeedRunStart.Ready)
    }

    @Test
    fun deletingTheLastCampaignLeavesNoCampaigns() = runBlocking {
        val a = populate("A")
        assertEquals(DeleteCampaignResult.Deleted(0), DeleteCampaignUseCase(campaigns)(a.campaignId))
        assertTrue(campaigns.getCampaigns().isEmpty())
        assertEquals(0, count("SELECT COUNT(*) FROM source_documents") + count("SELECT COUNT(*) FROM micro_cards") + count("SELECT COUNT(*) FROM study_attempts"))
        assertEquals(DeleteCampaignResult.NotFound, DeleteCampaignUseCase(campaigns)(a.campaignId))
    }

    @Test
    fun anAlreadyMissingFileDoesNotBreakDeletion() = runBlocking {
        val a = populate("A")
        File(a.pdfPath).delete()
        assertEquals(DeleteCampaignResult.Deleted(0), DeleteCampaignUseCase(campaigns)(a.campaignId))
        assertEquals(ownedRows(a).mapValues { 0 }, ownedRows(a))
        assertFalse(File(a.imagePath).exists())
    }

    @Test
    fun anUndeletableFileIsCountedWhileTheDatabaseDeletionStands() = runBlocking {
        val a = populate("A")
        // A non-empty directory inside the materials directory can't be deleted by File.delete().
        val stuck = File(File(a.imagePath).parentFile, "stuck-${UUID.randomUUID()}.jpg").apply { mkdirs(); File(this, "x").writeBytes(byteArrayOf(1)) }
        createdFiles += stuck
        documents.saveDocument(SourceDocument(campaignId = a.campaignId, sourceType = SourceType.GALLERY, localPath = stuck.absolutePath, createdAt = now, pageCount = 1))

        assertEquals(DeleteCampaignResult.Deleted(undeletedFileCount = 1), DeleteCampaignUseCase(campaigns)(a.campaignId))
        assertNull(campaigns.getCampaignById(a.campaignId))
        assertEquals(0, count("SELECT COUNT(*) FROM source_documents WHERE campaignId = ${a.campaignId}"))
        assertFalse(File(a.wordPath).exists())
    }

    @Test
    fun aPathOutsideAppStorageIsNeverDeleted() = runBlocking {
        val a = populate("A")
        val external = externalOriginal("jpg")
        documents.saveDocument(SourceDocument(campaignId = a.campaignId, sourceType = SourceType.GALLERY, localPath = external.absolutePath, createdAt = now, pageCount = 1))

        assertEquals(DeleteCampaignResult.Deleted(undeletedFileCount = 1), DeleteCampaignUseCase(campaigns)(a.campaignId))
        assertTrue(external.exists())
    }
}
