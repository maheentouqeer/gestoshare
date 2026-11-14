package com.example.manager

import com.example.model.GestureResult
import com.example.model.GestureType
import java.util.ArrayDeque

data class TemporalGestureConfig(
    val windowSize: Int = 5,
    val requiredConsecutiveFrames: Int = 5,
    val useMajorityVote: Boolean = false,
    val majorityVoteThreshold: Int = 5,
    val confidenceThreshold: Float = HaGridV2OnnxClassifier.DEFAULT_CONFIDENCE_THRESHOLD,
    val holdDurationMs: Long = 200L,
    val commandCooldownMs: Long = 1000L,
    val requireNeutralReset: Boolean = true,
    val neutralFramesToReset: Int = 1
) {
    init {
        require(windowSize >= 1) { "windowSize must be >= 1" }
        require(requiredConsecutiveFrames >= 1) { "requiredConsecutiveFrames must be >= 1" }
        require(majorityVoteThreshold >= 1) { "majorityVoteThreshold must be >= 1" }
    }
}

data class StabilizedGestureDecision(
    val triggeredGesture: GestureType? = null,
    val stableGesture: GestureType = GestureType.NONE,
    val rawPrediction: GestureResult = GestureResult(),
    val consecutiveCount: Int = 0,
    val requiredConsecutiveFrames: Int = 5,
    val windowVotes: List<GestureType> = emptyList(),
    val holdElapsedMs: Long = 0L,
    val isHeldLocked: Boolean = false,
    val isCooldownActive: Boolean = false,
    val cooldownRemainingMs: Long = 0L,
    val awaitingNeutralResetFor: GestureType? = null,
    val statusReason: String = "Awaiting hand"
)

/**
 * Temporal gesture smoothing, hold verification, cooldown, and held-gesture debounce.
 *
 * Thread-safe and completely decoupled from Compose UI timers: runs on MediaPipe's
 * background callback thread or test threads with deterministic timestamp support.
 */
class TemporalGestureStabilizer(
    initialConfig: TemporalGestureConfig = TemporalGestureConfig()
) {
    private val lock = Any()

    @Volatile
    private var config: TemporalGestureConfig = initialConfig

    private val rollingWindow = ArrayDeque<GestureType>()
    private var currentStreakGesture: GestureType? = null
    private var consecutiveCount: Int = 0
    private var streakStartMs: Long = 0L

    private var lastTriggeredTimeMs: Long = 0L
    private var latchedGesture: GestureType? = null
    private var neutralStreakCount: Int = 0

    fun getConfig(): TemporalGestureConfig = config

    fun updateConfig(transform: (TemporalGestureConfig) -> TemporalGestureConfig) {
        synchronized(lock) {
            config = transform(config)
            while (rollingWindow.size > config.windowSize) {
                rollingWindow.removeFirst()
            }
        }
    }

    fun setConfidenceThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.99f)
        updateConfig { it.copy(confidenceThreshold = clamped) }
    }

    fun reset() {
        synchronized(lock) {
            rollingWindow.clear()
            currentStreakGesture = null
            consecutiveCount = 0
            streakStartMs = 0L
            lastTriggeredTimeMs = 0L
            latchedGesture = null
            neutralStreakCount = 0
        }
    }

    fun process(
        prediction: GestureResult,
        nowMs: Long = prediction.timestamp
    ): StabilizedGestureDecision {
        synchronized(lock) {
            val cfg = config

            // 1. Ignore model / runtime errors without corrupting streak or releasing latch
            if (!prediction.errorMessage.isNullOrBlank()) {
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = GestureType.OTHER,
                    rawPrediction = prediction,
                    consecutiveCount = consecutiveCount,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = "Ignored classifier error: ${prediction.errorMessage}"
                )
            }

            // 2. Hand not detected -> ignore trigger, reset command streak, count toward neutral reset
            if (!prediction.isHandDetected || prediction.gesture == GestureType.NONE) {
                currentStreakGesture = null
                consecutiveCount = 0
                streakStartMs = 0L
                pushWindowVote(GestureType.NONE, cfg.windowSize)
                neutralStreakCount++
                if (cfg.requireNeutralReset && latchedGesture != null && neutralStreakCount >= cfg.neutralFramesToReset) {
                    latchedGesture = null
                }
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = GestureType.NONE,
                    rawPrediction = prediction,
                    consecutiveCount = 0,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = "No hand detected"
                )
            }

            // 3. OTHER or below confidence threshold -> treat as NEUTRAL, reset streak, release held latch
            val effectiveThreshold = cfg.confidenceThreshold
            val isNeutralOrUncertain = !prediction.isAccepted ||
                prediction.gesture == GestureType.OTHER ||
                prediction.confidence < effectiveThreshold

            if (isNeutralOrUncertain) {
                currentStreakGesture = null
                consecutiveCount = 0
                streakStartMs = 0L
                pushWindowVote(GestureType.OTHER, cfg.windowSize)
                neutralStreakCount++
                if (cfg.requireNeutralReset && latchedGesture != null && neutralStreakCount >= cfg.neutralFramesToReset) {
                    latchedGesture = null
                }
                val pct = (prediction.confidence * 100).toInt()
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = GestureType.OTHER,
                    rawPrediction = prediction,
                    consecutiveCount = 0,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = if (prediction.predictedClass == "OTHER") {
                        "Neutral pose (OTHER $pct%) — command trigger disarmed"
                    } else {
                        "Uncertain ${prediction.predictedClass} ($pct% < ${(effectiveThreshold * 100).toInt()}%)"
                    }
                )
            }

            // 4. Valid accepted command frame (LIKE / FIST / POINT / THREE_GUN / STOP)
            val gesture = prediction.gesture
            neutralStreakCount = 0
            pushWindowVote(gesture, cfg.windowSize)

            if (currentStreakGesture == gesture) {
                consecutiveCount++
            } else {
                currentStreakGesture = gesture
                consecutiveCount = 1
                streakStartMs = nowMs
            }

            val holdElapsed = (nowMs - streakStartMs).coerceAtLeast(0L)
            val windowSupport = rollingWindow.count { it == gesture }
            val stabilityMet = if (cfg.useMajorityVote) {
                windowSupport >= cfg.majorityVoteThreshold
            } else {
                consecutiveCount >= cfg.requiredConsecutiveFrames
            }

            val cooldownElapsed = if (lastTriggeredTimeMs > 0L) (nowMs - lastTriggeredTimeMs).coerceAtLeast(0L) else Long.MAX_VALUE
            val cooldownRemaining = if (lastTriggeredTimeMs > 0L && cooldownElapsed < cfg.commandCooldownMs) {
                cfg.commandCooldownMs - cooldownElapsed
            } else {
                0L
            }

            if (cfg.requireNeutralReset && latchedGesture == gesture) {
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = gesture,
                    rawPrediction = prediction,
                    consecutiveCount = consecutiveCount,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    holdElapsedMs = holdElapsed,
                    isHeldLocked = true,
                    isCooldownActive = cooldownRemaining > 0L,
                    cooldownRemainingMs = cooldownRemaining,
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = "${gesture.name} held — relax to OTHER/neutral hand to re-arm"
                )
            }

            if (!stabilityMet) {
                val target = if (cfg.useMajorityVote) cfg.majorityVoteThreshold else cfg.requiredConsecutiveFrames
                val current = if (cfg.useMajorityVote) windowSupport else consecutiveCount
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = GestureType.OTHER,
                    rawPrediction = prediction,
                    consecutiveCount = consecutiveCount,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    holdElapsedMs = holdElapsed,
                    isCooldownActive = cooldownRemaining > 0L,
                    cooldownRemainingMs = cooldownRemaining,
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = "Stabilizing ${gesture.name}: $current/$target frames"
                )
            }

            if (holdElapsed < cfg.holdDurationMs) {
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = gesture,
                    rawPrediction = prediction,
                    consecutiveCount = consecutiveCount,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    holdElapsedMs = holdElapsed,
                    isCooldownActive = cooldownRemaining > 0L,
                    cooldownRemainingMs = cooldownRemaining,
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = "Holding ${gesture.name}: ${holdElapsed}/${cfg.holdDurationMs}ms"
                )
            }

            if (cooldownRemaining > 0L) {
                return StabilizedGestureDecision(
                    triggeredGesture = null,
                    stableGesture = gesture,
                    rawPrediction = prediction,
                    consecutiveCount = consecutiveCount,
                    requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                    windowVotes = rollingWindow.toList(),
                    holdElapsedMs = holdElapsed,
                    isCooldownActive = true,
                    cooldownRemainingMs = cooldownRemaining,
                    awaitingNeutralResetFor = latchedGesture,
                    statusReason = "Cooldown active (${cooldownRemaining}ms remaining)"
                )
            }

            // Trigger command once and latch until neutral reset
            lastTriggeredTimeMs = nowMs
            if (cfg.requireNeutralReset) {
                latchedGesture = gesture
            }

            return StabilizedGestureDecision(
                triggeredGesture = gesture,
                stableGesture = gesture,
                rawPrediction = prediction,
                consecutiveCount = consecutiveCount,
                requiredConsecutiveFrames = cfg.requiredConsecutiveFrames,
                windowVotes = rollingWindow.toList(),
                holdElapsedMs = holdElapsed,
                isHeldLocked = cfg.requireNeutralReset,
                isCooldownActive = false,
                cooldownRemainingMs = 0L,
                awaitingNeutralResetFor = latchedGesture,
                statusReason = "Triggered ${gesture.name} (${gesture.actionName})"
            )
        }
    }

    private fun pushWindowVote(vote: GestureType, maxSize: Int) {
        rollingWindow.addLast(vote)
        while (rollingWindow.size > maxSize) {
            rollingWindow.removeFirst()
        }
    }
}
