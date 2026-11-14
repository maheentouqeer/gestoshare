package com.example.manager

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxMap
import ai.onnxruntime.OnnxSequence
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.example.model.GestureResult
import com.example.model.GestureType
import org.json.JSONObject
import java.io.FileNotFoundException
import java.nio.FloatBuffer

data class OnnxModelDiagnostics(
    val isLoaded: Boolean = false,
    val missingAssets: List<String> = emptyList(),
    val loadTimeMs: Long = 0L,
    val inputMetadata: String = "Uninspected",
    val outputMetadata: String = "Uninspected",
    val classOrder: List<String> = emptyList(),
    val actions: Map<String, String> = emptyMap(),
    val preprocessingSummary: String = "",
    val statusMessage: String = "Initializing ONNX Runtime..."
)

/**
 * Phase 7 real HaGRIDv2 ONNX Runtime classifier.
 *
 * Loads `airshare_gesture.onnx`, `labels.json`, `preprocessing.json` (and verifies
 * `hand_landmarker.task`) from `app/src/main/assets/`. Inspects actual ONNX input/output
 * metadata at initialization and consumes a [1, 63] FLOAT32 feature vector without applying
 * any duplicate StandardScaler in Kotlin (the exported pipeline already contains Scaler + RBF SVC).
 */
class HaGridV2OnnxClassifier(
    private val context: Context,
    private val requiredAssetOverrides: List<String>? = null
) : GestureClassifier {

    companion object {
        private const val TAG = "HaGridV2OnnxClassifier"
        const val MODEL_ASSET = "airshare_gesture.onnx"
        const val HAND_LANDMARKER_ASSET = "hand_landmarker.task"
        const val LABELS_ASSET = "labels.json"
        const val PREPROCESSING_ASSET = "preprocessing.json"

        const val EXPECTED_INPUT_NAME = "landmarks"
        const val EXPECTED_INPUT_FEATURES = 63
        const val EXPECTED_OUTPUT_LABEL = "label"
        const val EXPECTED_OUTPUT_PROBABILITIES = "probabilities"
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.8f

        val DEFAULT_REQUIRED_ASSETS = listOf(
            MODEL_ASSET,
            HAND_LANDMARKER_ASSET,
            LABELS_ASSET,
            PREPROCESSING_ASSET
        )

        val FALLBACK_CLASS_ORDER = listOf("fist", "like", "point", "stop", "three_gun", "OTHER")

        fun mapClassNameToGestureType(className: String): GestureType {
            return when (className) {
                "fist" -> GestureType.FIST
                "like" -> GestureType.LIKE
                "point" -> GestureType.POINT
                "stop" -> GestureType.STOP
                "three_gun" -> GestureType.THREE_GUN
                "OTHER" -> GestureType.OTHER
                else -> GestureType.OTHER
            }
        }
    }

    private val sessionLock = Any()

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    @Volatile
    private var isClosed = false

    @Volatile
    private var confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD

    private var classOrder: List<String> = FALLBACK_CLASS_ORDER
    private var actionMap: Map<String, String> = emptyMap()
    private var diagnostics: OnnxModelDiagnostics = OnnxModelDiagnostics()

    init {
        initializeClassifier()
    }

    private fun assetExists(assetName: String): Boolean {
        return try {
            context.assets.open(assetName).use { true }
        } catch (_: FileNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun readAssetText(assetName: String): String {
        return context.assets.open(assetName).bufferedReader().use { it.readText() }
    }

    private fun readAssetBytes(assetName: String): ByteArray {
        return context.assets.open(assetName).use { it.readBytes() }
    }

    private fun initializeClassifier() {
        val requiredAssets = requiredAssetOverrides ?: DEFAULT_REQUIRED_ASSETS
        val missing = requiredAssets.filter { !assetExists(it) }

        if (missing.isNotEmpty()) {
            val msg = "Missing required asset(s) in app/src/main/assets/: ${missing.joinToString(", ")}"
            Log.e(TAG, msg)
            diagnostics = OnnxModelDiagnostics(
                isLoaded = false,
                missingAssets = missing,
                classOrder = classOrder,
                statusMessage = msg
            )
            return
        }

        try {
            // 1. Parse labels.json
            val labelsJson = JSONObject(readAssetText(LABELS_ASSET))
            val parsedOrder = mutableListOf<String>()
            val classArray = labelsJson.getJSONArray("class_order")
            for (i in 0 until classArray.length()) {
                parsedOrder.add(classArray.getString(i))
            }
            if (parsedOrder.isEmpty()) {
                throw IllegalStateException("$LABELS_ASSET has empty class_order")
            }
            classOrder = parsedOrder

            val parsedActions = mutableMapOf<String, String>()
            val actionsObj = labelsJson.optJSONObject("actions")
            if (actionsObj != null) {
                val keys = actionsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    parsedActions[key] = actionsObj.optString(key, "")
                }
            }
            actionMap = parsedActions

            // 2. Parse preprocessing.json
            val prepJson = JSONObject(readAssetText(PREPROCESSING_ASSET))
            confidenceThreshold = prepJson.optDouble(
                "recommended_confidence_threshold",
                DEFAULT_CONFIDENCE_THRESHOLD.toDouble()
            ).toFloat().coerceIn(0.1f, 0.99f)
            val rawUnits = prepJson.optString("raw_units", "x*W, y*H, z*W")
            val leftRight = prepJson.optString("left_right", "no mirroring at inference")

            // 3. Load ONNX Runtime model & measure wall load time
            val loadStartNs = System.nanoTime()
            val modelBytes = readAssetBytes(MODEL_ASSET)
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            val session = env.createSession(modelBytes, sessionOptions)
            val loadTimeMs = maxOf(1L, (System.nanoTime() - loadStartNs) / 1_000_000L)

            // 4. Inspect runtime input & output metadata rather than assuming blind indices
            val inputInfoMap = session.inputInfo
            val outputInfoMap = session.outputInfo

            val inputSummary = inputInfoMap.entries.joinToString("; ") { (name, nodeInfo) ->
                val info = nodeInfo.info
                if (info is TensorInfo) {
                    "$name: ${info.type}${info.shape.contentToString()}"
                } else {
                    "$name: ${info?.javaClass?.simpleName ?: "unknown"}"
                }
            }
            val outputSummary = outputInfoMap.entries.joinToString("; ") { (name, nodeInfo) ->
                val info = nodeInfo.info
                if (info is TensorInfo) {
                    "$name: ${info.type}${info.shape.contentToString()}"
                } else {
                    "$name: ${info?.javaClass?.simpleName ?: "unknown"}"
                }
            }

            // Validate actual input tensor contract
            val landmarksNode = inputInfoMap[EXPECTED_INPUT_NAME]
                ?: throw IllegalStateException("ONNX input '$EXPECTED_INPUT_NAME' missing. Found: ${inputInfoMap.keys}")
            val landmarksTensorInfo = landmarksNode.info as? TensorInfo
                ?: throw IllegalStateException("ONNX input '$EXPECTED_INPUT_NAME' is not a Tensor")
            if (landmarksTensorInfo.type != OnnxJavaType.FLOAT) {
                throw IllegalStateException("ONNX input '$EXPECTED_INPUT_NAME' expected FLOAT but got ${landmarksTensorInfo.type}")
            }
            val inShape = landmarksTensorInfo.shape
            if (inShape.size != 2 || inShape[1] != EXPECTED_INPUT_FEATURES.toLong()) {
                throw IllegalStateException("ONNX input '$EXPECTED_INPUT_NAME' expected shape [*, $EXPECTED_INPUT_FEATURES] but got ${inShape.contentToString()}")
            }

            // Validate actual outputs ("label" and "probabilities")
            if (!outputInfoMap.containsKey(EXPECTED_OUTPUT_LABEL)) {
                throw IllegalStateException("ONNX output '$EXPECTED_OUTPUT_LABEL' missing. Found: ${outputInfoMap.keys}")
            }
            val probsNode = outputInfoMap[EXPECTED_OUTPUT_PROBABILITIES]
                ?: throw IllegalStateException("ONNX output '$EXPECTED_OUTPUT_PROBABILITIES' missing. Found: ${outputInfoMap.keys}")
            val probsTensorInfo = probsNode.info as? TensorInfo
            if (probsTensorInfo != null && probsTensorInfo.shape.size >= 2 && probsTensorInfo.shape[1] > 0 && probsTensorInfo.shape[1].toInt() != classOrder.size) {
                throw IllegalStateException(
                    "ONNX '$EXPECTED_OUTPUT_PROBABILITIES' dim[1]=${probsTensorInfo.shape[1]} disagrees with labels.json class_order size=${classOrder.size}"
                )
            }

            ortEnv = env
            ortSession = session
            diagnostics = OnnxModelDiagnostics(
                isLoaded = true,
                missingAssets = emptyList(),
                loadTimeMs = loadTimeMs,
                inputMetadata = inputSummary,
                outputMetadata = outputSummary,
                classOrder = classOrder,
                actions = actionMap,
                preprocessingSummary = "$rawUnits | $leftRight",
                statusMessage = "ONNX Runtime loaded ($MODEL_ASSET in ${loadTimeMs}ms)"
            )
            Log.i(TAG, "Loaded $MODEL_ASSET in ${loadTimeMs}ms | in=[$inputSummary] | out=[$outputSummary]")
        } catch (e: Exception) {
            try {
                ortSession?.close()
            } catch (_: Exception) {
            }
            ortSession = null
            val errMsg = "ONNX initialization failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.e(TAG, errMsg, e)
            diagnostics = OnnxModelDiagnostics(
                isLoaded = false,
                missingAssets = emptyList(),
                classOrder = classOrder,
                statusMessage = errMsg
            )
        }
    }

    fun getDiagnostics(): OnnxModelDiagnostics = diagnostics

    override fun isModelLoaded(): Boolean = !isClosed && diagnostics.isLoaded && ortSession != null

    override fun getLoadTimeMs(): Long = diagnostics.loadTimeMs

    override fun getClassOrder(): List<String> = classOrder

    override fun getConfidenceThreshold(): Float = confidenceThreshold

    override fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold.coerceIn(0.1f, 0.99f)
    }

    override fun getModelInfo(): String {
        val d = diagnostics
        return buildString {
            appendLine("Runtime: ONNX Runtime Android (${if (d.isLoaded) "LOADED" else "UNAVAILABLE"})")
            appendLine("Load time: ${d.loadTimeMs} ms | Threshold: ${String.format(java.util.Locale.US, "%.2f", confidenceThreshold)}")
            appendLine("Input: ${d.inputMetadata}")
            appendLine("Outputs: ${d.outputMetadata}")
            appendLine("Classes (${d.classOrder.size}): ${d.classOrder.joinToString(", ")}")
            if (d.missingAssets.isNotEmpty()) {
                appendLine("Missing assets: ${d.missingAssets.joinToString(", ")}")
            } else {
                append("Status: ${d.statusMessage}")
            }
        }.trim()
    }

    override fun classifyFeatures(features: FloatArray?): GestureResult {
        val now = System.currentTimeMillis()
        if (features == null || features.size != EXPECTED_INPUT_FEATURES) {
            return GestureResult(
                gesture = GestureType.NONE,
                confidence = 0.0f,
                timestamp = now,
                predictedClass = "no hand",
                predictedClassIndex = -1,
                classProbabilities = emptyList(),
                inferenceLatencyMs = 0L,
                inferenceLatencyUs = 0L,
                isHandDetected = false,
                isAccepted = false,
                errorMessage = if (!diagnostics.isLoaded) diagnostics.statusMessage else null
            )
        }

        for (value in features) {
            if (!value.isFinite()) {
                return GestureResult(
                    gesture = GestureType.OTHER,
                    confidence = 0.0f,
                    timestamp = now,
                    predictedClass = "OTHER",
                    predictedClassIndex = -1,
                    classProbabilities = emptyList(),
                    isHandDetected = true,
                    isAccepted = false,
                    errorMessage = "Invalid non-finite landmark feature value"
                )
            }
        }

        synchronized(sessionLock) {
            val env = ortEnv
            val session = ortSession
            if (isClosed || !diagnostics.isLoaded || env == null || session == null) {
                return GestureResult(
                    gesture = GestureType.NONE,
                    confidence = 0.0f,
                    timestamp = now,
                    predictedClass = "model_unavailable",
                    predictedClassIndex = -1,
                    classProbabilities = emptyList(),
                    isHandDetected = true,
                    isAccepted = false,
                    errorMessage = diagnostics.statusMessage
                )
            }

            return try {
                val t0 = System.nanoTime()
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(features),
                    longArrayOf(1L, EXPECTED_INPUT_FEATURES.toLong())
                ).use { inputTensor ->
                    session.run(mapOf(EXPECTED_INPUT_NAME to inputTensor)).use { result ->
                        val t1 = System.nanoTime()
                        val latencyUs = maxOf(1L, (t1 - t0) / 1_000L)
                        val latencyMs = (t1 - t0) / 1_000_000L

                        val probsOpt = result.get(EXPECTED_OUTPUT_PROBABILITIES)
                        val probsValue = if (probsOpt.isPresent) probsOpt.get() else {
                            throw IllegalStateException("Missing '$EXPECTED_OUTPUT_PROBABILITIES' output in ONNX result")
                        }
                        val probs = extractProbabilities(probsValue)
                        if (probs.size != classOrder.size) {
                            throw IllegalStateException("Expected ${classOrder.size} probabilities, got ${probs.size}")
                        }

                        var bestIdx = 0
                        var bestProb = probs[0]
                        for (i in 1 until probs.size) {
                            if (probs[i] > bestProb) {
                                bestProb = probs[i]
                                bestIdx = i
                            }
                        }

                        val rawClass = classOrder[bestIdx]
                        val threshold = confidenceThreshold
                        val accepted = rawClass != "OTHER" && bestProb >= threshold
                        val gestureType = if (accepted) {
                            mapClassNameToGestureType(rawClass)
                        } else {
                            GestureType.OTHER
                        }

                        GestureResult(
                            gesture = gestureType,
                            confidence = bestProb.coerceIn(0f, 1f),
                            timestamp = now,
                            predictedClass = rawClass,
                            predictedClassIndex = bestIdx,
                            classProbabilities = probs.toList(),
                            inferenceLatencyMs = latencyMs,
                            inferenceLatencyUs = latencyUs,
                            isHandDetected = true,
                            isAccepted = accepted,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ONNX inference error", e)
                GestureResult(
                    gesture = GestureType.NONE,
                    confidence = 0.0f,
                    timestamp = now,
                    predictedClass = "inference_error",
                    predictedClassIndex = -1,
                    classProbabilities = emptyList(),
                    isHandDetected = true,
                    isAccepted = false,
                    errorMessage = "ONNX inference error: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    private fun extractProbabilities(value: ai.onnxruntime.OnnxValue): FloatArray {
        return when (value) {
            is OnnxTensor -> {
                val fb = value.floatBuffer
                if (fb != null) {
                    val arr = FloatArray(fb.remaining())
                    fb.get(arr)
                    arr
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val nested = value.value as? Array<FloatArray>
                        ?: throw IllegalStateException("Unexpected ONNX tensor probability value type: ${value.value?.javaClass}")
                    nested.firstOrNull() ?: FloatArray(0)
                }
            }
            is OnnxSequence -> {
                val first = value.value.firstOrNull()
                if (first is OnnxMap) {
                    val map = first.value
                    FloatArray(classOrder.size) { idx ->
                        val clsName = classOrder[idx]
                        ((map[clsName] ?: map[idx.toLong()] ?: 0.0f) as Number).toFloat()
                    }
                } else {
                    throw IllegalStateException("Unexpected ONNX sequence element type: ${first?.javaClass}")
                }
            }
            else -> throw IllegalStateException("Unsupported probabilities output value: ${value.javaClass}")
        }
    }

    override fun close() {
        synchronized(sessionLock) {
            if (isClosed) return
            isClosed = true
            try {
                ortSession?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing OrtSession", e)
            }
            ortSession = null
        }
    }
}
