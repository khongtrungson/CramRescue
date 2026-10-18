package com.example.cramrescue.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.cramrescue.R
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
            .background(color = MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(top = 36.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Về màn hình trước"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemini API key",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when (uiState.isConfigured) {
                        null -> "Đang kiểm tra…"
                        true -> "Trạng thái: Đã cấu hình"
                        false -> "Trạng thái: Chưa cấu hình"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Key được mã hóa bằng Android Keystore và chỉ gửi tới Google Gemini API. " +
                            "Khi bạn phân tích, chỉ văn bản đã trích xuất (OCR từ ảnh/PDF và đoạn văn từ tệp Word) được gửi tới Gemini — không gửi ảnh hay tệp gốc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
            Row(
                modifier = Modifier.padding(0.dp),
                //verticalAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.exclamation_mark3),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    color = if (uiState.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Chưa có key? Tạo key miễn phí trong Google AI Studio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { uriHandler.openUri(AI_STUDIO_KEY_URL) }) {
            Text("Mở Google AI Studio", color = Color.Blue)
        }
    }
}
