package com.example.manager

import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface GestureRecognitionManager {
    val sessionState: StateFlow<GestureSessionState>
    val currentGesture: StateFlow<GestureResult>
    val isTrackingHand: StateFlow<Boolean>
    val classifierInfo: String

    fun startSession()
    fun stopSession()
    fun resetSession()
    fun simulateGestureForTesting(gesture: GestureType, confidence: Float = 0.95f)
}

class DefaultGestureRecognitionManager(
    private val classifier: GestureClassifier = HaGridV2OnnxClassifierPlaceholder()
) : GestureRecognitionManager {

    private val _sessionState = MutableStateFlow(GestureSessionState.IDLE)
    override val sessionState: StateFlow<GestureSessionState> = _sessionState.asStateFlow()

    private val _currentGesture = MutableStateFlow(GestureResult(GestureType.NONE, 0.0f))
    override val currentGesture: StateFlow<GestureResult> = _currentGesture.asStateFlow()

    private val _isTrackingHand = MutableStateFlow(false)
    override val isTrackingHand: StateFlow<Boolean> = _isTrackingHand.asStateFlow()

    override val classifierInfo: String
        get() = classifier.getModelInfo()

    override fun startSession() {
        if (_sessionState.value == GestureSessionState.IDLE || _sessionState.value == GestureSessionState.CANCELLED) {
            _sessionState.value = GestureSessionState.IDLE
        }
    }

    override fun stopSession() {
        _sessionState.value = GestureSessionState.IDLE
        _currentGesture.value = GestureResult(GestureType.NONE, 0f)
        _isTrackingHand.value = false
    }

    override fun resetSession() {
        _sessionState.value = GestureSessionState.IDLE
        _currentGesture.value = GestureResult(GestureType.NONE, 0f)
        _isTrackingHand.value = false
    }

    /**
     * Testing/diagnostic hook to step through the gesture state machine.
     * Clearly marked as developer simulation to test the 5-command lifecycle
     * while the HaGRIDv2 ONNX model is in standby.
     */
    override fun simulateGestureForTesting(gesture: GestureType, confidence: Float) {
        _isTrackingHand.value = (gesture != GestureType.NONE)
        _currentGesture.value = GestureResult(gesture, confidence)

        when (gesture) {
            GestureType.LIKE -> {
                // Activate
                if (_sessionState.value == GestureSessionState.IDLE || _sessionState.value == GestureSessionState.CANCELLED) {
                    _sessionState.value = GestureSessionState.ACTIVATED
                }
            }
            GestureType.FIST -> {
                // Grab content
                if (_sessionState.value == GestureSessionState.ACTIVATED) {
                    _sessionState.value = GestureSessionState.CONTENT_HELD
                }
            }
            GestureType.POINT -> {
                // Select device
                if (_sessionState.value == GestureSessionState.CONTENT_HELD) {
                    _sessionState.value = GestureSessionState.TARGET_LOCKED
                }
            }
            GestureType.THREE_GUN -> {
                // Release / send
                if (_sessionState.value == GestureSessionState.TARGET_LOCKED) {
                    _sessionState.value = GestureSessionState.TRANSFERRING
                }
            }
            GestureType.STOP -> {
                // Cancel
                _sessionState.value = GestureSessionState.CANCELLED
            }
            GestureType.NONE -> {
                // No change in state
            }
        }
    }
}
