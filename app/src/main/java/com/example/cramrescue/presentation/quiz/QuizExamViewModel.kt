package com.example.cramrescue.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.quiz.QuizSessionEngine
import com.example.cramrescue.domain.repository.QuizRepository
import com.example.cramrescue.domain.usecase.quiz.SubmitQuizAttemptUseCase
import com.example.cramrescue.domain.usecase.quiz.SubmitQuizResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QuizExamUiState(
    val isLoading: Boolean = true,
    val quiz: Quiz? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<Long, Int> = emptyMap(),
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val isWarning: Boolean = false,
    val isSubmitted: Boolean = false,
    val isSubmitting: Boolean = false,
    val submittedAttemptId: Long? = null,
    val errorMessage: String? = null
) {
    val currentQuestion: QuizQuestion? get() = questions.getOrNull(currentQuestionIndex)
    val answeredCount: Int get() = selectedAnswers.size
    val totalCount: Int get() = questions.size
}

class QuizExamViewModel(
    private val quizId: Long,
    private val quizRepository: QuizRepository,
    private val submitQuizAttempt: SubmitQuizAttemptUseCase,
    private val isRetake: Boolean = false,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val startTicker: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizExamUiState())
    val uiState: StateFlow<QuizExamUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var originalQuestions: List<QuizQuestion> = emptyList()

    init {
        loadExam()
    }

    private fun loadExam() {
        viewModelScope.launch(dispatcher) {
            val detail = quizRepository.getQuizDetail(quizId)
            if (detail == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Không tìm thấy đề thi.") }
                return@launch
            }

            originalQuestions = detail.questions
            val questions = if (isRetake || detail.attempts.isNotEmpty()) {
                QuizSessionEngine.shuffleForRetake(detail.questions)
            } else {
                detail.questions
            }

            val totalDuration = QuizSessionEngine.examDurationSeconds(questions.size)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    quiz = detail.quiz,
                    questions = questions,
                    remainingSeconds = totalDuration,
                    totalSeconds = totalDuration,
                    isWarning = QuizSessionEngine.isWarningTime(totalDuration)
                )
            }

            if (startTicker) {
                startTimer()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(dispatcher) {
            while (isActive && _uiState.value.remainingSeconds > 0 && !_uiState.value.isSubmitted) {
                delay(1000L)
                val newRemaining = (_uiState.value.remainingSeconds - 1).coerceAtLeast(0)
                val isWarning = QuizSessionEngine.isWarningTime(newRemaining)
                _uiState.update {
                    it.copy(
                        remainingSeconds = newRemaining,
                        isWarning = isWarning
                    )
                }

                if (newRemaining == 0L) {
                    // Timeout! Auto-submit
                    submitExam {}
                    break
                }
            }
        }
    }

    fun selectOption(questionId: Long, optionIndex: Int) {
        if (_uiState.value.isSubmitted || _uiState.value.isSubmitting) return
        _uiState.update { state ->
            val updated = state.selectedAnswers.toMutableMap()
            updated[questionId] = optionIndex
            state.copy(selectedAnswers = updated)
        }
    }

    fun nextQuestion() {
        _uiState.update { state ->
            if (state.currentQuestionIndex < state.questions.size - 1) {
                state.copy(currentQuestionIndex = state.currentQuestionIndex + 1)
            } else state
        }
    }

    fun previousQuestion() {
        _uiState.update { state ->
            if (state.currentQuestionIndex > 0) {
                state.copy(currentQuestionIndex = state.currentQuestionIndex - 1)
            } else state
        }
    }

    fun jumpToQuestion(index: Int) {
        _uiState.update { state ->
            if (index in state.questions.indices) {
                state.copy(currentQuestionIndex = index)
            } else state
        }
    }

    fun submitExam(onSuccess: (Long) -> Unit) {
        if (_uiState.value.isSubmitted || _uiState.value.isSubmitting) return
        timerJob?.cancel()
        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(dispatcher) {
            val currentState = _uiState.value
            val timeSpent = (currentState.totalSeconds - currentState.remainingSeconds).coerceAtLeast(1)
            val originalQuestionsMap = originalQuestions.associateBy { it.id }
            val originalAnswers = currentState.questions.associate { q ->
                val displayedIndex = currentState.selectedAnswers[q.id]
                val origQuestion = originalQuestionsMap[q.id]
                val origIndex = if (displayedIndex != null && origQuestion != null) {
                    val chosenText = q.options.getOrNull(displayedIndex)
                    if (chosenText != null) {
                        origQuestion.options.indexOf(chosenText).takeIf { it >= 0 }
                    } else null
                } else null
                q.id to origIndex
            }

            val result = submitQuizAttempt(
                quizId = quizId,
                userAnswers = originalAnswers,
                durationSeconds = timeSpent
            )
            when (result) {
                is SubmitQuizResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isSubmitted = true,
                            submittedAttemptId = result.attemptId
                        )
                    }
                    onSuccess(result.attemptId)
                }
                SubmitQuizResult.QuizNotFound -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Không tìm thấy đề thi.") }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class QuizExamViewModelFactory(
    private val quizId: Long,
    private val quizRepository: QuizRepository,
    private val submitQuizAttempt: SubmitQuizAttemptUseCase,
    private val isRetake: Boolean = false
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuizExamViewModel(quizId, quizRepository, submitQuizAttempt, isRetake) as T
    }
}
