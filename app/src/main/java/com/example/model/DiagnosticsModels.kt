package com.example.model

import android.os.Build
import com.example.BuildConfig
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.UUID

enum class ErrorSeverity(val label: String) {
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    CRITICAL("CRITICAL")
}

enum class DiagnosticCategory(val label: String) {
    GESTURE("Gesture"),
    CAMERA("Camera"),
    MODEL("AI Model"),
    NEARBY("Nearby P2P"),
    TRANSFER("Transfer"),
    FILE_IO("File I/O"),
    VERIFICATION("SHA-256"),
    STORAGE("Storage"),
    SYSTEM("System")
}

data class DiagnosticErrorEvent(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val title: String,
    val category: DiagnosticCategory,
    val severity: ErrorSeverity,
    val summary: String,
    val technicalMessage: String,
    val stackTrace: String,
    val sourceComponent: String,
    val operation: String,
    val deviceInfoJson: String,
    val stateSnapshotJson: String,
    val relatedTransferId: String? = null,
    val relatedEndpointId: String? = null,
    val suggestedAction: String,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
)

data class DiagnosticLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, WARN, ERROR
    val category: DiagnosticCategory = DiagnosticCategory.SYSTEM,
    val tag: String,
    val message: String,
    val metadata: String? = null
)

object DiagnosticCodes {
    // Gesture / Camera / Model
    const val GS_GESTURE_001 = "GS-GESTURE-001" // Missing MediaPipe task or model asset
    const val GS_GESTURE_002 = "GS-GESTURE-002" // ONNX Runtime initialization or inference error
    const val GS_GESTURE_003 = "GS-GESTURE-003" // CameraX / ImageAnalysis failure

    // Networking / Nearby
    const val GS_NET_001 = "GS-NET-001" // Nearby permission / adapter unavailable
    const val GS_NET_002 = "GS-NET-002" // Endpoint discovery or connection failure
    const val GS_NET_003 = "GS-NET-003" // Endpoint disconnected unexpectedly
    const val GS_NET_004 = "GS-NET-004" // Receiver declined or handshake rejected

    // File I/O & Verification
    const val GS_FILE_001 = "GS-FILE-001" // Cannot open input stream for incoming URI/content
    const val GS_FILE_002 = "GS-FILE-002" // Scoped storage permission denied (EACCES)
    const val GS_FILE_003 = "GS-FILE-003" // SHA-256 verification failed
    const val GS_FILE_004 = "GS-FILE-004" // Expected byte count mismatch
    const val GS_FILE_005 = "GS-FILE-005" // App storage / disk insufficient space
    const val GS_FILE_006 = "GS-FILE-006" // MediaStore Downloads export failed
    const val GS_FILE_007 = "GS-FILE-007" // Content import / SAF failure
    const val GS_FILE_008 = "GS-FILE-008" // External viewer launch failure

    // App / Persistence
    const val GS_APP_001 = "GS-APP-001" // Database read/write failure
    const val GS_APP_002 = "GS-APP-002" // Unhandled exception caught safely
}

class TransferVerificationException(
    val code: String,
    val category: DiagnosticCategory,
    override val message: String,
    val suggestedAction: String,
    cause: Throwable? = null
) : IOException(message, cause)

object DiagnosticClassifier {

    fun buildDeviceInfoJson(): String {
        val manufacturer = (Build.MANUFACTURER ?: "Android").replace("\"", "")
        val model = (Build.MODEL ?: "Device").replace("\"", "")
        val release = (Build.VERSION.RELEASE ?: "Unknown").replace("\"", "")
        return """{"manufacturer":"$manufacturer","model":"$model","androidRelease":"$release","sdkInt":${Build.VERSION.SDK_INT},"appVersion":"${BuildConfig.VERSION_NAME}","versionCode":${BuildConfig.VERSION_CODE}}"""
    }

    /**
     * Scrubs potential 6-digit auth codes, bearer-like tokens, or raw note content previews.
     */
    fun redactSensitiveText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .replace(Regex("(?i)(auth(?:Digits|Code)?[=:\\s]+)\\d{4,8}"), "$1[REDACTED]")
            .replace(Regex("(?i)(textContent[=:\\s]+)[^,\\n\\r]+"), "$1[REDACTED]")
    }

    /**
     * Classifies real runtime exceptions into specific, honest diagnostic codes without
     * guessing fake causes for unrecognized throwables.
     */
    fun classifyThrowable(
        throwable: Throwable,
        sourceComponent: String,
        operation: String,
        defaultCode: String = DiagnosticCodes.GS_APP_002,
        defaultCategory: DiagnosticCategory = DiagnosticCategory.SYSTEM,
        stateSnapshotJson: String = "{}",
        relatedTransferId: String? = null,
        relatedEndpointId: String? = null
    ): DiagnosticErrorEvent {
        if (throwable is TransferVerificationException) {
            return DiagnosticErrorEvent(
                code = throwable.code,
                title = titleForCode(throwable.code),
                category = throwable.category,
                severity = ErrorSeverity.ERROR,
                summary = redactSensitiveText(throwable.message),
                technicalMessage = "${throwable.javaClass.name}: ${redactSensitiveText(throwable.message)}",
                stackTrace = redactSensitiveText(throwable.stackTraceToString()),
                sourceComponent = sourceComponent,
                operation = operation,
                deviceInfoJson = buildDeviceInfoJson(),
                stateSnapshotJson = stateSnapshotJson,
                relatedTransferId = relatedTransferId,
                relatedEndpointId = relatedEndpointId,
                suggestedAction = throwable.suggestedAction
            )
        }

        val rawMsg = throwable.message ?: ""
        val lowerMsg = rawMsg.lowercase(Locale.US)

        val (resolvedCode, resolvedCategory, resolvedSummary, suggestedFix) = when {
            lowerMsg.contains("eacces") || lowerMsg.contains("permission denied") || throwable is SecurityException -> {
                Quad(
                    DiagnosticCodes.GS_FILE_002,
                    DiagnosticCategory.FILE_IO,
                    "Scoped Storage Access Denied (EACCES): Unable to read direct file path; use ContentResolver URI stream.",
                    "Ensure file payload is read via payload.asFile().asUri() and exported via MediaStore. If importing via picker, re-select the item so temporary URI grant is renewed."
                )
            }
            lowerMsg.contains("enospc") || lowerMsg.contains("no space") -> {
                Quad(
                    DiagnosticCodes.GS_FILE_005,
                    DiagnosticCategory.STORAGE,
                    "Insufficient device storage while copying or staging transfer payload.",
                    "Free up internal storage space and retry the transfer."
                )
            }
            throwable is FileNotFoundException -> {
                Quad(
                    DiagnosticCodes.GS_FILE_001,
                    DiagnosticCategory.FILE_IO,
                    "File or content URI not found during $operation: ${redactSensitiveText(rawMsg)}",
                    "Verify the source item still exists and retry the operation."
                )
            }
            else -> {
                Quad(
                    defaultCode,
                    defaultCategory,
                    "${throwable.javaClass.simpleName}: ${redactSensitiveText(rawMsg.ifBlank { "No message provided" })}",
                    suggestedActionForCode(defaultCode)
                )
            }
        }

        return DiagnosticErrorEvent(
            code = resolvedCode,
            title = titleForCode(resolvedCode),
            category = resolvedCategory,
            severity = severityForCode(resolvedCode),
            summary = resolvedSummary,
            technicalMessage = "${throwable.javaClass.name}: ${redactSensitiveText(rawMsg)}",
            stackTrace = redactSensitiveText(throwable.stackTraceToString()),
            sourceComponent = sourceComponent,
            operation = operation,
            deviceInfoJson = buildDeviceInfoJson(),
            stateSnapshotJson = stateSnapshotJson,
            relatedTransferId = relatedTransferId,
            relatedEndpointId = relatedEndpointId,
            suggestedAction = suggestedFix
        )
    }

    fun titleForCode(code: String): String = when (code) {
        DiagnosticCodes.GS_GESTURE_001 -> "Missing MediaPipe / Asset File"
        DiagnosticCodes.GS_GESTURE_002 -> "ONNX Classifier Initialization / Inference Error"
        DiagnosticCodes.GS_GESTURE_003 -> "Camera / Hand Tracker Runtime Failure"
        DiagnosticCodes.GS_NET_001 -> "Nearby Permissions or Adapter Unavailable"
        DiagnosticCodes.GS_NET_002 -> "Nearby Discovery / Connection Failure"
        DiagnosticCodes.GS_NET_003 -> "Peer Disconnected Unexpectedly"
        DiagnosticCodes.GS_NET_004 -> "Transfer Declined or Handshake Rejected"
        DiagnosticCodes.GS_FILE_001 -> "Input Stream Open Failure"
        DiagnosticCodes.GS_FILE_002 -> "Scoped Storage Access Denied (EACCES)"
        DiagnosticCodes.GS_FILE_003 -> "SHA-256 Integrity Verification Failed"
        DiagnosticCodes.GS_FILE_004 -> "Payload Size Mismatch"
        DiagnosticCodes.GS_FILE_005 -> "Insufficient Device Storage"
        DiagnosticCodes.GS_FILE_006 -> "MediaStore Downloads Export Failed"
        DiagnosticCodes.GS_FILE_007 -> "Content Import / SAF Failure"
        DiagnosticCodes.GS_FILE_008 -> "File Viewer Launch Error"
        DiagnosticCodes.GS_APP_001 -> "Local Database Persistence Error"
        else -> "Unexpected Application Exception"
    }

    fun severityForCode(code: String): ErrorSeverity = when (code) {
        DiagnosticCodes.GS_FILE_006,
        DiagnosticCodes.GS_NET_004 -> ErrorSeverity.WARNING
        DiagnosticCodes.GS_GESTURE_001,
        DiagnosticCodes.GS_GESTURE_002,
        DiagnosticCodes.GS_FILE_003 -> ErrorSeverity.CRITICAL
        else -> ErrorSeverity.ERROR
    }

    fun suggestedActionForCode(code: String): String = when (code) {
        DiagnosticCodes.GS_GESTURE_001 -> "Verify airshare_gesture.onnx, labels.json, preprocessing.json, and hand_landmarker.task are present in APK assets."
        DiagnosticCodes.GS_GESTURE_002 -> "Check ONNX runtime tensor metadata ([1, 63] FLOAT) and restart Gesture Radar."
        DiagnosticCodes.GS_GESTURE_003 -> "Grant Camera permission in Settings and ensure no other camera app holds the sensor."
        DiagnosticCodes.GS_NET_001 -> "Enable Bluetooth and Wi-Fi, then grant Nearby Wi-Fi / Bluetooth runtime permissions in Settings."
        DiagnosticCodes.GS_NET_002 -> "Move both phones closer together and restart Scan Nearby / Receive Mode."
        DiagnosticCodes.GS_NET_003 -> "Re-establish Nearby connection with the peer device and retry transfer."
        DiagnosticCodes.GS_NET_004 -> "Ask receiver to accept both the 6-digit Nearby security code and the transfer offer prompt."
        DiagnosticCodes.GS_FILE_001 -> "Retry reading payload via ContentResolver URI stream; source file may have moved."
        DiagnosticCodes.GS_FILE_002 -> "Avoid raw /Download/.nearby POSIX paths; rely on URI-based ContentResolver & MediaStore."
        DiagnosticCodes.GS_FILE_003 -> "Corrupted or mismatched payload rejected safely before saving. Retry transfer from sender."
        DiagnosticCodes.GS_FILE_004 -> "Bytes written did not match sender manifest size. Keep phones close and retry."
        DiagnosticCodes.GS_FILE_005 -> "Free internal storage space on the device before sending or receiving large files."
        DiagnosticCodes.GS_FILE_006 -> "Verified file remains safe in GestoShare private storage; open it via Transfer History or Active Transfer."
        DiagnosticCodes.GS_FILE_007 -> "Re-pick the file using SAF Document Picker or Android Photo Picker."
        DiagnosticCodes.GS_FILE_008 -> "Install an app capable of viewing this MIME type or access the file under Downloads/GestoShare."
        DiagnosticCodes.GS_APP_001 -> "Check internal storage space and restart GestoShare."
        else -> "Review the full technical stack trace below and copy the report for debugging."
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
