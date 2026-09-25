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
import com.example.cramrescue.domain.model.MAX_WORD_DOCUMENT_WORDS
import com.example.cramrescue.domain.usecase.word.ImportWordDocumentUseCase
import com.example.cramrescue.domain.usecase.word.WordImportResult
import com.example.cramrescue.domain.word.WordExtractionFailure
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
    private val importWordDocument: ImportWordDocumentUseCase,
    private val campaignId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMaterialUiState())
    val uiState: StateFlow<AddMaterialUiState> = _uiState.asStateFlow()

    /**
     * Reloads this campaign's materials from Room, the source of truth: persisted ones from an
     * earlier session, minus any deleted from the detail screen in the meantime.
     */
    fun refresh() {
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
            _uiState.update { it.copy(addedMaterials = existing) }
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

    /**
     * Imports a picked Word file (FR-3): copy into app storage, extract its text locally, then save
     * the document and its text atomically. Any failure removes the copy, so nothing is left behind.
     */
    fun onWordSelected(uri: Uri?) {
        if (uri == null) return // user cancelled — no-op

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val displayName = materialFileStore.readDisplayName(uri)
            val path = runCatching { materialFileStore.copyWordFrom(uri) }.getOrElse {
                _uiState.update { state -> state.copy(isSaving = false, errorMessage = "Không thể đọc tệp Word đã chọn.") }
                return@launch
            }

            when (val result = importWordDocument(campaignId, path, displayName)) {
                is WordImportResult.Imported -> _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        addedMaterials = state.addedMaterials +
                            MaterialPreviewItem(result.documentId, path, SourceType.WORD, displayName, pageCount = 1)
                    )
                }
                else -> {
                    materialFileStore.delete(path)
                    _uiState.update { state -> state.copy(isSaving = false, errorMessage = wordErrorMessage(result)) }
                }
            }
        }
    }

    private fun wordErrorMessage(result: WordImportResult): String = when (result) {
        is WordImportResult.Imported -> ""
        WordImportResult.NoText -> "Tài liệu Word không có nội dung văn bản."
        is WordImportResult.TooManyWords ->
            "Tài liệu Word có ${result.wordCount} từ, vượt giới hạn $MAX_WORD_DOCUMENT_WORDS từ. Hãy tách tài liệu và chọn phần trọng tâm."
        WordImportResult.SaveFailed -> "Không thể lưu tài liệu Word."
        is WordImportResult.Unreadable -> when (result.reason) {
            WordExtractionFailure.LEGACY_DOC_OR_ENCRYPTED ->
                "Tệp là định dạng Word cũ (.doc) hoặc đã đặt mật khẩu — chưa được hỗ trợ. Hãy lưu lại dưới dạng .docx (không mật khẩu)."
            WordExtractionFailure.NOT_DOCX -> "Tệp đã chọn không phải tài liệu Word .docx hợp lệ."
            WordExtractionFailure.MALFORMED -> "Tệp Word bị hỏng hoặc không đọc được."
            WordExtractionFailure.TOO_LARGE -> "Tệp Word quá lớn để xử lý an toàn."
            WordExtractionFailure.EMPTY_FILE -> "Tệp Word trống."
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
    private val importWordDocument: ImportWordDocumentUseCase,
    private val campaignId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddMaterialViewModel(materialFileStore, sourceDocumentRepository, pdfPageRenderer, importWordDocument, campaignId) as T
    }
}
