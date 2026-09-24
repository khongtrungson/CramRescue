package com.example.cramrescue.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.padding
import com.example.cramrescue.presentation.analysis.AI_STUDIO_KEY_URL

@Composable
fun GeminiSettingsScreen(
    viewModelFactory: GeminiSettingsViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: GeminiSettingsViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    // The typed key lives only in this composition: not in the ViewModel, not in saved state,
    // and it is cleared as soon as it has been validated and stored.
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Quay lại") }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Gemini API key (BYOK)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (uiState.isConfigured) {
                null -> "Đang kiểm tra…"
                true -> "Trạng thái: Đã cấu hình"
                false -> "Trạng thái: Chưa cấu hình"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Key được mã hóa bằng Android Keystore và chỉ gửi tới Google Gemini API. " +
                "Khi bạn phân tích, văn bản OCR (không phải ảnh hay tệp PDF) được gửi tới Gemini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(if (uiState.isConfigured == true) "Key mới (thay thế key hiện tại)" else "API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            enabled = !uiState.isBusy,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.save(keyInput) { keyInput = "" } },
            enabled = !uiState.isBusy && keyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kiểm tra & lưu key")
        }

        if (uiState.isConfigured == true) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::checkConnection,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kiểm tra kết nối")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = viewModel::remove,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xóa key", color = MaterialTheme.colorScheme.error)
            }
        }

        if (uiState.isBusy) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        val message = uiState.message
        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = if (uiState.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Chưa có key? Tạo key miễn phí trong Google AI Studio (key mới được tạo là auth key).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { uriHandler.openUri(AI_STUDIO_KEY_URL) }) {
            Text("Mở Google AI Studio")
        }
    }
}
