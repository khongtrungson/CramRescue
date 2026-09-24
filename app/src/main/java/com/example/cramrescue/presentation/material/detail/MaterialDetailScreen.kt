package com.example.cramrescue.presentation.material.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.presentation.material.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MaterialDetailScreen(
    viewModelFactory: MaterialDetailViewModelFactory,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val viewModel: MaterialDetailViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onDeleted()
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Xóa tài liệu?") },
            text = { Text("Tài liệu và dữ liệu OCR liên quan sẽ bị xóa khỏi chiến dịch. Tệp gốc trên máy của bạn không bị ảnh hưởng.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.delete()
                }) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Hủy") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Thêm tài liệu")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoadingDocument -> CircularProgressIndicator()

            uiState.documentNotFound -> Text(
                text = "Không tìm thấy tài liệu.",
                color = MaterialTheme.colorScheme.error
            )

            else -> {
                if (uiState.isPdf) {
                    PdfSummary(
                        displayName = uiState.displayName,
                        pageCount = uiState.pageCount,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val path = uiState.documentPath
                    if (path != null) {
                        MaterialFullPreview(
                            path = path,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.extractText() },
                    enabled = !uiState.isProcessingOcr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isProcessingOcr) "Đang xử lý..." else "Trích xuất văn bản")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Disabled while OCR runs, so OCR can never re-insert rows for a deleted document.
                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = uiState.canDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (uiState.isDeleting) "Đang xóa..." else "Xóa tài liệu",
                        color = if (uiState.canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.isProcessingOcr) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    if (uiState.totalPages > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Đang xử lý trang ${uiState.processedPages.coerceAtLeast(1)}/${uiState.totalPages}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                val error = uiState.errorMessage
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }

                if (uiState.hasResult) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Văn bản nhận diện (${uiState.pages.size} trang, ${uiState.totalCharacterCount} ký tự)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    uiState.pages.forEach { page ->
                        Spacer(modifier = Modifier.height(16.dp))
                        OcrPageSection(page = page)
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrPageSection(page: OcrPageUi) {
    Text(
        text = "Trang ${page.pageNumber} — ${page.blockCount} khối, ${page.characterCount} ký tự",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (page.isLowText) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
            Text(
                text = if (page.characterCount == 0) {
                    "Không nhận diện được văn bản nào ở trang này."
                } else {
                    "Văn bản nhận diện được quá ít (${page.characterCount} ký tự). " +
                        "Trang này có thể bị mờ hoặc chụp thiếu."
                },
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (page.text.isNotBlank()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
            Text(
                text = page.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PdfSummary(displayName: String?, pageCount: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tài liệu PDF",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayName ?: "(không có tên tệp)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$pageCount trang",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MaterialFullPreview(path: String, modifier: Modifier = Modifier) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmap(path, 1024) }.getOrNull()
        }
    }
    val bitmap = bitmapState.value

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
