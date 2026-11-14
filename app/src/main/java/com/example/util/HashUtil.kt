package com.example.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

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
            e.printStackTrace()
            ""
        }
    }

    fun verifyHash(actualHash: String, expectedHash: String): Boolean {
        if (actualHash.isBlank() || expectedHash.isBlank()) return false
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}
