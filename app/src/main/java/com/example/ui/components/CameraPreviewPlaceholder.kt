package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.manager.HandTrackerStatus
import com.example.manager.HandTrackingState
import com.example.manager.MediaPipeHandTracker
import com.example.model.GestureResult
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface

@Composable
fun CameraPreviewPlaceholder(
    currentGesture: GestureResult,
    handTrackingState: HandTrackingState,
    handTracker: MediaPipeHandTracker,
    isTrackingHandFallback: Boolean,
    useFrontCamera: Boolean = true,
    onToggleCamera: (() -> Unit)? = null,
    topOverlayContent: (@Composable BoxScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner, useFrontCamera) {
        var boundProvider: ProcessCameraProvider? = null
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    boundProvider = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val actualIsFront = when {
                        useFrontCamera && hasFront -> true
                        !useFrontCamera && hasBack -> false
                        hasFront -> true
                        else -> false
                    }
                    val cameraSelector = if (actualIsFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(handTracker.analyzerExecutor) { imageProxy ->
                                handTracker.analyzeImageProxy(imageProxy, isFrontCamera = actualIsFront)
                            }
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    handTracker.reportCameraError(
                        "Camera failed to bind: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    )
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            try {
                boundProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val isHandDetected = handTrackingState.isHandDetected || isTrackingHandFallback

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .glassSurface(cornerRadius = 24.dp, borderAccent = palette.accentPrimary.copy(alpha = 0.45f))
            .testTag("camera_preview_container")
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .glassSurface(cornerRadius = 32.dp, borderAccent = palette.accentPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera Permission",
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "GestoShare uses CameraX + MediaPipe to track 21 3D hand landmarks offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentPrimary,
                        contentColor = palette.onAccentPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("enable_camera_button")
                ) {
                    Text("Enable Camera Feed", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // HUD Corner Brackets + Live 21-Landmark Hand Skeleton Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = palette.accentPrimary.copy(alpha = pulseAlpha)
            val cornerLength = 30.dp.toPx()
            val strokeWidth = 2.5.dp.toPx()
            val inset = 14.dp.toPx()
            val w = size.width
            val h = size.height

            drawLine(strokeColor, Offset(inset, inset), Offset(inset + cornerLength, inset), strokeWidth)
            drawLine(strokeColor, Offset(inset, inset), Offset(inset, inset + cornerLength), strokeWidth)
            drawLine(strokeColor, Offset(w - inset, inset), Offset(w - inset - cornerLength, inset), strokeWidth)
            drawLine(strokeColor, Offset(w - inset, inset), Offset(w - inset, inset + cornerLength), strokeWidth)
            drawLine(strokeColor, Offset(inset, h - inset), Offset(inset + cornerLength, h - inset), strokeWidth)
            drawLine(strokeColor, Offset(inset, h - inset), Offset(inset, h - inset - cornerLength), strokeWidth)
            drawLine(strokeColor, Offset(w - inset, h - inset), Offset(w - inset - cornerLength, h - inset), strokeWidth)
            drawLine(strokeColor, Offset(w - inset, h - inset), Offset(w - inset, h - inset - cornerLength), strokeWidth)

            val points = handTrackingState.landmarks
            val mirrorOverlayX = handTrackingState.isFrontCamera
            if (points.size >= 21) {
                for ((startIdx, endIdx) in MediaPipeHandTracker.HAND_CONNECTIONS) {
                    val a = points[startIdx]
                    val b = points[endIdx]
                    val ax = if (mirrorOverlayX) 1f - a.x else a.x
                    val bx = if (mirrorOverlayX) 1f - b.x else b.x
                    drawLine(
                        color = palette.accentSecondary.copy(alpha = 0.9f),
                        start = Offset(ax * w, a.y * h),
                        end = Offset(bx * w, b.y * h),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                points.forEachIndexed { idx, lm ->
                    val drawX = if (mirrorOverlayX) 1f - lm.x else lm.x
                    val center = Offset(drawX * w, lm.y * h)
                    val isTip = idx == 4 || idx == 8 || idx == 12 || idx == 16 || idx == 20
                    val isWrist = idx == 0
                    drawCircle(
                        color = when {
                            isWrist -> Color.White
                            isTip -> palette.accentPrimary
                            else -> palette.accentSecondary
                        },
                        radius = if (isTip || isWrist) 5.5.dp.toPx() else 3.8.dp.toPx(),
                        center = center
                    )
                }
            }
        }

        // Top Docked Floating Content (Recipient Overlay + Status Strip) keeping center zone clear
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (topOverlayContent != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    topOverlayContent()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .glassSurface(
                            cornerRadius = 18.dp,
                            fillColor = if (palette.isDark) Color(0xCC090E1B) else Color(0xDDF4F8FF),
                            borderAccent = when {
                                handTrackingState.status == HandTrackerStatus.CAMERA_ERROR -> palette.error
                                handTrackingState.status == HandTrackerStatus.MODEL_MISSING -> palette.warning
                                handTrackingState.isHandDetected -> palette.success
                                else -> palette.accentPrimary.copy(alpha = 0.4f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                when {
                                    handTrackingState.status == HandTrackerStatus.CAMERA_ERROR -> palette.error
                                    handTrackingState.status == HandTrackerStatus.MODEL_MISSING -> palette.warning
                                    isHandDetected -> palette.success
                                    else -> palette.accentPrimary
                                },
                                CircleShape
                            )
                    )
                    val statusText = when {
                        !hasCameraPermission -> "CAMERA PERMISSION NEEDED"
                        handTrackingState.status == HandTrackerStatus.MODEL_MISSING ->
                            "ASSET MISSING (${handTrackingState.fps} FPS)"
                        handTrackingState.status == HandTrackerStatus.CAMERA_ERROR -> "CAMERA ERROR"
                        handTrackingState.isHandDetected ->
                            "${handTrackingState.handedness.uppercase()} · ${handTrackingState.fps} FPS · MP ${handTrackingState.inferenceTimeMs}ms"
                        else -> "SCANNING HAND · ${handTrackingState.fps} FPS"
                    }
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            handTrackingState.status == HandTrackerStatus.MODEL_MISSING -> palette.warning
                            isHandDetected -> palette.success
                            else -> palette.textPrimary
                        }
                    )
                }

                if (onToggleCamera != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .glassSurface(
                                cornerRadius = 16.dp,
                                fillColor = if (palette.isDark) Color(0xCC090E1B) else Color(0xDDF4F8FF),
                                borderAccent = palette.accentPrimary.copy(alpha = 0.45f)
                            )
                            .clickable { onToggleCamera() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("camera_facing_toggle_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Front/Back Camera",
                            tint = palette.accentPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (useFrontCamera) "FRONT" else "REAR",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary
                        )
                    }
                }
            }
        }

        // Bottom Frosted Gesture HUD (ONNX Prediction + Confidence + Latency)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .glassSurface(
                    cornerRadius = 16.dp,
                    fillColor = palette.glassFillElevated.copy(alpha = 0.90f),
                    borderAccent = when {
                        currentGesture.isAccepted -> palette.success.copy(alpha = 0.65f)
                        currentGesture.isHandDetected -> palette.warning.copy(alpha = 0.5f)
                        else -> palette.accentPrimary.copy(alpha = 0.35f)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentGesture.gesture.symbol,
                    fontSize = 24.sp
                )
                Column {
                    Text(
                        text = if (currentGesture.isHandDetected) {
                            "${currentGesture.predictedClass.uppercase()} · ${currentGesture.gesture.actionName}"
                        } else {
                            currentGesture.gesture.actionName
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (currentGesture.isAccepted) palette.success else palette.accentPrimary
                    )
                    Text(
                        text = when {
                            currentGesture.errorMessage != null -> currentGesture.errorMessage
                            currentGesture.isAccepted -> "Accepted (idx=${currentGesture.predictedClassIndex}) · ONNX ${currentGesture.inferenceLatencyUs}µs"
                            currentGesture.isHandDetected -> "Filtered -> OTHER / uncertain (${currentGesture.inferenceLatencyUs}µs)"
                            else -> currentGesture.gesture.description
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        currentGesture.isAccepted -> "ACCEPTED"
                        currentGesture.isHandDetected -> "UNCERTAIN"
                        else -> "NO HAND"
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        currentGesture.isAccepted -> palette.success
                        currentGesture.isHandDetected -> palette.warning
                        else -> palette.textSecondary
                    }
                )
                Text(
                    text = "${(currentGesture.confidence * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (currentGesture.isAccepted) palette.success else palette.textSecondary
                )
            }
        }
    }
}
