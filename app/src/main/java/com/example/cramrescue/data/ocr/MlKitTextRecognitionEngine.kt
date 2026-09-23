package com.example.cramrescue.data.ocr

import android.content.Context
import android.net.Uri
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.ocr.RecognizedText
import com.example.cramrescue.domain.ocr.RecognizedTextBlock
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * ML Kit-backed [TextRecognitionEngine] — the only file allowed to reference ML Kit types.
 * Everything above this boundary (use cases, repositories, Room, UI) only ever sees the plain
 * [RecognizedText]/[BoundingBox] domain shapes.
 *
 * The recognizer client is created once and reused for the app's lifetime (this class is
 * constructed once, in the Application), matching ML Kit's guidance to avoid creating a new
 * client per recognition call.
 */
class MlKitTextRecognitionEngine(
    private val context: Context
) : TextRecognitionEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Main-safe: InputImage.fromFilePath decodes the file (and reads its EXIF) synchronously, and
    // callers reach this from viewModelScope (Dispatchers.Main.immediate), so the decode is moved
    // off the UI thread here. recognizer.process() is already asynchronous on ML Kit's own workers.
    override suspend fun recognize(imagePath: String): RecognizedText = withContext(Dispatchers.IO) {
        // InputImage.fromFilePath reads the file's EXIF orientation itself, so the recognizer
        // always processes an upright image. image.width/height below are exactly the pixel
        // space ML Kit used, which is what the returned bounding boxes are relative to.
        val image = InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
        val text = recognizer.process(image).awaitResult()

        val blocks = text.textBlocks.map { block ->
            val box = block.boundingBox
            RecognizedTextBlock(
                text = block.text,
                boundingBox = BoundingBox(
                    left = box?.left ?: 0,
                    top = box?.top ?: 0,
                    right = box?.right ?: 0,
                    bottom = box?.bottom ?: 0
                )
            )
        }

        RecognizedText(
            sourceWidthPx = image.width,
            sourceHeightPx = image.height,
            blocks = blocks
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
