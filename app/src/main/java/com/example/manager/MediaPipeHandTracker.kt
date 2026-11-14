package com.example.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

enum class HandTrackerStatus(val label: String) {
    INITIALIZING("Initializing MediaPipe..."),
    ACTIVE("MediaPipe Active"),
    MODEL_MISSING("hand_landmarker.task Missing"),
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
    val handedness: String = "None",
    val fps: Int = 0,
    val inferenceTimeMs: Long = 0L,
    val statusMessage: String = "Initializing MediaPipe HandLandmarker..."
)

/**
 * Phase 6 MediaPipe Hand Landmark detector driven by CameraX ImageAnalysis.
 * Runs off the main UI thread on a single-thread executor with an AtomicBoolean
 * drop guard so frames are never queued faster than inference can process them.
 */
class MediaPipeHandTracker(
    private val context: Context
) {

    companion object {
        private const val TAG = "MediaPipeHandTracker"
        const val HAND_LANDMARKER_ASSET = "hand_landmarker.task"
        const val FEATURE_VECTOR_SIZE = 63

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
         * Converts 21 (x, y, z) MediaPipe landmarks into a wrist-centered,
         * scale-normalized 63-float vector ([1, 63]) matching Colab HaGRIDv2 training preprocessing:
         * 1. Translate all landmarks so wrist (index 0) is at (0, 0, 0).
         * 2. Divide all coordinates by the Euclidean 3D distance from wrist (0) to middle MCP (9).
         */
        fun normalizeLandmarksToFeatureVector(landmarks: List<NormalizedLandmarkPoint>): FloatArray {
            val out = FloatArray(FEATURE_VECTOR_SIZE)
            if (landmarks.size < 21) return out

            val wrist = landmarks[0]
            val middleMcp = landmarks[9]
            val dxScale = middleMcp.x - wrist.x
            val dyScale = middleMcp.y - wrist.y
            val dzScale = middleMcp.z - wrist.z
            var scale = sqrt(dxScale * dxScale + dyScale * dyScale + dzScale * dzScale)

            if (scale < 1e-5f) {
                var maxNorm = 0f
                for (pt in landmarks) {
                    val dx = pt.x - wrist.x
                    val dy = pt.y - wrist.y
                    val dz = pt.z - wrist.z
                    val n = sqrt(dx * dx + dy * dy + dz * dz)
                    if (n > maxNorm) maxNorm = n
                }
                scale = if (maxNorm >= 1e-5f) maxNorm else 1f
            }

            for (i in 0 until 21) {
                val pt = landmarks[i]
                val base = i * 3
                out[base] = (pt.x - wrist.x) / scale
                out[base + 1] = (pt.y - wrist.y) / scale
                out[base + 2] = (pt.z - wrist.z) / scale
            }
            return out
        }
    }

    val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _trackingState = MutableStateFlow(HandTrackingState())
    val trackingState: StateFlow<HandTrackingState> = _trackingState.asStateFlow()

    private val isProcessing = AtomicBoolean(false)
    private var handLandmarker: HandLandmarker? = null

    @Volatile
    private var isClosed = false

    private var frameCounter = 0
    private var lastFpsWindowMs = SystemClock.uptimeMillis()
    private var currentFps = 0

    init {
        initializeLandmarker()
    }

    private fun initializeLandmarker() {
        try {
            // Verify asset presence explicitly so missing file produces an honest diagnostic state
            context.assets.open(HAND_LANDMARKER_ASSET).close()

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(HAND_LANDMARKER_ASSET)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context.applicationContext, options)
            _trackingState.value = _trackingState.value.copy(
                status = HandTrackerStatus.ACTIVE,
                statusMessage = "MediaPipe HandLandmarker active (21 keypoints -> [1, 63])"
            )
            Log.d(TAG, "MediaPipe HandLandmarker initialized cleanly.")
        } catch (_: FileNotFoundException) {
            handLandmarker = null
            _trackingState.value = _trackingState.value.copy(
                status = HandTrackerStatus.MODEL_MISSING,
                statusMessage = "Missing assets/$HAND_LANDMARKER_ASSET. Camera preview runs; place model asset to enable 21-landmark detection."
            )
            Log.w(TAG, "Asset $HAND_LANDMARKER_ASSET not found in app/src/main/assets/")
        } catch (e: Exception) {
            handLandmarker = null
            _trackingState.value = _trackingState.value.copy(
                status = HandTrackerStatus.CAMERA_ERROR,
                statusMessage = "MediaPipe init error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
            Log.e(TAG, "Error initializing MediaPipe HandLandmarker", e)
        }
    }

    fun reportCameraError(message: String) {
        _trackingState.value = _trackingState.value.copy(
            status = HandTrackerStatus.CAMERA_ERROR,
            isHandDetected = false,
            landmarks = emptyList(),
            statusMessage = message
        )
    }

    /**
     * Called from CameraX ImageAnalysis analyzer on [analyzerExecutor].
     * Always closes [imageProxy] in a finally block.
     */
    fun analyzeImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (isClosed) {
            imageProxy.close()
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val startMs = SystemClock.uptimeMillis()
        var uprightBitmap: Bitmap? = null

        try {
            updateFpsCounter()
            val landmarker = handLandmarker
            if (landmarker == null) {
                // Still report live camera analyzer FPS honestly even when hand_landmarker.task is missing
                val prev = _trackingState.value
                if (prev.fps != currentFps) {
                    _trackingState.value = prev.copy(fps = currentFps)
                }
                return
            }

            val rawBitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            val matrix = Matrix().apply {
                if (rotation != 0) {
                    postRotate(rotation.toFloat())
                }
                if (isFrontCamera) {
                    // Mirror horizontally to match front-facing PreviewView coordinate space
                    postScale(-1f, 1f, rawBitmap.width / 2f, rawBitmap.height / 2f)
                }
            }

            uprightBitmap = Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )
            if (uprightBitmap !== rawBitmap) {
                rawBitmap.recycle()
            }

            val mpImage = BitmapImageBuilder(uprightBitmap).build()
            val result = landmarker.detect(mpImage)
            val latencyMs = SystemClock.uptimeMillis() - startMs

            publishResult(result, latencyMs)
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis error", e)
        } finally {
            try {
                uprightBitmap?.recycle()
            } catch (_: Exception) {
            }
            imageProxy.close()
            isProcessing.set(false)
        }
    }

    private fun publishResult(result: HandLandmarkerResult, latencyMs: Long) {
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            _trackingState.value = _trackingState.value.copy(
                status = HandTrackerStatus.ACTIVE,
                landmarks = emptyList(),
                normalizedFeatureVector = null,
                isHandDetected = false,
                handedness = "None",
                fps = currentFps,
                inferenceTimeMs = latencyMs,
                statusMessage = "Searching for hand in camera view..."
            )
            return
        }

        val firstHand = hands[0]
        val points = firstHand.map { lm ->
            NormalizedLandmarkPoint(x = lm.x(), y = lm.y(), z = lm.z())
        }
        val featureVec = normalizeLandmarksToFeatureVector(points)
        val handLabel = result.handednesses().firstOrNull()?.firstOrNull()?.categoryName() ?: "Hand"

        _trackingState.value = HandTrackingState(
            status = HandTrackerStatus.ACTIVE,
            landmarks = points,
            normalizedFeatureVector = featureVec,
            isHandDetected = true,
            handedness = handLabel,
            fps = currentFps,
            inferenceTimeMs = latencyMs,
            statusMessage = "$handLabel hand tracked · [1, 63] vector ready (${latencyMs}ms)"
        )
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
        try {
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing HandLandmarker", e)
        }
        handLandmarker = null
        try {
            analyzerExecutor.shutdownNow()
        } catch (_: Exception) {
        }
    }
}
