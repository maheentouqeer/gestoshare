package com.example.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.data.DiagnosticsRepository
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticCodes
import com.example.model.GestureResult
import com.example.model.GestureType
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

enum class HandTrackerStatus(val label: String) {
    INITIALIZING("Initializing MediaPipe..."),
    ACTIVE("MediaPipe LIVE_STREAM Active"),
    MODEL_MISSING("Required Asset Missing"),
    CAMERA_ERROR("Camera / Analyzer Error")
}

data class NormalizedLandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

data class HandTrackingState(
    val status: HandTrackerStatus = HandTrackerStatus.INITIALIZING,
    val landmarks: List<NormalizedLandmarkPoint> = emptyList(),
    val normalizedFeatureVector: FloatArray? = null,
    val isHandDetected: Boolean = false,
    val isFrontCamera: Boolean = false,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val handedness: String = "None",
    val fps: Int = 0,
    val inferenceTimeMs: Long = 0L,
    val statusMessage: String = "Initializing MediaPipe HandLandmarker (LIVE_STREAM)..."
)

data class LatencyStats(
    val modelLoadTimeMs: Long = 0L,
    val sampleCount: Int = 0,
    val lastMediaPipeMs: Long = 0L,
    val lastOnnxUs: Long = 0L,
    val avgOnnxMs: Float = 0f,
    val medianOnnxMs: Float = 0f,
    val p95OnnxMs: Float = 0f,
    val avgPipelineMs: Float = 0f
)

data class PredictionHistoryEntry(
    val timestampMs: Long,
    val predictedClass: String,
    val confidence: Float,
    val isAccepted: Boolean,
    val handedness: String,
    val onnxLatencyUs: Long,
    val pipelineLatencyMs: Long
)

/**
 * Phase 7 real-time gesture pipeline:
 * CameraX ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST) -> MediaPipe HandLandmarker (RunningMode.LIVE_STREAM)
 * -> exact Python preprocessing parity ([1, 63]) -> HaGridV2OnnxClassifier off the UI thread.
 */
class MediaPipeHandTracker(
    private val context: Context,
    val classifier: GestureClassifier = HaGridV2OnnxClassifier(context),
    val stabilizer: TemporalGestureStabilizer = TemporalGestureStabilizer(
        TemporalGestureConfig(confidenceThreshold = classifier.getConfidenceThreshold())
    ),
    private val diagnosticsRepository: DiagnosticsRepository? = null
) {

    companion object {
        private const val TAG = "MediaPipeHandTracker"
        const val HAND_LANDMARKER_ASSET = "hand_landmarker.task"
        const val FEATURE_VECTOR_SIZE = 63
        private const val MAX_LATENCY_WINDOW = 120
        private const val MAX_HISTORY_ENTRIES = 35
        private const val STALE_FRAME_TIMEOUT_MS = 2000L

        /**
         * 21 anatomical bone connections for rendering the MediaPipe hand skeleton.
         */
        val HAND_CONNECTIONS: List<Pair<Int, Int>> = listOf(
            // Thumb
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            // Index finger
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            // Middle finger
            0 to 9, 9 to 10, 10 to 11, 11 to 12,
            // Ring finger
            0 to 13, 13 to 14, 14 to 15, 15 to 16,
            // Pinky finger
            0 to 17, 17 to 18, 18 to 19, 19 to 20,
            // Palm knuckles bridge
            5 to 9, 9 to 13, 13 to 17
        )

        /**
         * Reproduces `live_test.py` and `preprocessing.json` identically:
         * ```python
         * pts = np.array([[p.x*W, p.y*H, p.z*W] for p in res.hand_landmarks[0]], dtype=np.float32)
         * pts -= pts[0]
         * pts /= (np.linalg.norm(pts[9]) + 1e-6)
         * ```
         * Flattened in order: x0,y0,z0, x1,y1,z1, ..., x20,y20,z20.
         */
        fun normalizeImageCoordinatesToFeatureVector(
            landmarks: List<NormalizedLandmarkPoint>,
            imageWidth: Int,
            imageHeight: Int
        ): FloatArray {
            val out = FloatArray(FEATURE_VECTOR_SIZE)
            if (landmarks.size < 21 || imageWidth <= 0 || imageHeight <= 0) return out

            val w = imageWidth.toFloat()
            val h = imageHeight.toFloat()

            val wristX = landmarks[0].x * w
            val wristY = landmarks[0].y * h
            val wristZ = landmarks[0].z * w

            val mcp9Dx = (landmarks[9].x * w) - wristX
            val mcp9Dy = (landmarks[9].y * h) - wristY
            val mcp9Dz = (landmarks[9].z * w) - wristZ
            val denom = sqrt(mcp9Dx * mcp9Dx + mcp9Dy * mcp9Dy + mcp9Dz * mcp9Dz) + 1e-6f

            for (i in 0 until 21) {
                val pt = landmarks[i]
                val base = i * 3
                out[base] = ((pt.x * w) - wristX) / denom
                out[base + 1] = ((pt.y * h) - wristY) / denom
                out[base + 2] = ((pt.z * w) - wristZ) / denom
            }
            return out
        }

        /**
         * Compatibility overload for square normalized units when image dimensions are unavailable.
         */
        fun normalizeLandmarksToFeatureVector(landmarks: List<NormalizedLandmarkPoint>): FloatArray {
            return normalizeImageCoordinatesToFeatureVector(landmarks, 1, 1)
        }
    }

    val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _trackingState = MutableStateFlow(HandTrackingState())
    val trackingState: StateFlow<HandTrackingState> = _trackingState.asStateFlow()

    private val _onnxPrediction = MutableStateFlow(
        GestureResult(
            gesture = GestureType.NONE,
            confidence = 0f,
            predictedClass = if (classifier.isModelLoaded()) "no hand" else "model_unavailable",
            predictedClassIndex = -1,
            isHandDetected = false,
            isAccepted = false,
            errorMessage = if (classifier.isModelLoaded()) null else classifier.getModelInfo()
        )
    )
    val onnxPrediction: StateFlow<GestureResult> = _onnxPrediction.asStateFlow()

    private val _confidenceThreshold = MutableStateFlow(classifier.getConfidenceThreshold())
    val confidenceThreshold: StateFlow<Float> = _confidenceThreshold.asStateFlow()

    private val _latencyStats = MutableStateFlow(
        LatencyStats(modelLoadTimeMs = classifier.getLoadTimeMs())
    )
    val latencyStats: StateFlow<LatencyStats> = _latencyStats.asStateFlow()

    private val _predictionHistory = MutableStateFlow<List<PredictionHistoryEntry>>(emptyList())
    val predictionHistory: StateFlow<List<PredictionHistoryEntry>> = _predictionHistory.asStateFlow()

    private val _stabilizerDecision = MutableStateFlow(StabilizedGestureDecision())
    val stabilizerDecision: StateFlow<StabilizedGestureDecision> = _stabilizerDecision.asStateFlow()

    private val _stabilizerConfig = MutableStateFlow(stabilizer.getConfig())
    val stabilizerConfig: StateFlow<TemporalGestureConfig> = _stabilizerConfig.asStateFlow()

    @Volatile
    private var onStabilizedCommandListener: ((GestureType, Float) -> Unit)? = null

    private val isProcessing = AtomicBoolean(false)
    @Volatile
    private var inFlightStartUptimeMs = 0L

    private var handLandmarker: HandLandmarker? = null

    @Volatile
    private var isClosed = false

    @Volatile
    private var lastIsFrontCamera = false

    private var lastFrameTimestampMs = 0L
    private val timestampLock = Any()
    private val pendingStartTimesNs = ConcurrentHashMap<Long, Long>()
    private val onnxWindowUs = ArrayList<Long>(MAX_LATENCY_WINDOW)
    private val pipelineWindowMs = ArrayList<Long>(MAX_LATENCY_WINDOW)
    private var lastHistoryLoggedClass: String? = null
    private var lastHistoryLoggedAtMs: Long = 0L

    private var frameCounter = 0
    private var lastFpsWindowMs = SystemClock.uptimeMillis()
    private var currentFps = 0

    init {
        initializeLandmarker()
    }

    private fun initializeLandmarker() {
        try {
            context.assets.open(HAND_LANDMARKER_ASSET).use { }

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(HAND_LANDMARKER_ASSET)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, inputImage ->
                    onLiveStreamResult(result, inputImage)
                }
                .setErrorListener { error ->
                    onLiveStreamError(error)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context.applicationContext, options)
            val modelOk = classifier.isModelLoaded()
            _trackingState.value = _trackingState.value.copy(
                status = if (modelOk) HandTrackerStatus.ACTIVE else HandTrackerStatus.MODEL_MISSING,
                statusMessage = if (modelOk) {
                    "MediaPipe LIVE_STREAM + ONNX active (load ${classifier.getLoadTimeMs()}ms)"
                } else {
                    "MediaPipe ready, ONNX missing/unavailable"
                }
            )
            Log.i(TAG, "MediaPipe LIVE_STREAM initialized. ONNX loaded=$modelOk")
        } catch (_: FileNotFoundException) {
            handLandmarker = null
            val msg = "Missing assets/$HAND_LANDMARKER_ASSET. Place official task file to enable 21-landmark detection."
            _trackingState.value = _trackingState.value.copy(
                status = HandTrackerStatus.MODEL_MISSING,
                statusMessage = msg
            )
            diagnosticsRepository?.recordError(
                code = DiagnosticCodes.GS_GESTURE_001,
                category = DiagnosticCategory.MODEL,
                sourceComponent = TAG,
                operation = "initializeLandmarker",
                summary = msg
            )
            Log.w(TAG, "Asset $HAND_LANDMARKER_ASSET not found in app/src/main/assets/")
        } catch (e: Exception) {
            handLandmarker = null
            _trackingState.value = _trackingState.value.copy(
                status = HandTrackerStatus.CAMERA_ERROR,
                statusMessage = "MediaPipe init error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
            diagnosticsRepository?.recordException(
                throwable = e,
                sourceComponent = TAG,
                operation = "initializeLandmarker",
                defaultCode = DiagnosticCodes.GS_GESTURE_003,
                defaultCategory = DiagnosticCategory.CAMERA
            )
            Log.e(TAG, "Error initializing MediaPipe HandLandmarker", e)
        }
    }

    fun setOnStabilizedCommandListener(listener: ((GestureType, Float) -> Unit)?) {
        onStabilizedCommandListener = listener
    }

    fun setConfidenceThreshold(threshold: Float) {
        classifier.setConfidenceThreshold(threshold)
        val clamped = classifier.getConfidenceThreshold()
        _confidenceThreshold.value = clamped
        stabilizer.setConfidenceThreshold(clamped)
        _stabilizerConfig.value = stabilizer.getConfig()
    }

    fun updateStabilizerConfig(transform: (TemporalGestureConfig) -> TemporalGestureConfig) {
        stabilizer.updateConfig(transform)
        _stabilizerConfig.value = stabilizer.getConfig()
    }

    fun resetStabilizer() {
        stabilizer.reset()
        _stabilizerDecision.value = StabilizedGestureDecision(
            requiredConsecutiveFrames = stabilizer.getConfig().requiredConsecutiveFrames
        )
    }

    fun clearPredictionHistory() {
        _predictionHistory.value = emptyList()
        lastHistoryLoggedClass = null
    }

    fun reportCameraError(message: String) {
        _trackingState.value = _trackingState.value.copy(
            status = HandTrackerStatus.CAMERA_ERROR,
            isHandDetected = false,
            landmarks = emptyList(),
            statusMessage = message
        )
        diagnosticsRepository?.recordError(
            code = DiagnosticCodes.GS_GESTURE_003,
            category = DiagnosticCategory.CAMERA,
            sourceComponent = TAG,
            operation = "reportCameraError",
            summary = message
        )
    }

    /**
     * Called from CameraX ImageAnalysis analyzer on [analyzerExecutor].
     * Rotates frame upright WITHOUT horizontal mirroring to match `live_test.py` and `preprocessing.json`,
     * submits asynchronously to MediaPipe LIVE_STREAM, and always closes [imageProxy] in `finally`.
     */
    fun analyzeImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (isClosed) {
            imageProxy.close()
            return
        }

        val nowUptime = SystemClock.uptimeMillis()
        if (isProcessing.get() && (nowUptime - inFlightStartUptimeMs) > STALE_FRAME_TIMEOUT_MS) {
            isProcessing.set(false)
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        inFlightStartUptimeMs = nowUptime
        lastIsFrontCamera = isFrontCamera
        var asyncDispatched = false

        try {
            updateFpsCounter()
            val landmarker = handLandmarker
            if (landmarker == null) {
                val prev = _trackingState.value
                if (prev.fps != currentFps) {
                    _trackingState.value = prev.copy(fps = currentFps)
                }
                return
            }

            val rawBitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            // Rotate upright ONLY (no horizontal flip) to preserve training orientation semantics
            val uprightBitmap: Bitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                ).also { created ->
                    if (created !== rawBitmap) {
                        rawBitmap.recycle()
                    }
                }
            } else {
                rawBitmap
            }

            val timestampMs = synchronized(timestampLock) {
                val next = maxOf(SystemClock.uptimeMillis(), lastFrameTimestampMs + 1L)
                lastFrameTimestampMs = next
                next
            }
            val nowNs = System.nanoTime()
            if (pendingStartTimesNs.size > 8) {
                val cutoffNs = nowNs - 5_000_000_000L
                val iterator = pendingStartTimesNs.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value < cutoffNs) {
                        iterator.remove()
                    }
                }
            }
            pendingStartTimesNs[timestampMs] = nowNs

            val mpImage = BitmapImageBuilder(uprightBitmap).build()
            landmarker.detectAsync(mpImage, timestampMs)
            asyncDispatched = true
        } catch (e: Exception) {
            Log.e(TAG, "Frame dispatch error", e)
        } finally {
            imageProxy.close()
            if (!asyncDispatched) {
                isProcessing.set(false)
            }
        }
    }

    private fun onLiveStreamResult(result: HandLandmarkerResult, inputImage: MPImage) {
        try {
            if (isClosed) return

            val tsMs = result.timestampMs()
            val startNs = pendingStartTimesNs.remove(tsMs)
            val mediaPipeMs = if (startNs != null) {
                maxOf(1L, (System.nanoTime() - startNs) / 1_000_000L)
            } else {
                0L
            }

            val frameWidth = inputImage.width
            val frameHeight = inputImage.height
            val hands = result.landmarks()

            if (hands.isEmpty()) {
                _trackingState.value = _trackingState.value.copy(
                    status = HandTrackerStatus.ACTIVE,
                    landmarks = emptyList(),
                    normalizedFeatureVector = null,
                    isHandDetected = false,
                    isFrontCamera = lastIsFrontCamera,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    handedness = "None",
                    fps = currentFps,
                    inferenceTimeMs = mediaPipeMs,
                    statusMessage = "Searching for hand (${frameWidth}x${frameHeight})..."
                )
                val noHandResult = classifier.classifyFeatures(null)
                _onnxPrediction.value = noHandResult
                _stabilizerDecision.value = stabilizer.process(noHandResult, SystemClock.uptimeMillis())
                return
            }

            val firstHand = hands[0]
            val points = firstHand.map { lm ->
                NormalizedLandmarkPoint(x = lm.x(), y = lm.y(), z = lm.z())
            }
            val featureVec = normalizeImageCoordinatesToFeatureVector(points, frameWidth, frameHeight)
            val handLabel = result.handednesses().firstOrNull()?.firstOrNull()?.categoryName() ?: "Hand"

            // Execute ONNX inference + temporal smoothing off the UI thread (MediaPipe worker thread)
            val prediction = classifier.classifyFeatures(featureVec)
            val totalPipelineMs = mediaPipeMs + prediction.inferenceLatencyMs
            val decision = stabilizer.process(prediction, SystemClock.uptimeMillis())

            _trackingState.value = HandTrackingState(
                status = HandTrackerStatus.ACTIVE,
                landmarks = points,
                normalizedFeatureVector = featureVec,
                isHandDetected = true,
                isFrontCamera = lastIsFrontCamera,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                handedness = handLabel,
                fps = currentFps,
                inferenceTimeMs = mediaPipeMs,
                statusMessage = "$handLabel hand · ${frameWidth}x${frameHeight} · MP ${mediaPipeMs}ms + ONNX ${prediction.inferenceLatencyUs}µs"
            )
            _onnxPrediction.value = prediction
            _stabilizerDecision.value = decision
            decision.triggeredGesture?.let { triggered ->
                onStabilizedCommandListener?.invoke(triggered, prediction.confidence)
            }
            recordLatencySample(mediaPipeMs, prediction.inferenceLatencyUs, totalPipelineMs)
            maybeAppendPredictionHistory(prediction, handLabel, totalPipelineMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling MediaPipe LIVE_STREAM result", e)
        } finally {
            isProcessing.set(false)
        }
    }

    private fun onLiveStreamError(error: RuntimeException) {
        Log.e(TAG, "MediaPipe LIVE_STREAM error", error)
        pendingStartTimesNs.clear()
        isProcessing.set(false)
        _trackingState.value = _trackingState.value.copy(
            status = HandTrackerStatus.CAMERA_ERROR,
            statusMessage = "MediaPipe LIVE_STREAM error: ${error.localizedMessage ?: error.javaClass.simpleName}"
        )
    }

    private fun recordLatencySample(mediaPipeMs: Long, onnxUs: Long, pipelineMs: Long) {
        if (onnxUs <= 0L) return
        synchronized(onnxWindowUs) {
            if (onnxWindowUs.size >= MAX_LATENCY_WINDOW) onnxWindowUs.removeAt(0)
            onnxWindowUs.add(onnxUs)

            if (pipelineWindowMs.size >= MAX_LATENCY_WINDOW) pipelineWindowMs.removeAt(0)
            pipelineWindowMs.add(pipelineMs)

            val sortedUs = onnxWindowUs.sorted()
            val count = sortedUs.size
            val avgOnnxMs = (sortedUs.sum().toDouble() / count / 1000.0).toFloat()
            val medianOnnxMs = (sortedUs[count / 2] / 1000.0).toFloat()
            val p95Index = ((count - 1) * 0.95).toInt().coerceIn(0, count - 1)
            val p95OnnxMs = (sortedUs[p95Index] / 1000.0).toFloat()
            val avgPipe = pipelineWindowMs.average().toFloat()

            _latencyStats.value = LatencyStats(
                modelLoadTimeMs = classifier.getLoadTimeMs(),
                sampleCount = _latencyStats.value.sampleCount + 1,
                lastMediaPipeMs = mediaPipeMs,
                lastOnnxUs = onnxUs,
                avgOnnxMs = avgOnnxMs,
                medianOnnxMs = medianOnnxMs,
                p95OnnxMs = p95OnnxMs,
                avgPipelineMs = avgPipe
            )
        }
    }

    private fun maybeAppendPredictionHistory(
        prediction: GestureResult,
        handLabel: String,
        pipelineLatencyMs: Long
    ) {
        val now = System.currentTimeMillis()
        val labelKey = "${prediction.predictedClass}:${prediction.isAccepted}"
        if (labelKey == lastHistoryLoggedClass && (now - lastHistoryLoggedAtMs) < 400L) {
            return
        }
        lastHistoryLoggedClass = labelKey
        lastHistoryLoggedAtMs = now

        val entry = PredictionHistoryEntry(
            timestampMs = now,
            predictedClass = prediction.predictedClass,
            confidence = prediction.confidence,
            isAccepted = prediction.isAccepted,
            handedness = handLabel,
            onnxLatencyUs = prediction.inferenceLatencyUs,
            pipelineLatencyMs = pipelineLatencyMs
        )
        val updated = (listOf(entry) + _predictionHistory.value).take(MAX_HISTORY_ENTRIES)
        _predictionHistory.value = updated
    }

    private fun updateFpsCounter() {
        frameCounter++
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastFpsWindowMs
        if (elapsed >= 1000L) {
            currentFps = ((frameCounter * 1000L) / elapsed).toInt()
            frameCounter = 0
            lastFpsWindowMs = now
        }
    }

    fun close() {
        isClosed = true
        pendingStartTimesNs.clear()
        try {
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing HandLandmarker", e)
        }
        handLandmarker = null
        try {
            classifier.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GestureClassifier", e)
        }
        try {
            analyzerExecutor.shutdownNow()
        } catch (_: Exception) {
        }
    }
}
