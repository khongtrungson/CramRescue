package com.example.cramrescue.presentation.campaign.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CampaignConfirmationScreen(
    viewModelFactory: CampaignConfirmationViewModelFactory,
    onContinueToCockpit: (Long) -> Unit,
    onBackToHome: () -> Unit
) {
    val viewModel: CampaignConfirmationViewModel = viewModel(factory = viewModelFactory)
    val campaign by viewModel.campaign.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Chiến dịch đã được tạo!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(24.dp))

        val current = campaign
        if (current == null) {
            Text(text = "Đang tải...", color = MaterialTheme.colorScheme.onBackground)
        } else {
            Text(text = current.name, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = current.subjectArchetype.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            Text(
                text = "Giờ thi: " + formatter.format(current.examDateTime.atZone(ZoneId.systemDefault())),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (current != null) {
            Button(onClick = { onContinueToCockpit(current.id) }) {
                Text("Vào Buồng lái sinh tồn")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        TextButton(onClick = onBackToHome) {
            Text("Về màn hình chính")
        }
    }
}
