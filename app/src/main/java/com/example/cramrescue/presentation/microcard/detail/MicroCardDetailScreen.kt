package com.example.cramrescue.presentation.microcard.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.presentation.analysis.TriageBadge

@Composable
fun MicroCardDetailScreen(
    viewModelFactory: MicroCardDetailViewModelFactory,
    onBack: () -> Unit,
    onOpenSource: (documentId: Long, pageIndex: Int) -> Unit
) {
    val viewModel: MicroCardDetailViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Thẻ ghi nhớ") }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.notFound -> Text("Không tìm thấy thẻ.", color = MaterialTheme.colorScheme.error)
            else -> {
                uiState.triage?.let { TriageBadge(it) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (uiState.isStale) {
                    Text(
                        text = "Thẻ này thuộc một bản phân tích cũ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                uiState.bullets.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val notes = uiState.notes
                if (notes != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ghi chú mở rộng",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Nguồn trong tài liệu",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (uiState.sources.isEmpty()) {
                    Text(
                        text = "Tài liệu nguồn đã bị xóa khỏi chiến dịch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uiState.sources.forEach { source ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSource(source.documentId, source.pageIndex) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${source.location} · ${source.blockCount} khối — Đối chiếu nguồn ›",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            source.snippets.forEach { snippet ->
                                Text(
                                    text = snippet?.let { "“$it”" } ?: "(khối văn bản này đã thay đổi hoặc bị xóa)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
