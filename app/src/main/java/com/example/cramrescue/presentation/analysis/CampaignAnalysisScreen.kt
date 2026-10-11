package com.example.cramrescue.presentation.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.ui.theme.CodeGreenContainer
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.CodeRedContainer
import com.example.cramrescue.ui.theme.CodeYellow
import com.example.cramrescue.ui.theme.CodeYellowContainer
import com.example.cramrescue.ui.theme.OnCodeGreenContainer
import com.example.cramrescue.ui.theme.OnCodeRed
import com.example.cramrescue.ui.theme.OnCodeRedContainer
import com.example.cramrescue.ui.theme.OnCodeYellow
import com.example.cramrescue.ui.theme.OnCodeYellowContainer

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Phân tích Pareto 80/20",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.campaignName} · ${uiState.archetypeName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Về Buồng lái sinh tồn"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Material & AI Status Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DỮ LIỆU ĐẦU VÀO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Gemini status pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        if (uiState.hasCredential) CodeGreenContainer else CodeYellowContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (uiState.hasCredential) "Gemini: Đã cấu hình" else "Gemini: Chưa cấu hình",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.hasCredential) OnCodeGreenContainer else OnCodeYellowContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MaterialStatus(uiState)

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onOpenSettings) {
                                Text(if (uiState.hasCredential) "Cài đặt key" else "Cấu hình key", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (uiState.hasCredential) viewModel.analyze() else onOpenSettings() },
                    enabled = !uiState.isAnalyzing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            uiState.isAnalyzing -> "Đang phân tích…"
                            !uiState.hasCredential -> "Cấu hình Gemini API key"
                            uiState.result != null -> "Phân tích lại"
                            else -> "Phân tích bằng AI"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.isAnalyzing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Đang gửi văn bản OCR tới Gemini và kiểm tra kết quả…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val error = uiState.errorMessage
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = CodeRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = error, color = OnCodeRedContainer, style = MaterialTheme.typography.bodyMedium)
                            if (uiState.result != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kết quả phân tích trước đó vẫn được giữ nguyên.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.errorNeedsSettings) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onOpenSettings) { Text("Mở cài đặt Gemini API key") }
                            }
                        }
                    }
                }

                val result = uiState.result
                if (result != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onOpenMicroCards,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xem thẻ ghi nhớ (Micro-Card)", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    AnalysisResult(result)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
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
        Text(text = "• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalysisResult(result: AnalysisResultUi) {
    if (result.isStale) {
        Surface(
            color = CodeYellowContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = OnCodeYellowContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tài liệu của chiến dịch đã thay đổi sau lần phân tích này. Kết quả dưới đây chưa bao gồm thay đổi — hãy phân tích lại.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnCodeYellowContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 3 Pareto KPI Cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KpiCard(
            title = "Code Red",
            subtitle = "Buộc thuộc",
            count = "${result.redCount}",
            color = CodeRed,
            containerColor = CodeRedContainer,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            title = "Code Yellow",
            subtitle = "Kéo điểm",
            count = "${result.yellowCount}",
            color = CodeYellow,
            containerColor = CodeYellowContainer,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            title = "Code Green",
            subtitle = "Đã lược bỏ",
            count = "${result.discardedGreenCount}",
            color = MaterialTheme.colorScheme.tertiary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Executive Summary Strategy Card
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CHIẾN LƯỢC ÔN TẬP PARETO 80/20",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${result.redCount} Code Red · ${result.yellowCount} Code Yellow · " +
                    "Đã lược bỏ ${result.discardedGreenCount} khái niệm râu ria/nâng cao (Code Green)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Phân tích lúc ${result.generatedAtText} từ ${result.analyzedPageCount} trang" +
                    if (result.lowTextPageCount > 0) " (${result.lowTextPageCount} trang OCR thấp)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "DANH SÁCH KHÁI NIỆM TRỌNG TÂM",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )

    result.priorities.forEach { priority ->
        Spacer(modifier = Modifier.height(12.dp))
        PriorityItem(priority)
    }
}

@Composable
private fun KpiCard(
    title: String,
    subtitle: String,
    count: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriorityItem(priority: PriorityUi) {
    val borderColor = if (priority.triage == TriageLevel.CODE_RED) {
        CodeRed.copy(alpha = 0.5f)
    } else {
        CodeYellow.copy(alpha = 0.5f)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TriageBadge(priority.triage)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = priority.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            priority.bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            val notes = priority.notes
            if (notes != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ghi chú mở rộng: $notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FindInPage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Nguồn: ${priority.sourceLabel}" + if (priority.sourcesResolved) "" else " (nguồn đã thay đổi)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
