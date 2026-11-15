package com.example.data

import com.example.BuildConfig
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticClassifier
import com.example.model.DiagnosticErrorEvent
import com.example.model.DiagnosticLogEntry
import com.example.model.ErrorSeverity
import com.example.model.RecentDeviceRecord
import com.example.model.TransferRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsRepository(
    private val dao: DiagnosticDao,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        const val MAX_ERROR_EVENTS = 200
        const val MAX_OPERATIONAL_LOGS = 500
    }

    val errorEvents: Flow<List<DiagnosticErrorEvent>> = dao.getAllErrorEvents().map { list ->
        list.map { it.toDomainModel() }
    }

    val operationalLogs: Flow<List<DiagnosticLogEntry>> = dao.getAllLogs().map { list ->
        list.map { it.toDomainModel() }
    }

    val recentDevices: Flow<List<RecentDeviceRecord>> = dao.getRecentDevices().map { list ->
        list.map { it.toDomainModel() }
    }

    fun recordError(
        code: String,
        category: DiagnosticCategory,
        sourceComponent: String,
        operation: String,
        summary: String,
        technicalMessage: String = summary,
        stackTrace: String = "",
        severity: ErrorSeverity = DiagnosticClassifier.severityForCode(code),
        suggestedAction: String = DiagnosticClassifier.suggestedActionForCode(code),
        stateSnapshotJson: String = "{}",
        relatedTransferId: String? = null,
        relatedEndpointId: String? = null
    ) {
        val event = DiagnosticErrorEvent(
            code = code,
            title = DiagnosticClassifier.titleForCode(code),
            category = category,
            severity = severity,
            summary = DiagnosticClassifier.redactSensitiveText(summary),
            technicalMessage = DiagnosticClassifier.redactSensitiveText(technicalMessage),
            stackTrace = DiagnosticClassifier.redactSensitiveText(stackTrace.ifBlank { Thread.currentThread().stackTrace.take(10).joinToString("\n") }),
            sourceComponent = sourceComponent,
            operation = operation,
            deviceInfoJson = DiagnosticClassifier.buildDeviceInfoJson(),
            stateSnapshotJson = DiagnosticClassifier.redactSensitiveText(stateSnapshotJson),
            relatedTransferId = relatedTransferId,
            relatedEndpointId = relatedEndpointId,
            suggestedAction = suggestedAction
        )
        ioScope.launch {
            dao.insertErrorEvent(DiagnosticEventEntity.fromDomainModel(event))
            dao.trimErrorEvents(MAX_ERROR_EVENTS)
            dao.insertLog(
                DiagnosticLogEntity.fromDomainModel(
                    DiagnosticLogEntry(
                        level = if (severity == ErrorSeverity.WARNING) "WARN" else "ERROR",
                        category = category,
                        tag = "$sourceComponent[$code]",
                        message = event.summary,
                        metadata = "transferId=${relatedTransferId ?: "none"}, endpoint=${relatedEndpointId ?: "none"}"
                    )
                )
            )
            dao.trimLogs(MAX_OPERATIONAL_LOGS)
        }
    }

    fun recordException(
        throwable: Throwable,
        sourceComponent: String,
        operation: String,
        defaultCode: String,
        defaultCategory: DiagnosticCategory,
        stateSnapshotJson: String = "{}",
        relatedTransferId: String? = null,
        relatedEndpointId: String? = null
    ) {
        val event = DiagnosticClassifier.classifyThrowable(
            throwable = throwable,
            sourceComponent = sourceComponent,
            operation = operation,
            defaultCode = defaultCode,
            defaultCategory = defaultCategory,
            stateSnapshotJson = stateSnapshotJson,
            relatedTransferId = relatedTransferId,
            relatedEndpointId = relatedEndpointId
        )
        ioScope.launch {
            dao.insertErrorEvent(DiagnosticEventEntity.fromDomainModel(event))
            dao.trimErrorEvents(MAX_ERROR_EVENTS)
            dao.insertLog(
                DiagnosticLogEntity.fromDomainModel(
                    DiagnosticLogEntry(
                        level = "ERROR",
                        category = event.category,
                        tag = "$sourceComponent[${event.code}]",
                        message = event.summary,
                        metadata = event.technicalMessage
                    )
                )
            )
            dao.trimLogs(MAX_OPERATIONAL_LOGS)
        }
    }

    fun recordLog(
        category: DiagnosticCategory,
        tag: String,
        message: String,
        level: String = "INFO",
        metadata: String? = null
    ) {
        val sanitizedMsg = DiagnosticClassifier.redactSensitiveText(message)
        val sanitizedMeta = metadata?.let { DiagnosticClassifier.redactSensitiveText(it) }
        ioScope.launch {
            dao.insertLog(
                DiagnosticLogEntity.fromDomainModel(
                    DiagnosticLogEntry(
                        level = level,
                        category = category,
                        tag = tag,
                        message = sanitizedMsg,
                        metadata = sanitizedMeta
                    )
                )
            )
            dao.trimLogs(MAX_OPERATIONAL_LOGS)
        }
    }

    suspend fun recordErrorSync(event: DiagnosticErrorEvent) {
        dao.insertErrorEvent(DiagnosticEventEntity.fromDomainModel(event))
        dao.trimErrorEvents(MAX_ERROR_EVENTS)
    }

    suspend fun deleteErrorEvent(id: String) {
        dao.deleteErrorEventById(id)
    }

    suspend fun clearAllErrors() {
        dao.clearAllErrorEvents()
    }

    suspend fun clearAllLogs() {
        dao.clearAllLogs()
    }

    fun recordRecentDeviceConnection(endpointName: String, endpointId: String) {
        val key = endpointName.trim().ifBlank { endpointId }
        ioScope.launch {
            val existing = dao.getRecentDevice(key)
            val entity = RecentDeviceEntity(
                deviceKey = key,
                displayName = key,
                lastEndpointId = endpointId,
                lastConnectedAt = System.currentTimeMillis(),
                connectionCount = (existing?.connectionCount ?: 0) + 1
            )
            dao.upsertRecentDevice(entity)
        }
    }

    fun forgetRecentDevice(deviceKey: String) {
        ioScope.launch {
            dao.deleteRecentDevice(deviceKey)
        }
    }

    fun formatSingleErrorReport(event: DiagnosticErrorEvent): String {
        val isoTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(event.timestamp))
        return buildString {
            appendLine("=== GestoShare Error Report ===")
            appendLine("App: GestoShare v${event.appVersion}")
            appendLine("Time: $isoTime")
            appendLine("Code: ${event.code}")
            appendLine("Severity: ${event.severity.name}")
            appendLine("Category: ${event.category.name}")
            appendLine("Component: ${event.sourceComponent}.${event.operation}")
            appendLine("Device: ${event.deviceInfoJson}")
            if (!event.relatedTransferId.isNullOrBlank()) {
                appendLine("Transfer ID: ${event.relatedTransferId}")
            }
            if (!event.relatedEndpointId.isNullOrBlank()) {
                appendLine("Endpoint ID: ${event.relatedEndpointId}")
            }
            appendLine("Summary: ${event.summary}")
            appendLine("Technical Detail: ${event.technicalMessage}")
            appendLine("State Snapshot: ${event.stateSnapshotJson}")
            appendLine("Suggested Fix: ${event.suggestedAction}")
            appendLine("Stack Trace:")
            appendLine(event.stackTrace.ifBlank { "(No stack trace captured)" })
            append("===============================")
        }
    }

    suspend fun exportDiagnosticsText(
        recentTransfers: List<TransferRecord>,
        modelSummary: String,
        nearbySummary: String
    ): String {
        val errors = dao.getAllErrorEventsSnapshot().map { it.toDomainModel() }
        val logs = dao.getAllLogsSnapshot().map { it.toDomainModel() }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

        return buildString {
            appendLine("====================================================")
            appendLine("GestoShare Diagnostics Bundle (TXT)")
            appendLine("Generated: ${fmt.format(Date())}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Device Info: ${DiagnosticClassifier.buildDeviceInfoJson()}")
            appendLine("Model Status: $modelSummary")
            appendLine("Nearby Status: $nearbySummary")
            appendLine("Total Error Events: ${errors.size} | Total Logs: ${logs.size}")
            appendLine("====================================================")
            appendLine()
            appendLine("--- ERROR EVENTS (${errors.size}) ---")
            if (errors.isEmpty()) {
                appendLine("(None)")
            } else {
                errors.forEach { appendLine(formatSingleErrorReport(it)); appendLine() }
            }
            appendLine("--- RECENT TRANSFER SUMMARY (${recentTransfers.take(15).size}) ---")
            recentTransfers.take(15).forEach { tx ->
                appendLine("${fmt.format(Date(tx.timestamp))} | ${tx.direction.name} | ${tx.status.name} | ${tx.fileName} (${tx.fileSizeFormatted}) | ${tx.sha256Verification}")
            }
            appendLine()
            appendLine("--- OPERATIONAL LOGS (${logs.size}) ---")
            logs.forEach { log ->
                appendLine("${fmt.format(Date(log.timestamp))} [${log.level}/${log.category.name}] ${log.tag}: ${log.message}" +
                    if (!log.metadata.isNullOrBlank()) " (${log.metadata})" else "")
            }
        }
    }

    suspend fun exportDiagnosticsJson(
        recentTransfers: List<TransferRecord>,
        modelSummary: String,
        nearbySummary: String
    ): String {
        val errors = dao.getAllErrorEventsSnapshot().map { it.toDomainModel() }
        val logs = dao.getAllLogsSnapshot().map { it.toDomainModel() }

        val root = JSONObject().apply {
            put("app", "GestoShare")
            put("versionName", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("generatedAtEpochMs", System.currentTimeMillis())
            put("deviceInfo", JSONObject(DiagnosticClassifier.buildDeviceInfoJson()))
            put("modelStatus", modelSummary)
            put("nearbyStatus", nearbySummary)
            put("errors", JSONArray().apply {
                errors.forEach { ev ->
                    put(JSONObject().apply {
                        put("id", ev.id)
                        put("timestamp", ev.timestamp)
                        put("code", ev.code)
                        put("title", ev.title)
                        put("severity", ev.severity.name)
                        put("category", ev.category.name)
                        put("component", ev.sourceComponent)
                        put("operation", ev.operation)
                        put("summary", ev.summary)
                        put("technicalMessage", ev.technicalMessage)
                        put("suggestedAction", ev.suggestedAction)
                        put("relatedTransferId", ev.relatedTransferId ?: JSONObject.NULL)
                        put("relatedEndpointId", ev.relatedEndpointId ?: JSONObject.NULL)
                        put("stateSnapshotJson", ev.stateSnapshotJson)
                        put("stackTrace", ev.stackTrace)
                    })
                }
            })
            put("logs", JSONArray().apply {
                logs.forEach { log ->
                    put(JSONObject().apply {
                        put("timestamp", log.timestamp)
                        put("level", log.level)
                        put("category", log.category.name)
                        put("tag", log.tag)
                        put("message", log.message)
                        put("metadata", log.metadata ?: JSONObject.NULL)
                    })
                }
            })
            put("recentTransfers", JSONArray().apply {
                recentTransfers.take(15).forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("direction", tx.direction.name)
                        put("status", tx.status.name)
                        put("fileName", tx.fileName)
                        put("fileSize", tx.fileSizeFormatted)
                        put("verification", tx.sha256Verification)
                        put("timestamp", tx.timestamp)
                    })
                }
            })
        }
        return root.toString(2)
    }
}
