package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    private const val TAG = "HashUtil"
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    private const val MAX_SAFE_FILENAME_LENGTH = 120

    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ""
        FileInputStream(file).use { calculateSha256(it) }
    }

    suspend fun calculateSha256(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { calculateSha256(it) } ?: ""
    }

    suspend fun calculateSha256(inputStream: InputStream): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            val hashBytes = digest.digest()
            val hexString = StringBuilder()
            for (b in hashBytes) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed computing SHA-256 checksum", e)
            ""
        }
    }

    fun verifyHash(actualHash: String, expectedHash: String): Boolean {
        if (actualHash.isBlank() || expectedHash.isBlank()) return false
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Strictly sanitizes peer-supplied or SAF filenames to prevent path traversal (`..`, `/`, `\`),
     * hidden dotfiles, control characters, and overlong filenames.
     */
    fun sanitizeFileName(rawName: String?, fallback: String = "shared_file"): String {
        val baseFallback = fallback.ifBlank { "shared_file" }
        if (rawName.isNullOrBlank()) return baseFallback

        // Strip any directory traversal prefixes from either Unix or Windows separators
        var leaf = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()

        // Replace control chars and disallowed filename characters
        leaf = leaf.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")

        // Eliminate any parent directory traversal sequences ("..")
        while (leaf.contains("..")) {
            leaf = leaf.replace("..", "_")
        }

        // Prevent hidden dotfiles or leading/trailing dots/spaces
        leaf = leaf.trimStart('.', ' ', '_').trimEnd(' ', '.')

        if (leaf.isBlank() || leaf == "." || leaf == "..") {
            return baseFallback
        }

        if (leaf.length > MAX_SAFE_FILENAME_LENGTH) {
            val dotIdx = leaf.lastIndexOf('.')
            if (dotIdx in 1 until leaf.length - 1) {
                val ext = leaf.substring(dotIdx).take(16)
                val stem = leaf.substring(0, (MAX_SAFE_FILENAME_LENGTH - ext.length).coerceAtLeast(8))
                leaf = "$stem$ext"
            } else {
                leaf = leaf.take(MAX_SAFE_FILENAME_LENGTH)
            }
        }
        return leaf.ifBlank { baseFallback }
    }
}
