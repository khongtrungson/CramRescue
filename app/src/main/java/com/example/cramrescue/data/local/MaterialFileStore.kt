package com.example.cramrescue.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Local, app-owned storage for imported study material images.
 *
 * Design decision (Milestone 4): every material — whether picked from the Gallery/Photo Picker
 * or captured with the Camera — ends up as a plain file under [Context.getFilesDir], not a
 * reference to someone else's content URI. Gallery/Photo Picker URIs are read-only grants whose
 * lifetime isn't something this app controls; copying the bytes once, at ingest time, is what
 * guarantees Milestone 5 (OCR) can reopen the exact same image later, including after the app
 * process has restarted. Camera capture already writes directly into this same directory (via a
 * FileProvider URI we hand to the camera app), so no extra copy is needed for that path.
 */
class MaterialFileStore(private val context: Context) {

    private val materialsDir: File
        get() = File(context.filesDir, "materials").apply { mkdirs() }

    /** Copies the bytes behind [sourceUri] (e.g. a Photo Picker result) into app-private storage. */
    fun copyFrom(sourceUri: Uri): String {
        val destination = File(materialsDir, "${UUID.randomUUID()}.jpg")
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open source URI: $sourceUri")
        input.use { stream ->
            destination.outputStream().use { output -> stream.copyTo(output) }
        }
        return destination.absolutePath
    }

    /** A fresh, empty file for the camera app to write a full-resolution capture into. */
    fun createCameraOutputFile(): File {
        return File(materialsDir, "${UUID.randomUUID()}.jpg")
    }

    /** A content:// URI for [file] that the camera app can be granted temporary write access to. */
    fun uriForFile(file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
