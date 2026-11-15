package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticErrorEvent
import com.example.model.DiagnosticLogEntry
import com.example.model.ErrorSeverity
import com.example.model.RecentDeviceRecord

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey val id: String,
    val code: String,
    val title: String,
    val category: String,
    val severity: String,
    val summary: String,
    val technicalMessage: String,
    val stackTrace: String,
    val sourceComponent: String,
    val operation: String,
    val deviceInfoJson: String,
    val stateSnapshotJson: String,
    val relatedTransferId: String?,
    val relatedEndpointId: String?,
    val suggestedAction: String,
    val timestamp: Long,
    val appVersion: String
) {
    fun toDomainModel(): DiagnosticErrorEvent {
        return DiagnosticErrorEvent(
            id = id,
            code = code,
            title = title,
            category = runCatching { DiagnosticCategory.valueOf(category) }.getOrDefault(DiagnosticCategory.SYSTEM),
            severity = runCatching { ErrorSeverity.valueOf(severity) }.getOrDefault(ErrorSeverity.ERROR),
            summary = summary,
            technicalMessage = technicalMessage,
            stackTrace = stackTrace,
            sourceComponent = sourceComponent,
            operation = operation,
            deviceInfoJson = deviceInfoJson,
            stateSnapshotJson = stateSnapshotJson,
            relatedTransferId = relatedTransferId,
            relatedEndpointId = relatedEndpointId,
            suggestedAction = suggestedAction,
            timestamp = timestamp,
            appVersion = appVersion
        )
    }

    companion object {
        fun fromDomainModel(e: DiagnosticErrorEvent): DiagnosticEventEntity = DiagnosticEventEntity(
            id = e.id,
            code = e.code,
            title = e.title,
            category = e.category.name,
            severity = e.severity.name,
            summary = e.summary,
            technicalMessage = e.technicalMessage,
            stackTrace = e.stackTrace,
            sourceComponent = e.sourceComponent,
            operation = e.operation,
            deviceInfoJson = e.deviceInfoJson,
            stateSnapshotJson = e.stateSnapshotJson,
            relatedTransferId = e.relatedTransferId,
            relatedEndpointId = e.relatedEndpointId,
            suggestedAction = e.suggestedAction,
            timestamp = e.timestamp,
            appVersion = e.appVersion
        )
    }
}

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val level: String,
    val category: String,
    val tag: String,
    val message: String,
    val metadata: String?
) {
    fun toDomainModel(): DiagnosticLogEntry = DiagnosticLogEntry(
        id = id,
        timestamp = timestamp,
        level = level,
        category = runCatching { DiagnosticCategory.valueOf(category) }.getOrDefault(DiagnosticCategory.SYSTEM),
        tag = tag,
        message = message,
        metadata = metadata
    )

    companion object {
        fun fromDomainModel(entry: DiagnosticLogEntry): DiagnosticLogEntity = DiagnosticLogEntity(
            id = entry.id,
            timestamp = entry.timestamp,
            level = entry.level,
            category = entry.category.name,
            tag = entry.tag,
            message = entry.message,
            metadata = entry.metadata
        )
    }
}

@Entity(tableName = "recent_devices")
data class RecentDeviceEntity(
    @PrimaryKey val deviceKey: String,
    val displayName: String,
    val lastEndpointId: String,
    val lastConnectedAt: Long,
    val connectionCount: Int
) {
    fun toDomainModel(): RecentDeviceRecord = RecentDeviceRecord(
        deviceKey = deviceKey,
        displayName = displayName,
        lastEndpointId = lastEndpointId,
        lastConnectedAt = lastConnectedAt,
        connectionCount = connectionCount
    )
}
