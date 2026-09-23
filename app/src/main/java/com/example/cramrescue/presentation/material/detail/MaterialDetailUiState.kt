package com.example.cramrescue.presentation.material.detail

data class MaterialDetailUiState(
    val isLoadingDocument: Boolean = true,
    val documentPath: String? = null,
    val documentNotFound: Boolean = false,
    val isProcessingOcr: Boolean = false,
    val recognizedText: String? = null,
    val characterCount: Int = 0,
    val blockCount: Int = 0,
    val isLowText: Boolean = false,
    val hasNoText: Boolean = false,
    val errorMessage: String? = null
)
