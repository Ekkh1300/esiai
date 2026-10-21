package com.sokhan.chat.domain.model

/** What kind of binary the user attached. */
enum class AttachmentKind { IMAGE, TEXT_FILE, PDF }

/**
 * One attachment. Files are copied into app-private storage when attached and
 * referenced by [relativePath] inside the files dir.
 */
data class Attachment(
    val id: String,
    val messageId: String? = null,
    val kind: AttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val sizeBytes: Long = 0L,
    val createdAt: Long = 0L
)
