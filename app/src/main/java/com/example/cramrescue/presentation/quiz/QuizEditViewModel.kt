package com.example.cramrescue.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import com.example.cramrescue.domain.usecase.quiz.UpdateQuizResult
import com.example.cramrescue.domain.usecase.quiz.UpdateQuizUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditableQuestion(
    val id: Long = 0L,
    val quizId: Long = 0L,
    val stem: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val correctOptionIndex: Int = 0,
    val explanation: String = "",
    val sourceBlockIds: List<String> = emptyList()
)

data class QuizEditUiState(
    val isLoading: Boolean = true,
    val quizId: Long = 0L,
    val campaignId: Long = 0L,
    val title: String = "",
    val questions: List<EditableQuestion> = emptyList(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class QuizEditViewModel(
    private val quizId: Long,
    private val quizRepository: QuizRepository,
    private val updateQuizUseCase: UpdateQuizUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizEditUiState(quizId = quizId))
    val uiState: StateFlow<QuizEditUiState> = _uiState.asStateFlow()

    private var originalQuiz: Quiz? = null

    init {
        loadQuiz()
    }

    fun loadQuiz() {
        viewModelScope.launch(dispatcher) {
            val detail = quizRepository.getQuizDetail(quizId)
            if (detail == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không tìm thấy thông tin đề thi."
                    )
                }
                return@launch
            }

            originalQuiz = detail.quiz
            val editableQuestions = detail.questions.map { q ->
                EditableQuestion(
                    id = q.id,
                    quizId = q.quizId,
                    stem = q.stem,
                    options = if (q.options.size == 4) q.options else (q.options + List(4 - q.options.size) { "" }),
                    correctOptionIndex = q.correctOptionIndex,
                    explanation = q.explanation,
                    sourceBlockIds = q.sourceBlockIds
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    quizId = detail.quiz.id,
                    campaignId = detail.quiz.campaignId,
                    title = detail.quiz.title,
                    questions = editableQuestions,
                    hasUnsavedChanges = false
                )
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _uiState.update {
            it.copy(title = newTitle, hasUnsavedChanges = true)
        }
    }

    fun updateQuestionStem(index: Int, stem: String) {
        _uiState.update { state ->
            val updated = state.questions.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(stem = stem)
            }
            state.copy(questions = updated, hasUnsavedChanges = true)
        }
    }

    fun updateOption(questionIndex: Int, optionIndex: Int, text: String) {
        _uiState.update { state ->
            val updated = state.questions.toMutableList()
            if (questionIndex in updated.indices) {
                val opts = updated[questionIndex].options.toMutableList()
                if (optionIndex in opts.indices) {
                    opts[optionIndex] = text
                    updated[questionIndex] = updated[questionIndex].copy(options = opts)
                }
            }
            state.copy(questions = updated, hasUnsavedChanges = true)
        }
    }

    fun selectCorrectOption(questionIndex: Int, correctIndex: Int) {
        _uiState.update { state ->
            val updated = state.questions.toMutableList()
            if (questionIndex in updated.indices && correctIndex in 0..3) {
                updated[questionIndex] = updated[questionIndex].copy(correctOptionIndex = correctIndex)
            }
            state.copy(questions = updated, hasUnsavedChanges = true)
        }
    }

    fun updateExplanation(questionIndex: Int, explanation: String) {
        _uiState.update { state ->
            val updated = state.questions.toMutableList()
            if (questionIndex in updated.indices) {
                updated[questionIndex] = updated[questionIndex].copy(explanation = explanation)
            }
            state.copy(questions = updated, hasUnsavedChanges = true)
        }
    }

    fun addQuestion() {
        _uiState.update { state ->
            val updated = state.questions.toMutableList()
            updated.add(
                EditableQuestion(
                    id = 0L,
                    quizId = quizId,
                    stem = "",
                    options = listOf("", "", "", ""),
                    correctOptionIndex = 0,
                    explanation = "",
                    sourceBlockIds = emptyList()
                )
            )
            state.copy(questions = updated, hasUnsavedChanges = true)
        }
    }

    fun deleteQuestion(index: Int) {
        _uiState.update { state ->
            if (state.questions.size <= 1) {
                state.copy(errorMessage = "Đề thi phải có ít nhất 1 câu hỏi, không thể xóa tiếp.")
            } else {
                val updated = state.questions.toMutableList()
                if (index in updated.indices) {
                    updated.removeAt(index)
                }
                state.copy(questions = updated, hasUnsavedChanges = true)
            }
        }
    }

    fun saveQuiz(onSuccess: () -> Unit) {
        val state = _uiState.value
        val quiz = originalQuiz ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        val domainQuestions = state.questions.mapIndexed { idx, eq ->
            QuizQuestion(
                id = eq.id,
                quizId = quizId,
                questionOrder = idx + 1,
                stem = eq.stem,
                options = eq.options,
                correctOptionIndex = eq.correctOptionIndex,
                explanation = eq.explanation,
                sourceBlockIds = eq.sourceBlockIds
            )
        }

        viewModelScope.launch(dispatcher) {
            val result = updateQuizUseCase(
                quiz = quiz.copy(title = state.title),
                questions = domainQuestions
            )
            when (result) {
                is UpdateQuizResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            isSaved = true
                        )
                    }
                    onSuccess()
                }
                is UpdateQuizResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class QuizEditViewModelFactory(
    private val quizId: Long,
    private val quizRepository: QuizRepository,
    private val updateQuizUseCase: UpdateQuizUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuizEditViewModel::class.java)) {
            return QuizEditViewModel(
                quizId = quizId,
                quizRepository = quizRepository,
                updateQuizUseCase = updateQuizUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
