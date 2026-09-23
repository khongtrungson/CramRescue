package com.example.cramrescue.presentation.material.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.domain.model.MAX_DOCUMENT_PAGES
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.pdf.PdfPageRenderer
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
    private val pdfPageRenderer: PdfPageRenderer,
    private val campaignId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMaterialUiState())
    val uiState: StateFlow<AddMaterialUiState> = _uiState.asStateFlow()

    init {
        // Show materials already persisted for this campaign (from an earlier session/process),
        // not just ones added in this ViewModel instance's lifetime.
        viewModelScope.launch {
            val existing = sourceDocumentRepository.getDocumentsForCampaign(campaignId)
                .map {
                    MaterialPreviewItem(
                        documentId = it.id,
                        path = it.localPath,
                        source = it.sourceType,
                        displayName = it.displayName,
                        pageCount = it.effectivePageCount
                    )
                }
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
                .onSuccess { id ->
                    _uiState.update {
                        it.copy(isSaving = false, addedMaterials = it.addedMaterials + MaterialPreviewItem(id, path, SourceType.CAMERA))
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
                    val id = saveDocument(path, SourceType.GALLERY)
                    id to path
                }
                result.onSuccess { (id, path) -> newItems += MaterialPreviewItem(id, path, SourceType.GALLERY) }
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

    /**
     * Imports a picked PDF: copy into app storage, read its page count, enforce the 20-page
     * product limit, then persist it as a SourceDocument owned by this campaign. The copied file
     * is removed again if the PDF turns out to be unreadable or oversized, so a rejected import
     * leaves nothing behind.
     */
    fun onPdfSelected(uri: Uri?) {
        if (uri == null) return // user cancelled — no-op

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            val displayName = materialFileStore.readDisplayName(uri)

            val path = runCatching { materialFileStore.copyPdfFrom(uri) }.getOrElse {
                _uiState.update { state -> state.copy(isSaving = false, errorMessage = "Không thể đọc tệp PDF đã chọn.") }
                return@launch
            }

            val pageCount = runCatching { pdfPageRenderer.withDocument(path) { it.pageCount } }.getOrElse {
                materialFileStore.delete(path)
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = "Không mở được tệp PDF này. Tệp có thể bị hỏng hoặc được đặt mật khẩu."
                    )
                }
                return@launch
            }

            if (pageCount > MAX_DOCUMENT_PAGES) {
                materialFileStore.delete(path)
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = "Tệp PDF có $pageCount trang, vượt quá giới hạn $MAX_DOCUMENT_PAGES trang. " +
                            "Vui lòng tách tệp và chọn tối đa $MAX_DOCUMENT_PAGES trang trọng tâm."
                    )
                }
                return@launch
            }

            runCatching { saveDocument(path, SourceType.PDF, pageCount = pageCount, displayName = displayName) }
                .onSuccess { id ->
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            addedMaterials = state.addedMaterials +
                                MaterialPreviewItem(id, path, SourceType.PDF, displayName, pageCount)
                        )
                    }
                }
                .onFailure {
                    materialFileStore.delete(path)
                    _uiState.update { state -> state.copy(isSaving = false, errorMessage = "Không thể lưu tệp PDF.") }
                }
        }
    }

    private suspend fun saveDocument(
        path: String,
        sourceType: SourceType,
        pageCount: Int = 1,
        displayName: String? = null
    ): Long {
        return sourceDocumentRepository.saveDocument(
            SourceDocument(
                campaignId = campaignId,
                sourceType = sourceType,
                localPath = path,
                createdAt = Instant.now(),
                pageCount = pageCount,
                displayName = displayName
            )
        )
    }
}

class AddMaterialViewModelFactory(
    private val materialFileStore: MaterialFileStore,
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val pdfPageRenderer: PdfPageRenderer,
    private val campaignId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddMaterialViewModel(materialFileStore, sourceDocumentRepository, pdfPageRenderer, campaignId) as T
    }
}
