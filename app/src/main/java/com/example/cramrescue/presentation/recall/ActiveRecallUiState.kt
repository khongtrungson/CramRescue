package com.example.cramrescue.presentation.recall

import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.recall.ClozeSegment
import com.example.cramrescue.presentation.microcard.SourceGroupUi

/** FR-12 labels, verbatim from requirements.md. */
fun RecallAssessment.label(): String = when (this) {
    RecallAssessment.NOT_REMEMBERED -> "Chưa nhớ"
    RecallAssessment.PARTIAL -> "Tạm ổn"
    RecallAssessment.MASTERED -> "Đã thuộc"
}

/** What is visible before reveal: never notes, sources or other bullets that could give the answer away. */
sealed interface QuestionUi {
    /** FR-11: one bullet with 1–2 keywords hidden; tapping a hidden box reveals. */
    data class Cloze(val segments: List<ClozeSegment>) : QuestionUi

    /** No safe keyword: recall the main points of the concept. */
    data class General(val pointCount: Int) : QuestionUi
}

/** Only exists once the learner has revealed the answer. */
data class RevealedUi(
    /** The hidden keywords, for a Cloze prompt. */
    val answers: List<String>,
    val bullets: List<String>,
    val notes: String?,
    /** Cited document pages; tapping one opens Source Grounding. Exists only after Reveal. */
    val sourceGroups: List<SourceGroupUi>
)

sealed interface ActiveRecallUiState {
    data object Loading : ActiveRecallUiState

    data class NoCards(val message: String) : ActiveRecallUiState

    data class Studying(
        val cardId: Long,
        val isMission: Boolean,
        val position: Int,
        val total: Int,
        /** The cards came from an older analysis / changed material (M8 stale rule). */
        val cardsStale: Boolean,
        val triage: TriageLevel,
        val title: String,
        val question: QuestionUi,
        /** null = QUESTION phase: answer hidden, no assessment possible. */
        val revealed: RevealedUi?,
        /** An assessment is being saved; assessment controls are disabled. */
        val isSaving: Boolean,
        val errorMessage: String?
    ) : ActiveRecallUiState

    data class Completed(
        val total: Int,
        val counts: Map<RecallAssessment, Int>,
        val isMission: Boolean
    ) : ActiveRecallUiState
}
