package com.example.cramrescue.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuestionReviewItem(
    val question: QuizQuestion,
    val selectedOptionIndex: Int?,
    val isCorrect: Boolean
)

data class QuizResultStats(
    val score: Float = 0.0f,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val totalQuestions: Int = 0,
    val durationSeconds: Long = 0L
)

data class QuestionGroundingInfo(
    val questionStem: String,
    val sourceBlockIds: List<String>,
    val explanation: String
)

data class QuizResultUiState(
    val isLoading: Boolean = true,
    val quiz: Quiz? = null,
    val attempt: QuizAttempt? = null,
    val reviewItems: List<QuestionReviewItem> = emptyList(),
    val stats: QuizResultStats = QuizResultStats(),
    val activeGrounding: QuestionGroundingInfo? = null,
    val errorMessage: String? = null
)

class QuizResultViewModel(
    private val quizId: Long,
    private val attemptId: Long,
    private val quizRepository: QuizRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizResultUiState())
    val uiState: StateFlow<QuizResultUiState> = _uiState.asStateFlow()

    init {
        loadResult()
    }

    fun loadResult() {
        viewModelScope.launch(dispatcher) {
            val detail = quizRepository.getQuizDetail(quizId)
            val attempt = quizRepository.getAttemptById(attemptId)

            if (detail == null || attempt == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Không tìm thấy kết quả bài thi.") }
                return@launch
            }

            val answersByQuestionId = attempt.answers.associateBy { it.questionId }
            val reviewList = detail.questions.map { question ->
                val answer = answersByQuestionId[question.id]
                val selected = answer?.selectedOptionIndex
                val isCorrect = selected != null && selected == question.correctOptionIndex
                QuestionReviewItem(
                    question = question,
                    selectedOptionIndex = selected,
                    isCorrect = isCorrect
                )
            }

            val incorrectCount = attempt.totalQuestions - attempt.correctCount
            val stats = QuizResultStats(
                score = attempt.score,
                correctCount = attempt.correctCount,
                incorrectCount = incorrectCount,
                totalQuestions = attempt.totalQuestions,
                durationSeconds = attempt.durationSeconds
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    quiz = detail.quiz,
                    attempt = attempt,
                    reviewItems = reviewList,
                    stats = stats
                )
            }
        }
    }

    fun showGrounding(item: QuestionReviewItem) {
        _uiState.update {
            it.copy(
                activeGrounding = QuestionGroundingInfo(
                    questionStem = item.question.stem,
                    sourceBlockIds = item.question.sourceBlockIds,
                    explanation = item.question.explanation
                )
            )
        }
    }

    fun dismissGrounding() {
        _uiState.update { it.copy(activeGrounding = null) }
    }
}

class QuizResultViewModelFactory(
    private val quizId: Long,
    private val attemptId: Long,
    private val quizRepository: QuizRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuizResultViewModel(quizId, attemptId, quizRepository) as T
    }
}
