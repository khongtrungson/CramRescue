package com.example.cramrescue.presentation.grounding

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.grounding.GroundedSource
import com.example.cramrescue.domain.grounding.GroundingResult
import com.example.cramrescue.domain.grounding.GroundingUnavailableReason
import com.example.cramrescue.domain.usecase.grounding.GetGroundedSourceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Longest side of the displayed bitmap: sharp on phones/tablets, bounded memory (~16 MB worst case).
private const val MAX_DISPLAY_DIMENSION_PX = 2048

sealed interface SourceGroundingUiState {
    data object Loading : SourceGroundingUiState
    data class Success(val source: GroundedSource, val bitmap: Bitmap) : SourceGroundingUiState
    data class Unavailable(val reason: GroundingUnavailableReason) : SourceGroundingUiState
    data class RenderError(val failure: SourceLoadFailure) : SourceGroundingUiState
}

/** Read-only: resolves persisted identity, then loads the page once. No OCR, no AI, no network. */
class SourceGroundingViewModel(
    private val campaignId: Long,
    private val cardId: Long,
    private val documentId: Long,
    private val pageIndex: Int,
    private val getGroundedSource: GetGroundedSourceUseCase,
    private val loader: SourcePageLoader
) : ViewModel() {

    private val _uiState = MutableStateFlow<SourceGroundingUiState>(SourceGroundingUiState.Loading)
    val uiState: StateFlow<SourceGroundingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = when (val result = getGroundedSource(campaignId, cardId, documentId, pageIndex)) {
                is GroundingResult.Unavailable -> SourceGroundingUiState.Unavailable(result.reason)
                is GroundingResult.Available -> when (val load = loader.load(result.source, MAX_DISPLAY_DIMENSION_PX)) {
                    is SourceLoadResult.Loaded -> SourceGroundingUiState.Success(result.source, load.bitmap)
                    is SourceLoadResult.Failed -> SourceGroundingUiState.RenderError(load.failure)
                }
            }
        }
    }

    override fun onCleared() {
        (_uiState.value as? SourceGroundingUiState.Success)?.bitmap?.recycle()
    }
}

class SourceGroundingViewModelFactory(
    private val campaignId: Long,
    private val cardId: Long,
    private val documentId: Long,
    private val pageIndex: Int,
    private val getGroundedSource: GetGroundedSourceUseCase,
    private val loader: SourcePageLoader
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SourceGroundingViewModel(campaignId, cardId, documentId, pageIndex, getGroundedSource, loader) as T
    }
}
