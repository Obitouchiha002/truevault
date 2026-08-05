package com.truevault.core.model

/**
 * A file the user picked, described by what the platform told us about it.
 *
 * [uriToken] is the string form of the content URI. It is treated as sensitive: it can reveal a file
 * name, an album, or a folder structure, so it is never logged, never put in a navigation route, and
 * stored only encrypted.
 *
 * Every other field is what the provider reported. A provider is allowed to report nothing, so
 * [sizeBytes] can be unknown and [mimeType] can be missing — both are handled rather than assumed.
 */
data class SelectedSource(
    val uriToken: String,
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val isFromPhotoPicker: Boolean,
) {
    val category: MimeCategory get() = mimeCategoryOf(mimeType, displayName)

    val hasKnownSize: Boolean get() = sizeBytes != null && sizeBytes >= 0

    /** Never includes the file name; safe to put in a log line or an error message. */
    fun describeSafely(): String = "source(category=$category, size=${sizeBytes ?: -1})"
}

/**
 * Classifies a file for the vault's categories.
 *
 * MIME type is trusted first because it comes from the platform. The extension is only a fallback
 * for providers that report `application/octet-stream` or nothing at all — which is common enough
 * that ignoring the case would put half of a user's documents in "Other".
 */
fun mimeCategoryOf(mimeType: String?, displayName: String?): MimeCategory {
    val type = mimeType?.lowercase()

    when {
        type == null || type == "application/octet-stream" -> Unit
        type.startsWith("image/") -> return MimeCategory.PHOTO
        type.startsWith("video/") -> return MimeCategory.VIDEO
        type.startsWith("audio/") -> return MimeCategory.AUDIO
        type == "application/pdf" -> return MimeCategory.DOCUMENT
        type.startsWith("text/") -> return MimeCategory.DOCUMENT
        type in OFFICE_MIME_TYPES -> return MimeCategory.DOCUMENT
        type in ARCHIVE_MIME_TYPES -> return MimeCategory.ARCHIVE
    }

    val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when (extension) {
        in PHOTO_EXTENSIONS -> MimeCategory.PHOTO
        in VIDEO_EXTENSIONS -> MimeCategory.VIDEO
        in AUDIO_EXTENSIONS -> MimeCategory.AUDIO
        in DOCUMENT_EXTENSIONS -> MimeCategory.DOCUMENT
        in ARCHIVE_EXTENSIONS -> MimeCategory.ARCHIVE
        else -> MimeCategory.OTHER
    }
}

private val OFFICE_MIME_TYPES = setOf(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/rtf",
)

private val ARCHIVE_MIME_TYPES = setOf(
    "application/zip",
    "application/x-tar",
    "application/gzip",
    "application/x-7z-compressed",
    "application/vnd.rar",
    "application/x-rar-compressed",
)

private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng", "avif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "amr")
private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "odt", "ods", "csv")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
