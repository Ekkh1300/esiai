package com.sokhan.chat.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a backup payload to storage WITHOUT the SAF document picker.
 *
 * The normal Settings flow asks the user where to save (CreateDocument); the
 * agent's `export_backup` tool has no Activity to launch a picker from, so it
 * writes to the shared Downloads collection instead (or the app-specific
 * directory before Android 10) and returns the location.
 */
class BackupFileWriter(private val context: Context) {

    /** @return a human-readable location the model can quote back. */
    suspend fun write(json: String): String = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "esi-agent-backup-$stamp.json"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/ESI Agent"
                )
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create $name in Downloads")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open $name for writing")
            "${Environment.DIRECTORY_DOWNLOADS}/ESI Agent/$name"
        } else {
            val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
            val file = File(dir, name)
            file.writeText(json)
            file.absolutePath
        }
    }
}
