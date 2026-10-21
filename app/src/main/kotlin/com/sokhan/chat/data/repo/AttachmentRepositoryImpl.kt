package com.sokhan.chat.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.sokhan.chat.core.AppLog
import com.sokhan.chat.core.Ids
import com.sokhan.chat.domain.model.Attachment
import com.sokhan.chat.domain.model.AttachmentKind
import com.sokhan.chat.domain.repository.AttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.sokhan.chat.data.db.DbMappers.toEntity
import com.sokhan.chat.data.db.DbMappers.toDomain
import com.sokhan.chat.data.db.dao.AttachmentDao
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Copies pickers' content into app-private files (which keeps remote payloads
 * reproducible, since Android content URIs are single-use permission grants).
 * Images get down-scaled/converted to JPEG 85 at the longest side of 1568px —
 * a practical sweet spot for vision APIs.
 */
class AttachmentRepositoryImpl(
    private val appContext: Context,
    private val dao: AttachmentDao
) : AttachmentRepository {

    private val storageRoot: File get() = File(appContext.filesDir, "attachments")

    override fun observeByMessage(messageId: String): Flow<List<Attachment>> =
        dao.observeByMessage(messageId).map { list -> list.map { it.toDomain() } }

    override fun observeForConversation(conversationId: String): Flow<List<Attachment>> =
        dao.observeForConversation(conversationId).map { list -> list.map { it.toDomain() } }

    override suspend fun assign(messageId: String, attachmentIds: List<String>) {
        if (attachmentIds.isEmpty()) return
        dao.assignToMessage(ids = attachmentIds, messageId = messageId)
    }

    override suspend fun byId(attachmentId: String): Attachment? =
        dao.get(attachmentId)?.toDomain()

    override suspend fun byMessage(messageId: String): List<Attachment> =
        dao.byMessage(messageId).map { it.toDomain() }

    override suspend fun createFromContentUri(
        uri: Uri,
        sourceName: String
    ): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val (displayName, declaredSize) = probe(uri, sourceName)
            val mime = resolver.getType(uri) ?: guessMime(displayName) ?: "application/octet-stream"
            val nameProbe = classify(mime, displayName)
            val kind = nameProbe
            val sizeLimit = MAX_BYTES[kind] ?: MAX_TXT_BYTES
            if (declaredSize > 0 && declaredSize > sizeLimit) {
                error("FILE_TOO_LARGE")
            }
            val id = Ids.newId()
            val safeName = displayName.sanitizeName()
            val storedPath = "attachments/$id.jpg"
            File(appContext.filesDir, storedPath).parentFile?.mkdirs()
            val target = File(appContext.filesDir, storedPath)

            when (kind) {
                AttachmentKind.IMAGE -> encodeCompressedBitmap(resolver, uri, target)
                else -> resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("UNREADABLE")
            }

            val attachment = Attachment(
                id = id,
                messageId = null,
                kind = kind,
                name = safeName,
                mimeType = mime,
                relativePath = storedPath,
                sizeBytes = target.length(),
                createdAt = System.currentTimeMillis()
            )
            dao.upsert(attachment.toEntity())
            attachment
        }
    }

    override suspend fun delete(attachmentId: String) {
        dao.get(attachmentId)?.let { entity ->
            runCatching {
                resolveFile(entity.toDomain())?.deleteRecursively()
            }
            dao.delete(attachmentId)
        }
    }

    override suspend fun resolveFile(attachment: Attachment): File? = attachmentFile(attachment)

    override fun attachmentFile(attachment: Attachment): File? {
        val path = attachment.relativePath
        if (path.isNullOrBlank()) return null
        val file = File(appContext.filesDir, path)
        return if (file.exists()) file else null
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun classify(mime: String, name: String): AttachmentKind = when {
        mime.startsWith("image/") -> AttachmentKind.IMAGE
        mime.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", true) -> AttachmentKind.PDF
        else -> AttachmentKind.TEXT_FILE
    }

    private fun guessMime(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun probe(uri: Uri, fallback: String): Pair<String, Long> {
        var displayName = fallback
        var size = -1L
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                        displayName = cursor.getString(it) ?: displayName
                    }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                        val candidate = cursor.getLong(it)
                        if (candidate > 0) size = candidate
                    }
                }
            }
        }
        return Pair(displayName, size)
    }

    private fun encodeCompressedBitmap(resolver: android.content.ContentResolver, uri: Uri, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
            ?: error("unreadable image")

        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / 2 >= TARGET_LONG_SIDE && height / 2 >= TARGET_LONG_SIDE) {
            sample *= 2
            width /= 2
            height /= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: error("unreadable image")

        val scale = maxOf(1.0, maxOf(decoded.width, decoded.height) / TARGET_LONG_SIDE.toDouble())
        val targetWidth = (decoded.width / scale).toInt().coerceAtLeast(1)
        val targetHeight = (decoded.height / scale).toInt().coerceAtLeast(1)
        val bitmap = if (targetWidth < decoded.width) {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        } else {
            decoded
        }
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (bitmap !== decoded) {
            bitmap.recycle()
        }
        decoded.recycle()
    }

    private fun String.sanitizeName(): String = this
        .replace(Regex("[\\r\\n]"), "_")
        .replace(Regex("[^A-Za-z0-9._\\-()\\u0600-\\u06FF ]"), "_")
        .take(80)
        .ifBlank { "attachment.bin" }

    private companion object {
        const val TARGET_LONG_SIDE = 1568
        const val JPEG_QUALITY = 85
        const val MAX_IMG_BYTES = 25L * 1024 * 1024
        const val MAX_PDF_BYTES = 20L * 1024 * 1024
        const val MAX_TXT_BYTES = 1L * 1024 * 1024
        val MAX_BYTES = mapOf(
            AttachmentKind.IMAGE to MAX_IMG_BYTES,
            AttachmentKind.PDF to MAX_PDF_BYTES,
            AttachmentKind.TEXT_FILE to MAX_TXT_BYTES
        )
    }
}
