package com.example.cramrescue.presentation.material.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// requirements.md FR-2: reject a gallery selection of more than 20 images.
const val MAX_GALLERY_SELECTION = 20

class AddMaterialViewModel(
    private val materialFileStore: MaterialFileStore,
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val campaignId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMaterialUiState())
    val uiState: StateFlow<AddMaterialUiState> = _uiState.asStateFlow()

    init {
        // Show materials already persisted for this campaign (from an earlier session/process),
        // not just ones added in this ViewModel instance's lifetime.
        viewModelScope.launch {
            val existing = sourceDocumentRepository.getDocumentsForCampaign(campaignId)
                .map { MaterialPreviewItem(it.localPath, it.sourceType) }
            if (existing.isNotEmpty()) {
                _uiState.update { it.copy(addedMaterials = existing) }
            }
        }
    }

    /** Creates a fresh destination file and returns the content:// URI to hand to the camera app. */
    fun prepareCameraCaptureUri(): Uri {
        val file = materialFileStore.createCameraOutputFile()
        _uiState.update { it.copy(pendingCameraFilePath = file.absolutePath, errorMessage = null) }
        return materialFileStore.uriForFile(file)
    }

    fun onCameraUnavailable() {
        _uiState.update { it.copy(pendingCameraFilePath = null, errorMessage = "Không tìm thấy ứng dụng camera trên thiết bị.") }
    }

    fun onCameraResult(success: Boolean) {
        val path = _uiState.value.pendingCameraFilePath
        _uiState.update { it.copy(pendingCameraFilePath = null) }
        if (!success || path == null) return // user cancelled or capture failed — silently stay on screen

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching { saveDocument(path, SourceType.CAMERA) }
                .onSuccess {
                    _uiState.update {
                        it.copy(isSaving = false, addedMaterials = it.addedMaterials + MaterialPreviewItem(path, SourceType.CAMERA))
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "Không thể lưu ảnh đã chụp.") }
                }
        }
    }

    fun onGallerySelected(uris: List<Uri>) {
        if (uris.isEmpty()) return // user cancelled — no-op

        if (uris.size > MAX_GALLERY_SELECTION) {
            _uiState.update {
                it.copy(errorMessage = "Chỉ được chọn tối đa $MAX_GALLERY_SELECTION ảnh mỗi lần. Vui lòng chọn lại.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val newItems = mutableListOf<MaterialPreviewItem>()
            var anyFailed = false

            for (uri in uris) {
                val result = runCatching {
                    val path = materialFileStore.copyFrom(uri)
                    saveDocument(path, SourceType.GALLERY)
                    path
                }
                result.onSuccess { path -> newItems += MaterialPreviewItem(path, SourceType.GALLERY) }
                    .onFailure { anyFailed = true }
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    addedMaterials = it.addedMaterials + newItems,
                    errorMessage = if (anyFailed) "Một số ảnh không thể lưu." else null
                )
            }
        }
    }

    private suspend fun saveDocument(path: String, sourceType: SourceType) {
        sourceDocumentRepository.saveDocument(
            SourceDocument(
                campaignId = campaignId,
                sourceType = sourceType,
                localPath = path,
                createdAt = Instant.now()
            )
        )
    }
}

class AddMaterialViewModelFactory(
    private val materialFileStore: MaterialFileStore,
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val campaignId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddMaterialViewModel(materialFileStore, sourceDocumentRepository, campaignId) as T
    }
}
