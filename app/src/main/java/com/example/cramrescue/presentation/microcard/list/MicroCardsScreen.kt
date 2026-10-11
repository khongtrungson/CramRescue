package com.example.cramrescue.presentation.microcard.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.presentation.analysis.TriageBadge
import com.example.cramrescue.presentation.recall.label
import com.example.cramrescue.ui.theme.CodeGreen
import com.example.cramrescue.ui.theme.CodeGreenContainer
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.CodeRedContainer
import com.example.cramrescue.ui.theme.CodeYellow
import com.example.cramrescue.ui.theme.CodeYellowContainer
import com.example.cramrescue.ui.theme.OnCodeRedContainer
import com.example.cramrescue.ui.theme.OnCodeYellowContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroCardsScreen(
    viewModelFactory: MicroCardsViewModelFactory,
    onBack: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onStartStudy: () -> Unit
) {
    val viewModel: MicroCardsViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Local reads only, on every (re)entry — never a Gemini request.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Thẻ ghi nhớ (Micro-Card)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.campaignName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {

                when (uiState.blocker) {
                    MicroCardsBlocker.NO_MATERIAL -> Notice("Chưa có tài liệu. Hãy thêm tài liệu từ Buồng lái sinh tồn.")
                    MicroCardsBlocker.NO_OCR ->
                        Notice("Tài liệu chưa có văn bản OCR. Mở từng tài liệu và bấm \"Trích xuất văn bản\".")
                    MicroCardsBlocker.NO_ANALYSIS -> {
                        Notice("Chưa có phân tích AI. Thẻ ghi nhớ được tạo từ các mục Code Red / Code Yellow của phân tích.")
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenAnalysis,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Mở Phân tích AI", fontWeight = FontWeight.Bold)
                        }
                    }
                    null -> Unit
                }

                if (uiState.blocker == null) {
                    if (uiState.analysisStale) {
                        Notice(
                            "Tài liệu của chiến dịch đã thay đổi sau lần phân tích gần nhất. " +
                                "Hãy phân tích lại, sau đó tạo lại thẻ."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenAnalysis,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Mở Phân tích AI", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (uiState.cardsStale) {
                        Notice("Các thẻ dưới đây được tạo từ một bản phân tích cũ hơn.")
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Primary Study CTA
                    if (uiState.hasCards) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val cardCount = uiState.redCards.size + uiState.yellowCards.size
                        Button(
                            onClick = onStartStudy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.cardsStale) "Ôn tập $cardCount thẻ (từ phân tích cũ)" else "Bắt đầu ôn tập ($cardCount thẻ)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Secondary CTA: Regenerate Cards (Outlined to avoid competing with Study CTA)
                    if (uiState.canCreateCards && (!uiState.hasCards || uiState.cardsStale)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::createCards,
                            enabled = !uiState.isCreating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.hasCards) "Tạo lại thẻ từ phân tích hiện tại" else "Tạo thẻ ghi nhớ từ phân tích",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val message = uiState.message
                if (message != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (uiState.messageIsError) CodeRedContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.messageIsError) OnCodeRedContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (uiState.hasCards) {
                    CardSection("CODE RED — buộc phải thuộc", uiState.redCards, onOpenCard)
                    CardSection("CODE YELLOW — kéo điểm an toàn", uiState.yellowCards, onOpenCard)
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Code Green: đã lược bỏ ${uiState.discardedGreenCount} khái niệm râu ria/nâng cao (không tạo thẻ).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
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
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = OnCodeYellowContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = OnCodeYellowContainer
            )
        }
    }
}

@Composable
private fun CardSection(title: String, cards: List<MicroCardItemUi>, onOpenCard: (Long) -> Unit) {
    if (cards.isEmpty()) return
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "$title (${cards.size})",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    cards.forEach { card ->
        Spacer(modifier = Modifier.height(10.dp))
        MicroCardItem(card, onClick = { onOpenCard(card.id) })
    }
}

@Composable
private fun MicroCardItem(card: MicroCardItemUi, onClick: () -> Unit) {
    val borderColor = if (card.triage == TriageLevel.CODE_RED) {
        CodeRed.copy(alpha = 0.5f)
    } else {
        CodeYellow.copy(alpha = 0.5f)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TriageBadge(card.triage)

                card.latestAssessment?.let { assessment ->
                    val (statusColor, containerColor) = when (assessment) {
                        RecallAssessment.MASTERED -> Pair(CodeGreen, CodeGreenContainer)
                        RecallAssessment.PARTIAL -> Pair(CodeYellow, CodeYellowContainer)
                        RecallAssessment.NOT_REMEMBERED -> Pair(CodeRed, CodeRedContainer)
                    }
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = assessment.label(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            card.bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            card.latestAssessment?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Lần ôn gần nhất: ${it.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nguồn: ${card.sourceLabel}" + if (card.sourcesAvailable) "" else " (nguồn đã thay đổi)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
