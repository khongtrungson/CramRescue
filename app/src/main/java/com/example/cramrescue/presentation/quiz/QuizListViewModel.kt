package com.example.cramrescue.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.repository.QuizRepository
import com.example.cramrescue.domain.usecase.quiz.GenerateQuizResult
import com.example.cramrescue.domain.usecase.quiz.GenerateQuizUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizListUiState(
    val isLoading: Boolean = true,
    val quizzes: List<Quiz> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

class QuizListViewModel(
    private val campaignId: Long,
    private val quizRepository: QuizRepository,
    private val generateQuiz: suspend (Long, Int) -> GenerateQuizResult,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizListUiState())
    val uiState: StateFlow<QuizListUiState> = _uiState.asStateFlow()

    init {
        loadQuizzes()
    }

    fun loadQuizzes() {
        viewModelScope.launch(dispatcher) {
            quizRepository.observeQuizzesByCampaign(campaignId).collect { list ->
                _uiState.update { it.copy(isLoading = false, quizzes = list) }
            }
        }
    }

    fun generateNewQuiz(questionCount: Int, onSuccess: (Long) -> Unit) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

        viewModelScope.launch(dispatcher) {
            val result = generateQuiz(campaignId, questionCount)
            when (result) {
                is GenerateQuizResult.Success -> {
                    _uiState.update { it.copy(isGenerating = false) }
                    onSuccess(result.quizId)
                }
                GenerateQuizResult.CampaignNotFound -> {
                    _uiState.update { it.copy(isGenerating = false, errorMessage = "Không tìm thấy chiến dịch.") }
                }
                GenerateQuizResult.NoMaterials -> {
                    _uiState.update { it.copy(isGenerating = false, errorMessage = "Chiến dịch chưa có tài liệu nào được nhận dạng. Hãy nạp tài liệu trước.") }
                }
                GenerateQuizResult.NoApiKey -> {
                    _uiState.update { it.copy(isGenerating = false, errorMessage = "Chưa thiết lập Gemini API key. Hãy vào cài đặt để thêm key.") }
                }
                is GenerateQuizResult.Failure -> {
                    val msg = when (result.failure) {
                        RemoteFailure.InvalidCredential -> "API key không hợp lệ hoặc không có quyền truy cập."
                        RemoteFailure.QuotaExhausted -> "Hạn mức API đã hết (Quota Exhausted)."
                        is RemoteFailure.RateLimited -> "Đã chạm giới hạn yêu cầu (Rate limited). Vui lòng thử lại sau."
                        RemoteFailure.Timeout -> "Yêu cầu AI quá thời gian chờ (Timeout)."
                        RemoteFailure.Network -> "Lỗi kết nối mạng."
                        RemoteFailure.MalformedResponse -> "Mô hình AI trả về kết quả không đúng cấu trúc."
                        RemoteFailure.RequestRejected -> "Yêu cầu bị từ chối."
                        RemoteFailure.Service -> "Dịch vụ AI gặp sự cố. Vui lòng thử lại sau."
                    }
                    _uiState.update { it.copy(isGenerating = false, errorMessage = msg) }
                }
                is GenerateQuizResult.ValidationFailed -> {
                    _uiState.update { it.copy(isGenerating = false, errorMessage = "Đề thi do AI sinh không đạt kiểm định chất lượng phòng thi.") }
                }
            }
        }
    }

    fun deleteQuiz(quizId: Long) {
        viewModelScope.launch(dispatcher) {
            quizRepository.deleteQuiz(quizId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class QuizListViewModelFactory(
    private val campaignId: Long,
    private val quizRepository: QuizRepository,
    private val generateQuizUseCase: GenerateQuizUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuizListViewModel(
            campaignId,
            quizRepository,
            { cId, count -> generateQuizUseCase(cId, count) }
        ) as T
    }
}
