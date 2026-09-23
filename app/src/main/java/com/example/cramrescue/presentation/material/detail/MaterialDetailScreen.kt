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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.presentation.material.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MaterialDetailScreen(
    viewModelFactory: MaterialDetailViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: MaterialDetailViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

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
                val path = uiState.documentPath
                if (path != null) {
                    MaterialFullPreview(
                        path = path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.extractText() },
                    enabled = !uiState.isProcessingOcr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isProcessingOcr) "Đang xử lý..." else "Trích xuất văn bản")
                }

                if (uiState.isProcessingOcr) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                val error = uiState.errorMessage
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }

                val text = uiState.recognizedText
                if (text != null) {
                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.isLowText) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "Văn bản nhận diện được quá ít (${uiState.characterCount} ký tự). " +
                                    "Ảnh có thể bị mờ hoặc chụp thiếu — hãy thử chụp lại.",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (uiState.hasNoText) {
                        Text(
                            text = "Không nhận diện được văn bản nào trong ảnh này.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = "Văn bản nhận diện (${uiState.blockCount} khối, ${uiState.characterCount} ký tự)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = text.ifBlank { "(trống)" },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
