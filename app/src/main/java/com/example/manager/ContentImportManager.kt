package com.example.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

interface ContentImportManager {
    suspend fun importFromUri(context: Context, uri: Uri): ContentItem?
    suspend fun importMultipleFromUris(context: Context, uris: List<Uri>): List<ContentItem>
    fun createFromText(text: String): ContentItem
    fun createFromUrl(url: String): ContentItem
    fun formatFileSize(bytes: Long): String
    fun clearOutgoingCache(context: Context)
}

class DefaultContentImportManager : ContentImportManager {

    override suspend fun importFromUri(context: Context, uri: Uri): ContentItem? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // Persist URI read access when supported (e.g., SAF ACTION_OPEN_DOCUMENT)
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ephemeral Sharesheet / Photo Picker URIs do not grant persistable access;
                // copying to local app cache below guarantees uninterrupted read access.
            } catch (_: Exception) {
            }

            var fileName = "GestoShare_${System.currentTimeMillis()}"
            var fileSize = 0L
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val safeName = sanitizeFileName(fileName)
            val cacheDir = File(context.cacheDir, "outgoing").apply { mkdirs() }
            val cachedFile = File(cacheDir, "${UUID.randomUUID().toString().take(8)}_$safeName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return@withContext null

            if (fileSize <= 0L) {
                fileSize = cachedFile.length()
            }

            val sha256 = HashUtil.calculateSha256(cachedFile)
            val contentType = detectContentType(mimeType, safeName)

            ContentItem(
                uri = uri,
                name = safeName,
                mimeType = mimeType,
                sizeBytes = fileSize,
                thumbnailUri = if (contentType == ContentType.IMAGE || contentType == ContentType.VIDEO) uri else null,
                type = contentType,
                cachedFilePath = cachedFile.absolutePath,
                sha256Hash = sha256
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun importMultipleFromUris(context: Context, uris: List<Uri>): List<ContentItem> {
        val results = mutableListOf<ContentItem>()
        for (uri in uris) {
            importFromUri(context, uri)?.let { results.add(it) }
        }
        return results
    }

    override fun createFromText(text: String): ContentItem {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val sha256 = sha256Bytes(bytes)
        val snippet = if (text.length > 28) text.take(28) + "..." else text
        return ContentItem(
            name = "Note: $snippet",
            mimeType = "text/plain",
            sizeBytes = bytes.size.toLong(),
            textContent = text,
            type = ContentType.TEXT,
            sha256Hash = sha256
        )
    }

    override fun createFromUrl(url: String): ContentItem {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url
        val bytes = cleanUrl.toByteArray(StandardCharsets.UTF_8)
        val sha256 = sha256Bytes(bytes)
        return ContentItem(
            name = cleanUrl.replace("https://", "").replace("http://", "").take(32),
            mimeType = "text/uri-list",
            sizeBytes = bytes.size.toLong(),
            textContent = cleanUrl,
            type = ContentType.URL,
            sha256Hash = sha256
        )
    }

    override fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    override fun clearOutgoingCache(context: Context) {
        try {
            File(context.cacheDir, "outgoing").deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").ifBlank { "shared_file" }
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun detectContentType(mimeType: String, fileName: String): ContentType {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        val lowerName = fileName.lowercase(Locale.ROOT)
        return when {
            lowerMime.startsWith("image/") -> ContentType.IMAGE
            lowerMime.startsWith("video/") -> ContentType.VIDEO
            lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> ContentType.DOCUMENT_PDF
            lowerMime.startsWith("text/") || lowerName.endsWith(".txt") -> ContentType.TEXT
            lowerMime.contains("uri") || lowerName.startsWith("http") -> ContentType.URL
            else -> ContentType.GENERIC_FILE
        }
    }
}
