package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticErrorEvent
import com.example.model.DiagnosticLogEntry
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassErrorCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    errorEvents: List<DiagnosticErrorEvent>,
    operationalLogs: List<DiagnosticLogEntry>,
    modelSummary: String,
    nearbySummary: String,
    onCopyErrorReport: (DiagnosticErrorEvent) -> Unit,
    onExportDiagnosticsTxt: () -> Unit,
    onExportDiagnosticsJson: () -> Unit,
    onDeleteErrorEvent: (String) -> Unit,
    onClearAllDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    var activeTab by remember { mutableStateOf("ERRORS") }
    var selectedCategoryFilter by remember { mutableStateOf<DiagnosticCategory?>(null) }
    var inspectedEvent by remember { mutableStateOf<DiagnosticErrorEvent?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredErrors = remember(errorEvents, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) errorEvents
        else errorEvents.filter { it.category == selectedCategoryFilter }
    }

    val filteredLogs = remember(operationalLogs, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) operationalLogs
        else operationalLogs.filter { it.category == selectedCategoryFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("diagnostics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary + Export Controls Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_summary_card"),
                cornerRadius = 20.dp,
                fillColor = palette.glassFillElevated,
                borderAccent = if (errorEvents.isNotEmpty()) palette.error.copy(alpha = 0.55f) else palette.success.copy(alpha = 0.5f),
                borderWidth = 1.5.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (errorEvents.isEmpty()) Icons.Default.CheckCircle else Icons.Default.BugReport,
                                contentDescription = "Diagnostics Status",
                                tint = if (errorEvents.isEmpty()) palette.success else palette.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Runtime Telemetry & Error Center",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }

                        GlassStatusBadge(
                            label = "${errorEvents.size} ERR / ${operationalLogs.size} LOG",
                            color = if (errorEvents.isEmpty()) palette.success else palette.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Model: $modelSummary",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary
                    )
                    Text(
                        text = "Nearby: $nearbySummary",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassButton(
                            onClick = onExportDiagnosticsTxt,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("export_diagnostics_txt_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export TXT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        GlassOutlinedButton(
                            onClick = onExportDiagnosticsJson,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("export_diagnostics_json_button"),
                            accentColor = palette.accentSecondary,
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (errorEvents.isNotEmpty() || operationalLogs.isNotEmpty()) {
                            GlassOutlinedButton(
                                onClick = { showClearDialog = true },
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("clear_diagnostics_button"),
                                accentColor = palette.error,
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Diagnostics",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tabs (Error Reports vs Operational Logs) + Category Filter Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton(
                        onClick = { activeTab = "ERRORS" },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("diagnostics_tab_errors"),
                        containerColor = if (activeTab == "ERRORS") palette.accentPrimary else palette.glassFill,
                        contentColor = if (activeTab == "ERRORS") palette.onAccentPrimary else palette.textSecondary,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Error Reports (${errorEvents.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    GlassButton(
                        onClick = { activeTab = "LOGS" },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("diagnostics_tab_logs"),
                        containerColor = if (activeTab == "LOGS") palette.accentSecondary else palette.glassFill,
                        contentColor = if (activeTab == "LOGS") Color.White else palette.textSecondary,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Operational Logs (${operationalLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("All", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accentPrimary,
                            selectedLabelColor = palette.onAccentPrimary,
                            containerColor = palette.glassFill,
                            labelColor = palette.textSecondary
                        )
                    )
                    listOf(
                        DiagnosticCategory.FILE_IO,
                        DiagnosticCategory.VERIFICATION,
                        DiagnosticCategory.NEARBY,
                        DiagnosticCategory.GESTURE,
                        DiagnosticCategory.MODEL,
                        DiagnosticCategory.CAMERA
                    ).forEach { cat ->
                        val isSel = selectedCategoryFilter == cat
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCategoryFilter = if (isSel) null else cat },
                            label = { Text(cat.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentPrimary,
                                selectedLabelColor = palette.onAccentPrimary,
                                containerColor = palette.glassFill,
                                labelColor = palette.textSecondary
                            )
                        )
                    }
                }
            }
        }

        if (activeTab == "ERRORS") {
            if (filteredErrors.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(cornerRadius = 18.dp)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = palette.success,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Recorded Error Reports",
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All Nearby transfers, ContentResolver I/O, SHA-256 verification, and ONNX gesture predictions ran cleanly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredErrors, key = { it.id }) { event ->
                    GlassErrorCard(
                        event = event,
                        onClick = { inspectedEvent = event },
                        onCopyReport = { onCopyErrorReport(event) }
                    )
                }
            }
        } else {
            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(cornerRadius = 18.dp)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No operational logs recorded for this filter yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            } else {
                val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                items(filteredLogs, key = { it.id }) { entry ->
                    val levelColor = when (entry.level.uppercase(Locale.US)) {
                        "ERROR" -> palette.error
                        "WARN" -> palette.warning
                        else -> palette.accentPrimary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 12.dp,
                                fillColor = palette.glassFill,
                                borderAccent = levelColor.copy(alpha = 0.35f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassStatusBadge(label = entry.level, color = levelColor)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${fmt.format(Date(entry.timestamp))} · ${entry.tag}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textMuted
                            )
                            Text(
                                text = entry.message,
                                fontSize = 11.sp,
                                color = palette.textPrimary
                            )
                            if (!entry.metadata.isNullOrBlank()) {
                                Text(
                                    text = entry.metadata,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Error Detail Inspection Modal with One-Tap Copy Full Report
    inspectedEvent?.let { ev ->
        AlertDialog(
            onDismissRequest = { inspectedEvent = null },
            containerColor = palette.dialogSurface,
            title = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassStatusBadge(label = ev.code, color = palette.error)
                        GlassStatusBadge(label = ev.severity.label, color = palette.warning)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ev.title,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = ev.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Suggested Fix: ${ev.suggestedAction}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.success
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                cornerRadius = 10.dp,
                                fillColor = palette.glassFillInset,
                                borderAccent = palette.glassBorder
                            )
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Component: ${ev.sourceComponent}.${ev.operation}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.accentPrimary
                            )
                            Text(
                                text = "Technical: ${ev.technicalMessage}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textSecondary
                            )
                            Text(
                                text = "State: ${ev.stateSnapshotJson}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textMuted
                            )
                            Text(
                                text = "Stack Trace:\n${ev.stackTrace}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCopyErrorReport(ev)
                    },
                    modifier = Modifier.testTag("dialog_copy_error_report_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentPrimary,
                        contentColor = palette.onAccentPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY ERROR REPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDeleteErrorEvent(ev.id)
                            inspectedEvent = null
                        }
                    ) {
                        Text("Delete", color = palette.error)
                    }
                    TextButton(onClick = { inspectedEvent = null }) {
                        Text("Close", color = palette.textSecondary)
                    }
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = palette.dialogSurface,
            title = { Text("Clear Diagnostics & Logs?", color = palette.textPrimary) },
            text = {
                Text(
                    text = "This permanently removes all captured error reports and operational logs from local storage.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllDiagnostics()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.error, contentColor = Color.White)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}
