package com.example.cramrescue.presentation.material.add

import com.example.cramrescue.domain.model.SourceType

data class MaterialPreviewItem(
    val documentId: Long,
    val path: String,
    val source: SourceType
)

data class AddMaterialUiState(
    val pendingCameraFilePath: String? = null,
    val addedMaterials: List<MaterialPreviewItem> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)
