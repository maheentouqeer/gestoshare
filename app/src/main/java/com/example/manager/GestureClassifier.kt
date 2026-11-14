package com.example.manager

import com.example.model.GestureResult
import com.example.model.GestureType

/**
 * Interface defining the HaGRIDv2 gesture classification contract.
 * Consumes a [1, 63] wrist-centered, palm-scale-normalized FLOAT32 feature vector
 * extracted from MediaPipe image-coordinate landmarks.
 */
interface GestureClassifier : AutoCloseable {
    fun isModelLoaded(): Boolean
    fun getModelInfo(): String
    fun getLoadTimeMs(): Long
    fun getClassOrder(): List<String>
    fun getConfidenceThreshold(): Float
    fun setConfidenceThreshold(threshold: Float)
    fun classifyFeatures(features: FloatArray?): GestureResult
    suspend fun classifyGesture(inputData: Any?): GestureResult {
        return classifyFeatures(inputData as? FloatArray)
    }
    override fun close() {}
}

/**
 * Fallback placeholder when no ONNX model context is provided (e.g., isolated unit tests).
 */
class HaGridV2OnnxClassifierPlaceholder : GestureClassifier {

    private val modelVersion = "HaGRIDv2-SVM-ONNX (Standby)"
    private val inputTensorDimensions = "[1, 63] FLOAT32 landmarks"
    private var threshold = 0.8f

    override fun isModelLoaded(): Boolean = false

    override fun getModelInfo(): String {
        return "Model: $modelVersion\nInput format: $inputTensorDimensions\nStatus: Placeholder"
    }

    override fun getLoadTimeMs(): Long = 0L

    override fun getClassOrder(): List<String> = listOf("fist", "like", "point", "stop", "three_gun", "OTHER")

    override fun getConfidenceThreshold(): Float = threshold

    override fun setConfidenceThreshold(threshold: Float) {
        this.threshold = threshold.coerceIn(0.1f, 0.99f)
    }

    override fun classifyFeatures(features: FloatArray?): GestureResult {
        return GestureResult(
            gesture = GestureType.NONE,
            confidence = 0.0f,
            timestamp = System.currentTimeMillis(),
            predictedClass = "no hand",
            predictedClassIndex = -1,
            isHandDetected = false,
            isAccepted = false
        )
    }
}
