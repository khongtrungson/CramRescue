package com.example.cramrescue.presentation.material.add

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.presentation.material.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PDF_MIME_TYPE = "application/pdf"

@Composable
fun AddMaterialScreen(
    viewModelFactory: AddMaterialViewModelFactory,
    onOpenMaterial: (Long) -> Unit,
    onBackToCockpit: () -> Unit
) {
    val viewModel: AddMaterialViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Reload from Room on every (re)entry, e.g. after a material was deleted in its detail screen.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_SELECTION)
    ) { uris -> viewModel.onGallerySelected(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> viewModel.onCameraResult(success) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.onPdfSelected(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        TextButton(onClick = onBackToCockpit) {
            Text("← Buồng lái sinh tồn")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Thêm tài liệu",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Chọn nguồn tài liệu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Chọn từ thư viện")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                val uri = viewModel.prepareCameraCaptureUri()
                try {
                    cameraLauncher.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    viewModel.onCameraUnavailable()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Chụp bằng camera")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { pdfLauncher.launch(arrayOf(PDF_MIME_TYPE)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Chọn tệp PDF")
        }

        if (uiState.isSaving) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        val error = uiState.errorMessage
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (uiState.addedMaterials.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Đã thêm vào chiến dịch hiện tại (${uiState.addedMaterials.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow {
                items(uiState.addedMaterials) { material ->
                    val tileModifier = Modifier
                        .padding(end = 8.dp)
                        .size(96.dp)
                        .clickable { onOpenMaterial(material.documentId) }

                    if (material.isPdf) {
                        PdfTile(pageCount = material.pageCount, modifier = tileModifier)
                    } else {
                        MaterialThumbnail(path = material.path, modifier = tileModifier)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.addedMaterials.isNotEmpty()) {
            Button(onClick = onBackToCockpit, modifier = Modifier.fillMaxWidth()) {
                Text("Về Buồng lái sinh tồn")
            }
        }
    }
}

@Composable
private fun PdfTile(pageCount: Int, modifier: Modifier = Modifier) {
    val shape = remember { RoundedCornerShape(8.dp) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PDF",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$pageCount trang",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MaterialThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmap(path, 512) }.getOrNull()
        }
    }

    val bitmap = bitmapState.value
    val shape = remember { RoundedCornerShape(8.dp) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
