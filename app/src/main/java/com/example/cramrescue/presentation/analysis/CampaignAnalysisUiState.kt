package com.example.cramrescue.presentation.analysis

import com.example.cramrescue.domain.model.TriageLevel

data class PriorityUi(
    val title: String,
    val triage: TriageLevel,
    val bullets: List<String>,
    val notes: String?,
    /** e.g. "cramrescue_test.pdf · tr. 1, 2 (3 khối)". */
    val sourceLabel: String,
    /** Every source ref still resolves to an existing OCR block. */
    val sourcesResolved: Boolean
)

data class AnalysisResultUi(
    val summary: String,
    val priorities: List<PriorityUi>,
    val redCount: Int,
    val yellowCount: Int,
    val discardedGreenCount: Int,
    val analyzedPageCount: Int,
    val lowTextPageCount: Int,
    val generatedAtText: String,
    val isStale: Boolean
)

data class CampaignAnalysisUiState(
    val isLoading: Boolean = true,
    val campaignName: String = "",
    val archetypeName: String = "",
    val hasCredential: Boolean = false,
    val usablePageCount: Int = 0,
    val lowTextPageCount: Int = 0,
    val emptyPageCount: Int = 0,
    val documentsWithoutOcr: Int = 0,
    val wordDocumentCount: Int = 0,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    /** The error can be fixed in the BYOK settings screen. */
    val errorNeedsSettings: Boolean = false,
    val result: AnalysisResultUi? = null
)
