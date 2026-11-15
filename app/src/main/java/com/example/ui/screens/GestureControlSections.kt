package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.HaGridV2OnnxClassifier
import com.example.manager.HandTrackerStatus
import com.example.manager.HandTrackingState
import com.example.manager.LatencyStats
import com.example.manager.OnnxModelDiagnostics
import com.example.manager.PredictionHistoryEntry
import com.example.model.GestureResult
import com.example.model.GestureType
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OnnxProbabilityDashboardCard(
    liveOnnxPrediction: GestureResult,
    onnxDiagnostics: OnnxModelDiagnostics,
    confidenceThreshold: Float,
    onUpdateConfidenceThreshold: (Float) -> Unit
) {
    val palette = GestoTheme.colors
    val classes = onnxDiagnostics.classOrder.ifEmpty { HaGridV2OnnxClassifier.FALLBACK_CLASS_ORDER }
    val probs = liveOnnxPrediction.classProbabilities

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onnx_probabilities_card"),
        cornerRadius = 18.dp,
        fillColor = palette.glassFillElevated,
        borderAccent = if (liveOnnxPrediction.isAccepted) palette.success.copy(alpha = 0.65f) else palette.accentSecondary.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("6-Class ONNX Probability Output", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Text(
                        text = if (liveOnnxPrediction.isHandDetected) {
                            "Top: ${liveOnnxPrediction.predictedClass} (${(liveOnnxPrediction.confidence * 100).toInt()}%) · " +
                                if (liveOnnxPrediction.isAccepted) "ACCEPTED" else "FILTERED -> OTHER"
                        } else {
                            "Show a hand in front of the camera for live probabilities"
                        },
                        fontSize = 11.sp,
                        color = if (liveOnnxPrediction.isAccepted) palette.success else palette.textSecondary
                    )
                }
                Text(
                    text = "Thresh ${String.format(Locale.US, "%.2f", confidenceThreshold)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            classes.forEachIndexed { idx, className ->
                val prob = probs.getOrElse(idx) { 0f }.coerceIn(0f, 1f)
                val isTop = idx == liveOnnxPrediction.predictedClassIndex
                val barColor = when {
                    isTop && className != "OTHER" && prob >= confidenceThreshold -> palette.success
                    isTop && className != "OTHER" -> palette.warning
                    className == "OTHER" -> palette.textSecondary
                    else -> palette.accentPrimary.copy(alpha = 0.7f)
                }

                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val actionHint = onnxDiagnostics.actions[className]?.takeIf { it.isNotBlank() }
                        Text(
                            text = if (actionHint != null) "$idx · $className ($actionHint)" else "$idx · $className",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                            color = if (isTop) palette.textPrimary else palette.textSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%5.1f%%", prob * 100f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                            color = if (isTop) barColor else palette.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { prob },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = palette.glassFillInset
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confidence Threshold", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Text("${(confidenceThreshold * 100).toInt()}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.accentPrimary)
            }
            Slider(
                value = confidenceThreshold,
                onValueChange = onUpdateConfidenceThreshold,
                valueRange = 0.50f..0.95f,
                colors = SliderDefaults.colors(thumbColor = palette.accentPrimary, activeTrackColor = palette.accentPrimary),
                modifier = Modifier.testTag("confidence_threshold_slider")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnnxAssetDiagnosticsCard(
    onnxDiagnostics: OnnxModelDiagnostics,
    handTrackingState: HandTrackingState,
    latencyStats: LatencyStats,
    classifierInfo: String
) {
    val palette = GestoTheme.colors
    val missing = onnxDiagnostics.missingAssets
    val hasAssetError = missing.isNotEmpty() || !onnxDiagnostics.isLoaded || handTrackingState.status == HandTrackerStatus.MODEL_MISSING

    GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("onnx_diagnostics_card"),
        cornerRadius = 18.dp,
        fillColor = if (hasAssetError) palette.error.copy(alpha = 0.14f) else palette.glassFillElevated,
        borderAccent = if (hasAssetError) palette.error.copy(alpha = 0.7f) else palette.accentPrimary.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (hasAssetError) Icons.Default.Warning else Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (hasAssetError) palette.error else palette.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("ONNX Runtime & Asset Diagnostics", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                }
                Text(
                    text = "Load ${latencyStats.modelLoadTimeMs}ms",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (onnxDiagnostics.isLoaded) palette.success else palette.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HaGridV2OnnxClassifier.DEFAULT_REQUIRED_ASSETS.forEach { assetName ->
                    val isMissing = missing.contains(assetName) ||
                        (assetName == HaGridV2OnnxClassifier.HAND_LANDMARKER_ASSET && handTrackingState.status == HandTrackerStatus.MODEL_MISSING)
                    Row(
                        modifier = Modifier
                            .glassSurface(
                                cornerRadius = 8.dp,
                                fillColor = (if (isMissing) palette.error else palette.success).copy(alpha = 0.16f),
                                borderAccent = (if (isMissing) palette.error else palette.success).copy(alpha = 0.7f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isMissing) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isMissing) palette.error else palette.success,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(assetName, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = palette.textPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = classifierInfo,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (hasAssetError) palette.error else palette.textSecondary,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMiniBox("ONNX AVG", if (latencyStats.sampleCount > 0) String.format(Locale.US, "%.2fms", latencyStats.avgOnnxMs) else "--", Modifier.weight(1f))
                MetricMiniBox("MEDIAN", if (latencyStats.sampleCount > 0) String.format(Locale.US, "%.2fms", latencyStats.medianOnnxMs) else "--", Modifier.weight(1f))
                MetricMiniBox("P95", if (latencyStats.sampleCount > 0) String.format(Locale.US, "%.2fms", latencyStats.p95OnnxMs) else "--", Modifier.weight(1f))
                MetricMiniBox("HAND", handTrackingState.handedness, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun CommandPredictionLogsCard(
    commandLogs: List<String>,
    predictionHistory: List<PredictionHistoryEntry>,
    onClearPredictionHistory: () -> Unit
) {
    val palette = GestoTheme.colors
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("prediction_history_card"),
        cornerRadius = 18.dp,
        fillColor = palette.glassFillElevated,
        borderAccent = palette.accentSecondary.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Command & Prediction Logs", fontWeight = FontWeight.Bold, color = palette.textPrimary, fontSize = 13.sp)
                OutlinedButton(
                    onClick = onClearPredictionHistory,
                    modifier = Modifier.height(30.dp).testTag("clear_prediction_history_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 10.sp, color = palette.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            commandLogs.take(5).forEach { logLine ->
                Text(logLine, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = palette.accentPrimary, maxLines = 1)
            }
            if (commandLogs.isNotEmpty()) Spacer(modifier = Modifier.height(6.dp))

            if (predictionHistory.isEmpty()) {
                Text("No hand predictions recorded yet.", fontSize = 11.sp, color = palette.textSecondary)
            } else {
                predictionHistory.take(6).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .glassSurface(
                                cornerRadius = 8.dp,
                                fillColor = palette.glassFillInset,
                                borderAccent = if (entry.isAccepted) palette.success.copy(alpha = 0.35f) else palette.glassBorder
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatter.format(Date(entry.timestampMs))} · ${entry.predictedClass} (${entry.handedness})",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.isAccepted) palette.success else palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(entry.confidence * 100).toInt()}% · ${entry.onnxLatencyUs}µs",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.isAccepted) palette.accentPrimary else palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GestureCommandHookCard(
    command: GestureType,
    isActive: Boolean,
    onSimulateGesture: (GestureType) -> Unit
) {
    val palette = GestoTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("gesture_command_${command.name}"),
        cornerRadius = 16.dp,
        fillColor = if (isActive) palette.accentPrimary.copy(alpha = 0.14f) else palette.glassFillElevated,
        borderAccent = if (isActive) palette.accentPrimary else palette.glassBorder,
        borderWidth = if (isActive) 1.5.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(46.dp).glassSurface(cornerRadius = 12.dp, fillColor = palette.glassFillInset, borderAccent = palette.glassBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = command.symbol, fontSize = 24.sp)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(command.name, fontWeight = FontWeight.Bold, color = if (isActive) palette.accentPrimary else palette.textPrimary, fontSize = 14.sp)
                        Text("· ${command.actionName}", fontWeight = FontWeight.SemiBold, color = if (command == GestureType.STOP) palette.error else palette.accentPrimary, fontSize = 12.sp)
                    }
                    Text(command.description, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = palette.textSecondary)
                }
            }

            Button(
                onClick = { onSimulateGesture(command) },
                modifier = Modifier.height(36.dp).testTag("trigger_${command.name.lowercase()}"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) palette.accentPrimary else palette.glassFillInset,
                    contentColor = if (isActive) palette.onAccentPrimary else palette.accentPrimary
                ),
                border = BorderStroke(1.dp, if (isActive) palette.accentPrimary else palette.glassBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(text = if (isActive) "Active" else "Trigger", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricMiniBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    Column(
        modifier = modifier
            .glassSurface(cornerRadius = 10.dp, fillColor = palette.glassFillInset, borderAccent = palette.glassBorder)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = palette.textSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = palette.accentPrimary, maxLines = 1)
    }
}
