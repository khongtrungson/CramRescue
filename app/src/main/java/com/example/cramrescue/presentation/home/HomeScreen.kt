package com.example.cramrescue.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.OnCodeRed

@Composable
fun HomeScreen(
    viewModelFactory: HomeViewModelFactory,
    onCreateCampaignClick: () -> Unit,
    onOpenCockpit: (Long) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Re-read on every entry: a Campaign may have been created, edited or deleted meanwhile.
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeContent(uiState, onCreateCampaignClick, onOpenCockpit)
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onCreateCampaignClick: () -> Unit,
    onOpenCockpit: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "CramRescue 48h",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Trợ Lý Sinh Tồn Phòng Thi",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            uiState.isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            uiState.isEmpty -> {
                Text(
                    text = "Chưa có chiến dịch nào. Tạo một chiến dịch cho môn thi sắp tới để bắt đầu.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onCreateCampaignClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Tạo chiến dịch sinh tồn")
                }
            }

            else -> {
                Button(onClick = onCreateCampaignClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Tạo chiến dịch mới")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("campaign-list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.upcoming.isNotEmpty()) {
                        item { SectionTitle("Sắp thi") }
                        items(uiState.upcoming, key = { it.id }) { row -> CampaignRow(row, onOpenCockpit) }
                    }
                    if (uiState.past.isNotEmpty()) {
                        item { SectionTitle("Đã qua giờ thi") }
                        items(uiState.past, key = { it.id }) { row -> CampaignRow(row, onOpenCockpit) }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun CampaignRow(row: CampaignRowUi, onOpenCockpit: (Long) -> Unit) {
    val reached = row.status == CampaignRowStatus.EXAM_REACHED
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("campaign-row-${row.id}")
            .clickable { onOpenCockpit(row.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (reached) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(row.archetype, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.status == CampaignRowStatus.EMERGENCY) {
                    Text(
                        text = "Khẩn cấp",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnCodeRed,
                        modifier = Modifier.background(CodeRed, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "${row.examLabel} · ${row.remainingLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
