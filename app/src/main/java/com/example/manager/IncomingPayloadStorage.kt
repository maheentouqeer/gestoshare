package com.example.manager

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.DiagnosticsRepository
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticCodes
import com.example.model.ErrorSeverity
import com.example.model.TransferItemMetadata
import com.example.model.TransferPipelineStage
import com.example.model.TransferVerificationException
import com.example.util.HashUtil
import com.google.android.gms.nearby.connection.Payload
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

sealed class IncomingPayloadSource {
    data class ContentUriSource(val uri: Uri) : IncomingPayloadSource()
    data class PfdSource(val pfd: ParcelFileDescriptor) : IncomingPayloadSource()
    data class AppPrivateFileSource(val file: File) : IncomingPayloadSource()
}

data class SavedPayloadResult(
    val shareableUri: Uri?,
    val permanentFile: File,
    val sha256Hash: String,
    val bytesCopied: Long,
    val mediaStoreExported: Boolean,
    val mediaStoreUri: Uri?,
    val mediaStoreNote: String?
)

/**
 * Modern Android Scoped-Storage compliant handler for incoming Nearby FILE payloads.
 *
 * CRITICAL FIX FOR PHYSICAL PHONES (Android 10–15 EACCES bug):
 * Always opens [Payload.File.asUri] via [ContentResolver.openInputStream] first, or
 * [Payload.File.asParcelFileDescriptor] second. On Android 10+ (API 29+), raw
 * `Payload.File.asJavaFile()` fallback is strictly disallowed and fails cleanly with [GS_FILE_002].
 *
 * Pipeline guarantees:
 * 1. Copies via URI/PFD stream to app-private staging file while computing SHA-256 in one pass.
 * 2. Verifies exact byte length against metadata (`GS-FILE-004`).
 * 3. Verifies SHA-256 digest strictly (`GS-FILE-003`), discarding corrupted staging bytes.
 * 4. Commits verified copy to a unique non-colliding file in app-private `files/received/`
 *    (never overwriting any existing verified file).
 * 5. Cleans up original Nearby staging URI/PFD ONLY AFTER app-private copy, verification,
 *    and app-private commit succeed (or inside error cleanup after stream closure).
 * 6. Exports verified copy to public `Downloads/GestoShare` via `MediaStore` (`IS_PENDING` 1 -> 0 on API 29+)
 *    ONLY after verification succeeds. Never returns raw `file://` URIs as shareable external URIs.
 */
class IncomingPayloadStorage(
    private val context: Context,
    private val diagnostics: DiagnosticsRepository? = null
) {
    companion object {
        private const val TAG = "IncomingPayloadStorage"
        private const val COPY_BUFFER_BYTES = 64 * 1024

        /**
         * Resolves a Nearby [Payload.Type.FILE] source safely for the given [sdkInt].
         *
         * Priority:
         * 1. [Payload.File.asUri] (`content://`)
         * 2. [Payload.File.asParcelFileDescriptor]
         * 3. On API < 29 only: readable [Payload.File.asJavaFile]
         *
         * On Android 10+ (`sdkInt >= Build.VERSION_CODES.Q`), if neither URI nor PFD is
         * exposed, explicitly throws [TransferVerificationException] with [DiagnosticCodes.GS_FILE_002]
         * rather than attempting POSIX file reads into `/Download/.nearby`.
         */
        fun resolvePayloadSourceOrThrow(
            payload: Payload,
            sdkInt: Int = Build.VERSION.SDK_INT
        ): IncomingPayloadSource {
            val payloadFile = payload.asFile()
                ?: throw TransferVerificationException(
                    code = DiagnosticCodes.GS_FILE_001,
                    category = DiagnosticCategory.FILE_IO,
                    message = "Nearby payload ${payload.id} is not a FILE payload.",
                    suggestedAction = "Retry transfer with a valid file payload."
                )

            return resolvePayloadComponentsOrThrow(
                payloadId = payload.id,
                uri = payloadFile.asUri(),
                pfd = payloadFile.asParcelFileDescriptor(),
                javaFile = payloadFile.asJavaFile(),
                sdkInt = sdkInt
            )
        }

        internal fun resolvePayloadComponentsOrThrow(
            payloadId: Long,
            uri: Uri?,
            pfd: ParcelFileDescriptor?,
            javaFile: File?,
            sdkInt: Int = Build.VERSION.SDK_INT
        ): IncomingPayloadSource {
            uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.let {
                return IncomingPayloadSource.ContentUriSource(it)
            }
            pfd?.let {
                return IncomingPayloadSource.PfdSource(it)
            }

            if (sdkInt >= Build.VERSION_CODES.Q) {
                throw TransferVerificationException(
                    code = DiagnosticCodes.GS_FILE_002,
                    category = DiagnosticCategory.FILE_IO,
                    message = "Scoped storage (API $sdkInt) disallows raw Nearby file path access${javaFile?.let { " (${it.name})" } ?: ""}; neither content:// URI nor ParcelFileDescriptor was provided.",
                    suggestedAction = "Payload must be opened via ContentResolver.openInputStream(uri) or ParcelFileDescriptor on Android 10+."
                )
            }

            if (javaFile != null && javaFile.canRead()) {
                return IncomingPayloadSource.AppPrivateFileSource(javaFile)
            }
            if (uri != null) {
                return IncomingPayloadSource.ContentUriSource(uri)
            }

            throw TransferVerificationException(
                code = DiagnosticCodes.GS_FILE_001,
                category = DiagnosticCategory.FILE_IO,
                message = "Nearby payload $payloadId exposed no readable URI, PFD, or readable legacy file.",
                suggestedAction = "Ensure payload uses ContentResolver URI stream and retry."
            )
        }

        fun resolvePayloadSource(
            payload: Payload,
            sdkInt: Int = Build.VERSION.SDK_INT
        ): IncomingPayloadSource? = runCatching {
            resolvePayloadSourceOrThrow(payload, sdkInt)
        }.getOrNull()

        fun safeDeletePayload(context: Context, payload: Payload) {
            val payloadFile = payload.asFile() ?: return
            payloadFile.asUri()?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
            }
            try {
                payloadFile.asParcelFileDescriptor()?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun copyVerifyAndSave(
        transferId: String,
        meta: TransferItemMetadata,
        source: IncomingPayloadSource,
        onStageChange: (TransferPipelineStage, String) -> Unit = { _, _ -> },
        customMediaStoreExporter: ((File, String, String) -> Pair<Uri?, String?>)? = null,
        customFileProviderResolver: ((File) -> Uri)? = null
    ): SavedPayloadResult {
        val resolver = context.contentResolver
        val safeName = HashUtil.sanitizeFileName(meta.name, "received_file")
        val verifyDir = File(context.cacheDir, "incoming_verify").apply { mkdirs() }
        if (meta.sizeBytes > 0L && verifyDir.usableSpace < meta.sizeBytes + 512L * 1024L) {
            throw TransferVerificationException(
                code = DiagnosticCodes.GS_FILE_005,
                category = DiagnosticCategory.STORAGE,
                message = "Insufficient storage space on receiver for '${safeName}' (needs ${meta.sizeBytes} bytes).",
                suggestedAction = "Free storage space and retry transfer."
            )
        }

        val tempVerifyFile = File(verifyDir, "${UUID.randomUUID().toString().take(8)}_$safeName")
        val digest = MessageDigest.getInstance("SHA-256")
        var totalCopied = 0L

        fun cleanupSourceQuietly() {
            when (source) {
                is IncomingPayloadSource.ContentUriSource -> {
                    if (source.uri.scheme == ContentResolver.SCHEME_CONTENT) {
                        try {
                            resolver.delete(source.uri, null, null)
                        } catch (e: Exception) {
                            Log.d(TAG, "Non-fatal cleanup notice for Nearby URI: ${e.message}")
                        }
                    }
                }
                is IncomingPayloadSource.PfdSource -> {
                    try {
                        source.pfd.close()
                    } catch (_: Exception) {
                    }
                }
                is IncomingPayloadSource.AppPrivateFileSource -> Unit
            }
        }

        try {
            // Stage 1: COPY FROM URI / PFD STREAM TO APP-PRIVATE STAGING
            onStageChange(
                TransferPipelineStage.COPYING_TO_PRIVATE_STORAGE,
                "Copying ${meta.name} via scoped storage stream..."
            )

            val openedStream: InputStream = try {
                when (source) {
                    is IncomingPayloadSource.ContentUriSource -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            source.uri.scheme != ContentResolver.SCHEME_CONTENT
                        ) {
                            throw TransferVerificationException(
                                code = DiagnosticCodes.GS_FILE_002,
                                category = DiagnosticCategory.FILE_IO,
                                message = "Scoped storage (API ${Build.VERSION.SDK_INT}) rejects unsupported ${source.uri.scheme ?: "unknown"}:// URI source; content:// URI required.",
                                suggestedAction = "Open incoming Nearby payload using a content:// URI via ContentResolver.openInputStream() or ParcelFileDescriptor."
                            )
                        }
                        resolver.openInputStream(source.uri)
                            ?: throw TransferVerificationException(
                                code = DiagnosticCodes.GS_FILE_001,
                                category = DiagnosticCategory.FILE_IO,
                                message = "ContentResolver returned null InputStream for payload URI ${source.uri}",
                                suggestedAction = "Retry the transfer; Nearby staging URI was inaccessible."
                            )
                    }
                    is IncomingPayloadSource.PfdSource -> {
                        ParcelFileDescriptor.AutoCloseInputStream(source.pfd)
                    }
                    is IncomingPayloadSource.AppPrivateFileSource -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            source.file.absolutePath.contains(".nearby")
                        ) {
                            throw TransferVerificationException(
                                code = DiagnosticCodes.GS_FILE_002,
                                category = DiagnosticCategory.FILE_IO,
                                message = "Blocked raw .nearby FileInputStream on Android Q+; URI stream required.",
                                suggestedAction = "Use ContentResolver.openInputStream(uri) instead of raw POSIX path."
                            )
                        }
                        FileInputStream(source.file)
                    }
                }
            } catch (e: TransferVerificationException) {
                throw e
            } catch (e: SecurityException) {
                throw TransferVerificationException(
                    code = DiagnosticCodes.GS_FILE_002,
                    category = DiagnosticCategory.FILE_IO,
                    message = "Scoped storage EACCES opening incoming payload stream: ${e.message}",
                    suggestedAction = "Payload must be opened via ContentResolver.openInputStream(uri).",
                    cause = e
                )
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isEacces = msg.contains("EACCES", ignoreCase = true) ||
                    msg.contains("Permission denied", ignoreCase = true)
                throw TransferVerificationException(
                    code = if (isEacces) DiagnosticCodes.GS_FILE_002 else DiagnosticCodes.GS_FILE_001,
                    category = DiagnosticCategory.FILE_IO,
                    message = "Failed opening incoming stream for ${meta.name}: $msg",
                    suggestedAction = "Ensure payload uses ContentResolver URI stream and retry.",
                    cause = e
                )
            }

            try {
                openedStream.use { input ->
                    FileOutputStream(tempVerifyFile).use { output ->
                        val buf = ByteArray(COPY_BUFFER_BYTES)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            digest.update(buf, 0, read)
                            totalCopied += read
                        }
                        output.fd.sync()
                    }
                }
            } catch (e: TransferVerificationException) {
                throw e
            } catch (e: Exception) {
                throw TransferVerificationException(
                    code = DiagnosticCodes.GS_FILE_001,
                    category = DiagnosticCategory.FILE_IO,
                    message = "Failed copying incoming stream for '${meta.name}': ${e.message ?: e.javaClass.simpleName}",
                    suggestedAction = "Keep devices nearby and retry transfer.",
                    cause = e
                )
            }

            // Stage 2: SIZE & CRYPTOGRAPHIC SHA-256 VERIFICATION (before cleaning source or committing permanent file)
            onStageChange(
                TransferPipelineStage.VERIFYING_INTEGRITY,
                "Verifying ${totalCopied}B and SHA-256 for ${meta.name}..."
            )

            if (meta.sizeBytes > 0L && totalCopied != meta.sizeBytes) {
                throw TransferVerificationException(
                    code = DiagnosticCodes.GS_FILE_004,
                    category = DiagnosticCategory.VERIFICATION,
                    message = "Byte length mismatch for '${meta.name}': expected ${meta.sizeBytes}B, got ${totalCopied}B",
                    suggestedAction = "Keep sender and receiver nearby and retry transfer."
                )
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (meta.sha256.isNotBlank() && !HashUtil.verifyHash(actualHash, meta.sha256)) {
                throw TransferVerificationException(
                    code = DiagnosticCodes.GS_FILE_003,
                    category = DiagnosticCategory.VERIFICATION,
                    message = "SHA-256 integrity mismatch for '${meta.name}' (expected ${meta.sha256.take(12)}..., got ${actualHash.take(12)}...). Corrupted payload discarded.",
                    suggestedAction = "Payload corrupted in transit. Re-send file from sender."
                )
            }

            // Stage 3: COMMIT VERIFIED COPY INTO APP-PRIVATE RECEIVED DIRECTORY (never overwrites existing verified file)
            val receivedDir = File(context.filesDir, "received").apply { mkdirs() }
            val permanentFile = synchronized(this) {
                createUniqueFile(receivedDir, safeName)
            }
            val commitTemp = File(receivedDir, ".commit_${UUID.randomUUID().toString().take(8)}_$safeName")
            try {
                FileInputStream(tempVerifyFile).use { input ->
                    FileOutputStream(commitTemp).use { output ->
                        input.copyTo(output, COPY_BUFFER_BYTES)
                        output.fd.sync()
                    }
                }
                if (!commitTemp.renameTo(permanentFile)) {
                    FileInputStream(commitTemp).use { input ->
                        FileOutputStream(permanentFile).use { output ->
                            input.copyTo(output, COPY_BUFFER_BYTES)
                            output.fd.sync()
                        }
                    }
                    commitTemp.delete()
                }
            } catch (e: Exception) {
                commitTemp.delete()
                if (permanentFile.exists() && permanentFile.length() == 0L) {
                    permanentFile.delete()
                }
                throw e
            } finally {
                tempVerifyFile.delete()
            }

            // Clean up Nearby staging URI/PFD ONLY AFTER copy + verification + app-private commit succeed
            cleanupSourceQuietly()

            val privateShareUri: Uri? = try {
                val resolved = customFileProviderResolver?.invoke(permanentFile)
                    ?: FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        permanentFile
                    )
                if (resolved.scheme == ContentResolver.SCHEME_CONTENT) {
                    resolved
                } else {
                    Log.w(TAG, "FileProvider returned non-content URI scheme (${resolved.scheme}); suppressing raw file URI.")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "FileProvider.getUriForFile failed for ${permanentFile.name}", e)
                diagnostics?.recordError(
                    code = DiagnosticCodes.GS_FILE_008,
                    category = DiagnosticCategory.FILE_IO,
                    sourceComponent = TAG,
                    operation = "resolveFileProviderUri",
                    summary = "FileProvider could not generate content:// URI for '${permanentFile.name}'; raw file:// fallback suppressed.",
                    technicalMessage = e.localizedMessage ?: e.javaClass.simpleName,
                    severity = ErrorSeverity.WARNING,
                    relatedTransferId = transferId
                )
                null
            }

            // Stage 4: EXPORT VERIFIED FILE TO PUBLIC Downloads/GestoShare (ONLY AFTER SHA-256 PASSES)
            onStageChange(
                TransferPipelineStage.EXPORTING_TO_DOWNLOADS,
                "Exporting verified ${permanentFile.name} to Downloads/GestoShare..."
            )

            var exportedUri: Uri? = null
            var exportFailureNote: String? = null
            var exportOk = false

            if (customMediaStoreExporter != null) {
                try {
                    val (u, note) = customMediaStoreExporter(permanentFile, permanentFile.name, meta.mimeType)
                    exportedUri = u
                    exportFailureNote = note
                    exportOk = (u != null && note == null)
                } catch (e: Exception) {
                    exportOk = false
                    exportFailureNote = e.localizedMessage ?: e.javaClass.simpleName
                }
            } else {
                val res = exportToPublicDownloads(permanentFile, permanentFile.name, meta.mimeType)
                exportedUri = res.first
                exportFailureNote = res.second
                exportOk = (res.second == null && (exportedUri != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q))
            }

            if (!exportOk) {
                val warnMsg = "Saved & verified in app storage, but MediaStore Downloads export failed: ${exportFailureNote ?: "unknown error"}"
                Log.w(TAG, warnMsg)
                diagnostics?.recordError(
                    code = DiagnosticCodes.GS_FILE_006,
                    category = DiagnosticCategory.STORAGE,
                    sourceComponent = TAG,
                    operation = "exportToPublicDownloads",
                    summary = warnMsg,
                    technicalMessage = exportFailureNote ?: warnMsg,
                    relatedTransferId = transferId
                )
            } else {
                diagnostics?.recordLog(
                    category = DiagnosticCategory.FILE_IO,
                    tag = TAG,
                    message = "Verified & exported '${permanentFile.name}' ($totalCopied B, sha=${actualHash.take(12)}...)"
                )
            }

            // Never leak a file:// URI as shareableUri: only content:// URIs from MediaStore or FileProvider
            val safeExportShareUri = exportedUri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
            val safeShareableUri = safeExportShareUri ?: privateShareUri

            return SavedPayloadResult(
                shareableUri = safeShareableUri,
                permanentFile = permanentFile,
                sha256Hash = actualHash,
                bytesCopied = totalCopied,
                mediaStoreExported = exportOk,
                mediaStoreUri = exportedUri,
                mediaStoreNote = if (exportOk) "Exported to Downloads/GestoShare" else exportFailureNote
            )
        } catch (e: Exception) {
            tempVerifyFile.delete()
            cleanupSourceQuietly()
            throw e
        }
    }

    fun exportToPublicDownloads(sourceFile: File, displayName: String, mimeType: String): Pair<Uri?, String?> {
        return try {
            val cleanMime = mimeType.ifBlank { "application/octet-stream" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, cleanMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GestoShare")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null to "MediaStore insert returned null URI"

                try {
                    val outStream = resolver.openOutputStream(uri)
                        ?: run {
                            resolver.delete(uri, null, null)
                            return null to "MediaStore openOutputStream returned null"
                        }
                    outStream.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out, COPY_BUFFER_BYTES)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri to null
                } catch (e: Exception) {
                    try {
                        resolver.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                    null to (e.localizedMessage ?: e.javaClass.simpleName)
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "GestoShare"
                ).apply { mkdirs() }
                val dest = createUniqueFile(downloadsDir, displayName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output, COPY_BUFFER_BYTES)
                    }
                }
                // Return FileProvider content:// URI if covered by file_paths.xml, or null instead of file:// URI
                val contentUri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                }.getOrNull()
                contentUri to null
            }
        } catch (e: Exception) {
            null to (e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    fun createUniqueFile(dir: File, originalName: String): File {
        val safeName = HashUtil.sanitizeFileName(originalName, "received_file")
        var candidate = File(dir, safeName)
        if (!candidate.exists()) return candidate
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_($counter)$ext")
            counter++
        }
        return candidate
    }
}
