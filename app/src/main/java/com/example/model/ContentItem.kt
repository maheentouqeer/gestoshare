package com.example.model

import android.net.Uri

enum class ContentType {
    IMAGE,
    VIDEO,
    DOCUMENT_PDF,
    TEXT,
    URL,
    GENERIC_FILE
}

data class ContentItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val textContent: String? = null,
    val thumbnailUri: Uri? = null,
    val type: ContentType,
    val cachedFilePath: String? = null,
    val sha256Hash: String? = null
)
