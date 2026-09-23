package com.example.cramrescue.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local, app-owned storage for imported study materials (images and PDFs).
 *
 * Design decision (Milestone 4, extended in Milestone 6): every material — gallery image, camera
 * capture, or PDF — ends up as a plain file under [Context.getFilesDir], not a reference to
 * someone else's content URI. Picker URIs are read-only grants whose lifetime this app doesn't
 * control; copying the bytes once, at ingest time, is what guarantees OCR can reopen the exact
 * same source later, including after the process has restarted. Camera capture already writes
 * directly into this directory via a FileProvider URI, so it needs no extra copy.
 */
class MaterialFileStore(private val context: Context) {

    private val materialsDir: File
        get() = File(context.filesDir, "materials").apply { mkdirs() }

    /** Scratch space for PDF page renders, which are deleted as soon as OCR has read them. */
    private val renderCacheDir: File
        get() = File(context.cacheDir, "pdf-pages").apply { mkdirs() }

    /**
     * Copies the bytes behind [sourceUri] (e.g. a Photo Picker result) into app-private storage.
     *
     * Main-safe: callers run this from `viewModelScope`, which is `Dispatchers.Main.immediate`, so
     * this function owns its own dispatcher rather than making every caller remember to.
     */
    suspend fun copyFrom(sourceUri: Uri): String = copyInto(sourceUri, extension = "jpg")

    /** Copies a picked PDF into app-private storage, same ownership model as images. */
    suspend fun copyPdfFrom(sourceUri: Uri): String = copyInto(sourceUri, extension = "pdf")

    private suspend fun copyInto(sourceUri: Uri, extension: String): String =
        withContext(Dispatchers.IO) {
            val destination = File(materialsDir, "${UUID.randomUUID()}.$extension")
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Unable to open source URI: $sourceUri")
            input.use { stream ->
                destination.outputStream().use { output -> stream.copyTo(output) }
            }
            destination.absolutePath
        }

    /**
     * The provider's display name for [sourceUri] (e.g. the PDF filename), or null. Display only —
     * it is not unique and is never used as an identity or a filesystem path.
     */
    suspend fun readDisplayName(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    /** Deletes a file this store created. Used to roll back a failed/rejected import. */
    suspend fun delete(localPath: String) = withContext(Dispatchers.IO) {
        runCatching { File(localPath).delete() }
        Unit
    }

    /** A fresh, empty file for the camera app to write a full-resolution capture into. */
    fun createCameraOutputFile(): File = File(materialsDir, "${UUID.randomUUID()}.jpg")

    /** A temporary destination for one rendered PDF page; deleted right after OCR reads it. */
    fun createRenderedPageFile(): File = File(renderCacheDir, "${UUID.randomUUID()}.jpg")

    /** A content:// URI for [file] that the camera app can be granted temporary write access to. */
    fun uriForFile(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
