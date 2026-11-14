package com.example.manager

import com.example.model.GestureResult
import com.example.model.GestureType

/**
 * Interface defining the gesture classification contract.
 *
 * Phase 6 provides live MediaPipe 21-landmark extraction -> [1, 63] normalized vector.
 * Phase 7 will feed that [1, 63] vector into the custom HaGRIDv2 ONNX Runtime model
 * to output probabilities across the 5 GestoShare command classes.
 */
interface GestureClassifier {
    fun isModelLoaded(): Boolean
    fun getModelInfo(): String
    suspend fun classifyGesture(inputData: Any?): GestureResult
}

/**
 * Placeholder implementation for the HaGRIDv2 ONNX gesture classifier (Phase 7).
 *
 * We do not invent fake ML inference. Phase 6 extracts real 21 hand keypoints via
 * MediaPipe HandLandmarker into a [1, 63] FloatArray; Phase 7 will plug in the
 * trained .onnx weights.
 */
class HaGridV2OnnxClassifierPlaceholder : GestureClassifier {

    private val modelVersion = "HaGRIDv2-Tiny-ONNX (5-Class Sub-graph: LIKE, FIST, POINT, THREE_GUN, STOP)"
    private val inputTensorDimensions = "[1, 63] (21 wrist-centered, scale-normalized MediaPipe XYZ landmarks)"

    override fun isModelLoaded(): Boolean {
        return false
    }

    override fun getModelInfo(): String {
        return "Model: $modelVersion\nInput tensor: $inputTensorDimensions\nStatus: Awaiting Phase 7 ONNX Runtime weights (.onnx)"
    }

    override suspend fun classifyGesture(inputData: Any?): GestureResult {
        return GestureResult(
            gesture = GestureType.NONE,
            confidence = 0.0f,
            timestamp = System.currentTimeMillis()
        )
    }
}
