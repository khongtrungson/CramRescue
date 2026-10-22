package com.example.cramrescue.presentation.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.R
import com.example.cramrescue.domain.model.StudyReminder
import com.example.cramrescue.presentation.analysis.AI_STUDIO_KEY_URL
import java.util.Locale

@Composable
fun GeminiSettingsScreen(
    viewModelFactory: GeminiSettingsViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: GeminiSettingsViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    var keyInput by remember { mutableStateOf("") }
    var pendingActionOnPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingActionOnPermission?.invoke()
        }
        pendingActionOnPermission = null
    }

    val requestPermissionIfNeeded: (() -> Unit) -> Unit = { onGranted ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onGranted()
            } else {
                pendingActionOnPermission = onGranted
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onGranted()
        }
    }

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
                    text = "Cài đặt",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: Nhắc nhở học tập (Nhiều mốc thông báo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Nhắc nhở học tập",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cài đặt các mốc thông báo ôn thi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    requestPermissionIfNeeded {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.addReminder(hour, minute)
                            },
                            StudyReminder.DEFAULT_HOUR,
                            StudyReminder.DEFAULT_MINUTE,
                            true
                        ).show()
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Thêm giờ")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.reminders.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Chưa có thông báo nào. Nhấn '+ Thêm giờ' để đặt lịch nhắc học tập.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.reminders.forEach { reminder ->
                    ReminderItemCard(
                        reminder = reminder,
                        onToggle = { enabled ->
                            if (enabled) {
                                requestPermissionIfNeeded { viewModel.toggleReminder(reminder.id, true) }
                            } else {
                                viewModel.toggleReminder(reminder.id, false)
                            }
                        },
                        onTimeClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> viewModel.updateReminderTime(reminder.id, h, m) },
                                reminder.hour,
                                reminder.minute,
                                true
                            ).show()
                        },
                        onMessageChange = { msg ->
                            viewModel.updateReminderMessage(reminder.id, msg)
                        },
                        onDelete = {
                            viewModel.deleteReminder(reminder.id)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: Gemini API Key
        Text(
            text = "Google Gemini API Key",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

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

        Spacer(modifier = Modifier.height(20.dp))

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

@Composable
private fun ReminderItemCard(
    reminder: StudyReminder,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
    onMessageChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Khung giờ hiển thị
                val formattedTime = String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    reminder.hour,
                    reminder.minute
                )

                OutlinedButton(
                    onClick = onTimeClick,
                    enabled = reminder.enabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reminder.enabled,
                        onCheckedChange = onToggle
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa thông báo",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (reminder.enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                var localMessage by remember(reminder.message) {
                    mutableStateOf(reminder.message)
                }

                OutlinedTextField(
                    value = localMessage,
                    onValueChange = {
                        localMessage = it
                        onMessageChange(it)
                    },
                    label = { Text("Lời nhắn", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text(StudyReminder.DEFAULT_MESSAGE) },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
