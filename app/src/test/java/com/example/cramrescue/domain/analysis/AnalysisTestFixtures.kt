package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.DocumentText
import com.example.cramrescue.domain.model.DocumentTextBlock
import com.example.cramrescue.domain.model.LOW_TEXT_CHARACTER_THRESHOLD
import com.example.cramrescue.domain.model.OcrPageResult
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.OcrTextBlock
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.repository.CampaignDeletion
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.DocumentTextRepository
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import com.example.cramrescue.domain.repository.StudyAnalysisRepository
import com.example.cramrescue.domain.word.ExtractedTextBlock
import java.time.Instant

// Test-only placeholder; not a real credential.
const val FAKE_API_KEY = "test-key-not-real"

val NOW: Instant = Instant.parse("2026-09-23T10:00:00Z")

fun campaign(id: Long, archetype: SubjectArchetype = SubjectArchetype.SOCIAL_THEORY) = Campaign(
    id = id,
    name = "Campaign $id",
    subjectArchetype = archetype,
    examDateTime = NOW.plusSeconds(36 * 3600),
    createdAt = NOW
)

fun document(id: Long, campaignId: Long, name: String? = null, pages: Int = 1) = SourceDocument(
    id = id,
    campaignId = campaignId,
    sourceType = if (pages > 1) SourceType.PDF else SourceType.GALLERY,
    localPath = "/doc$id",
    createdAt = NOW,
    pageCount = pages,
    displayName = name
)

/** [pages] maps pageIndex → block texts (in orderIndex order). */
fun ocr(documentId: Long, pages: Map<Int, List<String>>, processedAt: Instant = NOW) = OcrResult(
    documentId = documentId,
    pages = pages.map { (pageIndex, texts) ->
        val characters = texts.sumOf { it.length }
        OcrPageResult(
            documentId = documentId,
            pageIndex = pageIndex,
            sourceWidthPx = 1000,
            sourceHeightPx = 1400,
            characterCount = characters,
            isLowText = characters < LOW_TEXT_CHARACTER_THRESHOLD,
            processedAt = processedAt,
            blocks = texts.mapIndexed { order, text ->
                OcrTextBlock(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    orderIndex = order,
                    text = text,
                    boundingBox = BoundingBox(11, 22, 333, 444)
                )
            }
        )
    }
)

val LONG_TEXT = "Binary search requires a sorted input and runs in logarithmic time on arrays."

class FakeCampaignRepository(private val campaigns: List<Campaign>) : CampaignRepository {
    override suspend fun createCampaign(campaign: Campaign): Long = error("unused")
    override suspend fun getCampaignById(id: Long): Campaign? = campaigns.firstOrNull { it.id == id }
    override suspend fun getCampaigns(): List<Campaign> = campaigns
    override suspend fun updateCampaign(campaign: Campaign): Boolean = error("unused")
    override suspend fun deleteCampaign(campaignId: Long): CampaignDeletion? = error("unused")
}

class FakeSourceDocumentRepository(
    val documents: MutableList<SourceDocument>,
    /** Stands in for the Room transaction also removing the document's OCR. */
    private val onDeleted: (documentId: Long) -> Unit = {}
) : SourceDocumentRepository {
    var deleteCalls = 0
    var failDeletes = false

    override suspend fun saveDocument(document: SourceDocument): Long = error("unused")
    override suspend fun getDocumentsForCampaign(campaignId: Long) = documents.filter { it.campaignId == campaignId }
    override suspend fun getDocumentById(documentId: Long) = documents.firstOrNull { it.id == documentId }
    override suspend fun deleteDocument(campaignId: Long, documentId: Long): Boolean {
        deleteCalls++
        if (failDeletes) throw IllegalStateException("simulated database failure")
        val removed = documents.removeAll { it.id == documentId && it.campaignId == campaignId }
        if (removed) onDeleted(documentId)
        return removed
    }
}

class FakeOcrRepository(val results: MutableMap<Long, OcrResult>) : OcrRepository {
    override suspend fun saveResult(result: OcrResult) {
        results[result.documentId] = result
    }
    override suspend fun getResultForDocument(documentId: Long) = results[documentId]
}

class FakeCredentialRepository(var key: String? = FAKE_API_KEY) : GeminiCredentialRepository {
    override suspend fun hasCredential() = key != null
    override suspend fun getCredential() = key
    override suspend fun saveCredential(apiKey: String) {
        key = apiKey
    }
    override suspend fun clearCredential() {
        key = null
    }
}

/** Keeps exactly one analysis per campaign, like the Room table keyed by campaignId. */
class FakeStudyAnalysisRepository : StudyAnalysisRepository {
    val analyses = mutableMapOf<Long, StudyAnalysis>()
    var replaceCalls = 0
    override suspend fun replaceAnalysis(analysis: StudyAnalysis) {
        replaceCalls++
        analyses[analysis.campaignId] = analysis
    }
    override suspend fun getAnalysisForCampaign(campaignId: Long) = analyses[campaignId]
}

class FakeStudyAnalysisClient : StudyAnalysisClient {
    override val modelId = "fake-model"
    var nextDraft: AnalysisDraft? = null
    var nextFailure: RemoteFailure? = null
    val prompts = mutableListOf<StudyAnalysisPrompt>()
    val keysUsed = mutableListOf<String>()
    var verifyCalls = 0

    override suspend fun verifyCredential(apiKey: String) {
        verifyCalls++
        keysUsed += apiKey
        nextFailure?.let { throw RemoteFailureException(it) }
    }

    override suspend fun analyze(apiKey: String, prompt: StudyAnalysisPrompt): AnalysisDraft {
        keysUsed += apiKey
        prompts += prompt
        nextFailure?.let { throw RemoteFailureException(it) }
        return nextDraft ?: error("no draft configured")
    }
}

fun draft(vararg priorities: DraftPriority, summary: String = "Tập trung vào tìm kiếm nhị phân.") =
    AnalysisDraft(summary = summary, priorities = priorities.toList())

fun item(
    title: String = "Tìm kiếm nhị phân",
    triage: String = "CODE_RED",
    bullets: List<String> = listOf("Mảng phải được sắp xếp", "Độ phức tạp O(log n)"),
    refs: List<String> = listOf("B1")
) = DraftPriority(title = title, triage = triage, bullets = bullets, sourceBlockIds = refs)

/** In-memory Word text store (M13); [saved] records every atomic save. */
class FakeDocumentTextRepository(
    val texts: MutableMap<Long, DocumentText> = mutableMapOf()
) : DocumentTextRepository {
    val saved = mutableListOf<Pair<SourceDocument, List<ExtractedTextBlock>>>()
    var failSaves = false
    private var nextId = 500L

    override suspend fun saveWordDocument(
        document: SourceDocument,
        blocks: List<ExtractedTextBlock>,
        extractedAt: Instant
    ): Long {
        if (failSaves) throw IllegalStateException("simulated database failure")
        val id = nextId++
        saved += document to blocks
        texts[id] = wordText(id, blocks.map { it.text }, extractedAt, blocks.map { it.kind })
        return id
    }

    override suspend fun getText(documentId: Long) = texts[documentId]
}

fun wordDocument(id: Long, campaignId: Long, name: String? = "notes.docx") = SourceDocument(
    id = id,
    campaignId = campaignId,
    sourceType = SourceType.WORD,
    localPath = "/doc$id.docx",
    createdAt = NOW,
    pageCount = 1,
    displayName = name
)

/** Word text whose blocks get orderIndex = position, unless [orders] overrides them. */
fun wordText(
    documentId: Long,
    texts: List<String>,
    extractedAt: Instant = NOW,
    kinds: List<TextBlockKind>? = null,
    orders: List<Int>? = null
) = DocumentText(
    documentId = documentId,
    extractedAt = extractedAt,
    blocks = texts.mapIndexed { i, text ->
        DocumentTextBlock(
            documentId, orders?.get(i) ?: i, kinds?.get(i) ?: TextBlockKind.PARAGRAPH, text
        )
    }
)
