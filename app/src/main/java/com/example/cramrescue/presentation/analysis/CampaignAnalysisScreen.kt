package com.example.cramrescue.presentation.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.CodeYellow
import com.example.cramrescue.ui.theme.OnCodeRed
import com.example.cramrescue.ui.theme.OnCodeYellow

@Composable
fun CampaignAnalysisScreen(
    viewModelFactory: CampaignAnalysisViewModelFactory,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMicroCards: () -> Unit
) {
    val viewModel: CampaignAnalysisViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Re-read local state every time the screen is (re)entered, e.g. back from BYOK settings.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Buồng lái sinh tồn") }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
            return@Column
        }

        Text(
            text = "Phân tích Pareto 80/20",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${uiState.campaignName} · ${uiState.archetypeName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        MaterialStatus(uiState)

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (uiState.hasCredential) "Gemini: Đã cấu hình" else "Gemini: Chưa cấu hình",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(if (uiState.hasCredential) "Cài đặt key" else "Cấu hình key")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { if (uiState.hasCredential) viewModel.analyze() else onOpenSettings() },
            enabled = !uiState.isAnalyzing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    uiState.isAnalyzing -> "Đang phân tích…"
                    !uiState.hasCredential -> "Cấu hình Gemini API key"
                    uiState.result != null -> "Phân tích lại"
                    else -> "Phân tích bằng AI"
                }
            )
        }

        if (uiState.isAnalyzing) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                text = "Đang gửi văn bản OCR tới Gemini và kiểm tra kết quả…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        val error = uiState.errorMessage
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
            if (uiState.result != null) {
                Text(
                    text = "Kết quả phân tích trước đó vẫn được giữ nguyên.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.errorNeedsSettings) {
                TextButton(onClick = onOpenSettings) { Text("Mở cài đặt Gemini API key") }
            }
        }

        val result = uiState.result
        if (result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenMicroCards, modifier = Modifier.fillMaxWidth()) {
                Text("Xem thẻ ghi nhớ (Micro-Card)")
            }
            Spacer(modifier = Modifier.height(24.dp))
            AnalysisResult(result)
        }
    }
}

@Composable
private fun MaterialStatus(uiState: CampaignAnalysisUiState) {
    val lines = buildList {
        add("${uiState.usablePageCount} trang có văn bản OCR")
        if (uiState.wordDocumentCount > 0) add("${uiState.wordDocumentCount} tài liệu Word (văn bản đoạn văn)")
        if (uiState.lowTextPageCount > 0) add("${uiState.lowTextPageCount} trang chất lượng OCR thấp (vẫn gửi, có đánh dấu)")
        if (uiState.emptyPageCount > 0) add("${uiState.emptyPageCount} trang trống (bỏ qua)")
        if (uiState.documentsWithoutOcr > 0) add("${uiState.documentsWithoutOcr} tài liệu chưa trích xuất văn bản (bỏ qua)")
    }
    lines.forEach {
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalysisResult(result: AnalysisResultUi) {
    if (result.isStale) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Tài liệu của chiến dịch đã thay đổi sau lần phân tích này. Kết quả dưới đây chưa bao gồm thay đổi — hãy phân tích lại.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "${result.redCount} Code Red · ${result.yellowCount} Code Yellow · " +
            "Đã lược bỏ ${result.discardedGreenCount} khái niệm râu ria/nâng cao (Code Green)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
    Text(
        text = "Phân tích lúc ${result.generatedAtText} từ ${result.analyzedPageCount} trang" +
            if (result.lowTextPageCount > 0) " (${result.lowTextPageCount} trang OCR thấp)" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    result.priorities.forEach { priority ->
        Spacer(modifier = Modifier.height(16.dp))
        PriorityItem(priority)
    }
}

@Composable
private fun PriorityItem(priority: PriorityUi) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TriageBadge(priority.triage)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = priority.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            priority.bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            val notes = priority.notes
            if (notes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ghi chú mở rộng: $notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nguồn: ${priority.sourceLabel}" + if (priority.sourcesResolved) "" else " (nguồn đã thay đổi)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun TriageBadge(triage: TriageLevel) {
    // Semantic triage colors as small signals only (CONTRIBUTING.md "UI / Design System").
    val (label, container, content) = when (triage) {
        TriageLevel.CODE_RED -> Triple("CODE RED", CodeRed, OnCodeRed)
        TriageLevel.CODE_YELLOW -> Triple("CODE YELLOW", CodeYellow, OnCodeYellow)
        TriageLevel.CODE_GREEN -> Triple("CODE GREEN", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
    }
    BadgeSurface(label, container, content)
}

@Composable
private fun BadgeSurface(label: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
