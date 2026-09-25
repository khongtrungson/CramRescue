package com.example.cramrescue.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.model.MAX_DOCUMENT_PAGES
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.usecase.analysis.GenerateAnalysisResult
import com.example.cramrescue.domain.usecase.analysis.GenerateCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CampaignAnalysisViewModel(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val credentialRepository: GeminiCredentialRepository,
    private val getCampaignAnalysis: GetCampaignAnalysisUseCase,
    private val generateCampaignAnalysis: GenerateCampaignAnalysisUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampaignAnalysisUiState())
    val uiState: StateFlow<CampaignAnalysisUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    /** Reads only local state (Room + key presence); never calls Gemini. */
    fun refresh() {
        viewModelScope.launch {
            val campaign = campaignRepository.getCampaignById(campaignId)
            val state = getCampaignAnalysis(campaignId)
            val hasCredential = credentialRepository.hasCredential()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    campaignName = campaign?.name.orEmpty(),
                    archetypeName = campaign?.subjectArchetype?.displayName.orEmpty(),
                    hasCredential = hasCredential,
                    usablePageCount = state.source.analyzedPageCount,
                    lowTextPageCount = state.source.lowTextPageCount,
                    emptyPageCount = state.source.emptyPageCount,
                    documentsWithoutOcr = state.source.documentsWithoutOcr,
                    wordDocumentCount = state.source.wordDocumentCount,
                    result = state.analysis?.toUi(state.source, state.isStale),
                    errorNeedsSettings = if (hasCredential) false else it.errorNeedsSettings
                )
            }
        }
    }

    /** One request at a time; the running request is cancelled if the user leaves the screen. */
    fun analyze() {
        if (analysisJob?.isActive == true) return
        _uiState.update { it.copy(isAnalyzing = true, errorMessage = null, errorNeedsSettings = false) }
        analysisJob = viewModelScope.launch {
            val result = generateCampaignAnalysis(campaignId)
            _uiState.update { it.copy(isAnalyzing = false) }
            when (result) {
                is GenerateAnalysisResult.Success -> refresh()
                else -> _uiState.update {
                    it.copy(errorMessage = result.errorMessage(), errorNeedsSettings = result.needsSettings())
                }
            }
        }
    }

    private fun GenerateAnalysisResult.needsSettings(): Boolean =
        this == GenerateAnalysisResult.MissingCredential ||
            (this is GenerateAnalysisResult.Failed && failure == RemoteFailure.InvalidCredential)

    private fun GenerateAnalysisResult.errorMessage(): String? = when (this) {
        is GenerateAnalysisResult.Success -> null
        GenerateAnalysisResult.CampaignNotFound -> "Không tìm thấy chiến dịch."
        GenerateAnalysisResult.NoUsableText ->
            "Chưa có văn bản OCR để phân tích. Hãy thêm tài liệu và bấm \"Trích xuất văn bản\" trước."
        is GenerateAnalysisResult.TooManyPages ->
            "Chiến dịch có $pageCount trang, vượt giới hạn $MAX_DOCUMENT_PAGES trang cho một lần phân tích."
        GenerateAnalysisResult.MissingCredential -> "Chưa cấu hình Gemini API key."
        is GenerateAnalysisResult.Failed -> failure.userMessage()
        is GenerateAnalysisResult.InvalidAiOutput ->
            "Kết quả AI không vượt qua kiểm tra (ví dụ: trích dẫn nguồn không tồn tại) nên không được lưu. Hãy thử lại."
    }

    private fun StudyAnalysis.toUi(source: AnalysisSource, isStale: Boolean): AnalysisResultUi {
        val labels = source.pages.associate { it.documentId to it.documentLabel }
        val existingRefs = source.refMap.values.toSet()
        return AnalysisResultUi(
            summary = summary,
            priorities = priorities.map { priority ->
                // A validated priority always has ≥1 source ref; having none left means every
                // document it came from was deleted (their refs are removed with the document).
                if (priority.sourceRefs.isEmpty()) {
                    return@map PriorityUi(
                        title = priority.title,
                        triage = priority.triage,
                        bullets = priority.bullets,
                        notes = priority.notes,
                        sourceLabel = "tài liệu nguồn đã bị xóa khỏi chiến dịch",
                        sourcesResolved = true
                    )
                }
                PriorityUi(
                    title = priority.title,
                    triage = priority.triage,
                    bullets = priority.bullets,
                    notes = priority.notes,
                    sourceLabel = priority.sourceRefs.groupBy { it.documentId }.entries.joinToString("; ") { (documentId, refs) ->
                        val pages = refs.map { it.pageIndex + 1 }.distinct().sorted().joinToString(", ")
                        "${labels[documentId] ?: "Tài liệu #$documentId"} · tr. $pages (${refs.size} khối)"
                    },
                    sourcesResolved = priority.sourceRefs.all { it in existingRefs }
                )
            },
            redCount = priorities.count { it.triage == TriageLevel.CODE_RED },
            yellowCount = priorities.count { it.triage == TriageLevel.CODE_YELLOW },
            discardedGreenCount = discardedGreenCount,
            analyzedPageCount = analyzedPageCount,
            lowTextPageCount = lowTextPageCount,
            generatedAtText = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
                .format(generatedAt.atZone(ZoneId.systemDefault())),
            isStale = isStale
        )
    }
}

class CampaignAnalysisViewModelFactory(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val credentialRepository: GeminiCredentialRepository,
    private val getCampaignAnalysis: GetCampaignAnalysisUseCase,
    private val generateCampaignAnalysis: GenerateCampaignAnalysisUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CampaignAnalysisViewModel(
            campaignId,
            campaignRepository,
            credentialRepository,
            getCampaignAnalysis,
            generateCampaignAnalysis
        ) as T
    }
}
