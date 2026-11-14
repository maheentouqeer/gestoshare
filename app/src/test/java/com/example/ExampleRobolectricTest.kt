package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.manager.DefaultContentImportManager
import com.example.manager.DefaultGestureRecognitionManager
import com.example.manager.DefaultTransferStateManager
import com.example.manager.GestureActionEffect
import com.example.manager.GestureSessionContext
import com.example.manager.HaGridV2OnnxClassifier
import com.example.manager.MediaPipeHandTracker
import com.example.manager.NormalizedLandmarkPoint
import com.example.manager.TemporalGestureConfig
import com.example.manager.TemporalGestureStabilizer
import com.example.model.ActiveTransfer
import com.example.model.ConnectionSession
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.GestureOperationMode
import com.example.model.GestureResult
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.model.SessionStatus
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.util.HashUtil
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GestoShare", appName)
    }

    @Test
    fun `test content import text and url creation computes sha256`() {
        val manager = DefaultContentImportManager()
        val textItem = manager.createFromText("Secret GestoShare passcode")
        assertEquals(ContentType.TEXT, textItem.type)
        assertEquals("text/plain", textItem.mimeType)
        assertEquals(64, textItem.sha256Hash?.length)

        val urlItem = manager.createFromUrl("github.com/aistudio/gestoshare")
        assertEquals(ContentType.URL, urlItem.type)
        assertTrue(urlItem.textContent?.startsWith("https://") == true)
        assertEquals(64, urlItem.sha256Hash?.length)
        assertEquals("1.0 MB", manager.formatFileSize(1024 * 1024))
    }

    @Test
    fun `test HashUtil streaming sha256 and mismatch detection`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileA = File(context.cacheDir, "test_hash_a.bin")
        val fileB = File(context.cacheDir, "test_hash_b.bin")
        try {
            fileA.writeText("GestoShare-Payload-Verify-12345")
            fileB.writeText("GestoShare-Payload-Corrupted-999")

            val hashA1 = HashUtil.calculateSha256(fileA)
            val hashA2 = HashUtil.calculateSha256(fileA)
            val hashB = HashUtil.calculateSha256(fileB)

            assertEquals(64, hashA1.length)
            assertTrue(HashUtil.verifyHash(hashA1, hashA2))
            assertFalse(HashUtil.verifyHash(hashA1, hashB))
            assertFalse(HashUtil.verifyHash("", hashA1))
        } finally {
            fileA.delete()
            fileB.delete()
        }
    }

    @Test
    fun `test MediaPipe 21-landmark normalization produces 63-float wrist-centered unit scale vector`() {
        val landmarks = MutableList(21) { idx ->
            when (idx) {
                0 -> NormalizedLandmarkPoint(x = 0.5f, y = 0.5f, z = 0.0f)
                9 -> NormalizedLandmarkPoint(x = 0.5f, y = 0.3f, z = 0.0f)
                else -> NormalizedLandmarkPoint(x = 0.5f + idx * 0.01f, y = 0.5f - idx * 0.01f, z = 0.02f)
            }
        }

        val vec = MediaPipeHandTracker.normalizeLandmarksToFeatureVector(landmarks)
        assertEquals(63, vec.size)

        assertEquals(0f, vec[0], 1e-4f)
        assertEquals(0f, vec[1], 1e-4f)
        assertEquals(0f, vec[2], 1e-4f)

        assertEquals(0f, vec[9 * 3], 1e-4f)
        assertEquals(-1f, vec[9 * 3 + 1], 1e-4f)
        assertEquals(0f, vec[9 * 3 + 2], 1e-4f)
    }

    @Test
    fun `test transfer state manager staging`() {
        val stateManager = DefaultTransferStateManager()
        val manager = DefaultContentImportManager()
        val item = manager.createFromText("Demo note")

        stateManager.stageItem(item)
        assertEquals(1, stateManager.stagedItems.value.size)
        assertTrue(stateManager.isReadyForGestureTransfer.value)

        stateManager.clearStagedItems()
        assertEquals(0, stateManager.stagedItems.value.size)
    }

    @Test
    fun `test device item real endpointId and connection session model`() {
        val device = DeviceItem(
            endpointId = "XYZ123",
            name = "Redmi Note 12",
            connectionState = DeviceConnectionState.DISCOVERED
        )
        assertEquals("XYZ123", device.endpointId)
        assertEquals("XYZ123", device.id)
        assertEquals("Redmi Note 12", device.name)

        val session = ConnectionSession(
            endpointId = "XYZ123",
            endpointName = "Redmi Note 12",
            authDigits = "782914",
            isIncoming = true,
            status = SessionStatus.INITIATED
        )
        assertEquals("782914", session.authDigits)
        assertTrue(session.isIncoming)
    }

    // -------------------------------------------------------------------------
    // Phase 7: Real HaGRIDv2 ONNX Runtime & Python Preprocessing Parity Tests
    // -------------------------------------------------------------------------

    @Test
    fun `onnx_test_1 model and all 4 required assets load cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val diag = classifier.getDiagnostics()
            assertTrue("Expected all assets loaded, got: ${diag.statusMessage}", classifier.isModelLoaded())
            assertTrue("Expected empty missingAssets, got: ${diag.missingAssets}", diag.missingAssets.isEmpty())
            assertTrue("Load time should be > 0ms", classifier.getLoadTimeMs() > 0L)
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_2 input metadata matches landmarks FLOAT and 63 features`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val inputMeta = classifier.getDiagnostics().inputMetadata
            assertTrue("Input metadata should contain landmarks: $inputMeta", inputMeta.contains("landmarks"))
            assertTrue("Input metadata should contain FLOAT: $inputMeta", inputMeta.contains("FLOAT"))
            assertTrue("Input metadata should contain 63 features: $inputMeta", inputMeta.contains("63"))
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_3 output metadata exposes label and probabilities`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val outputMeta = classifier.getDiagnostics().outputMetadata
            assertTrue("Output metadata should contain label: $outputMeta", outputMeta.contains("label"))
            assertTrue("Output metadata should contain probabilities: $outputMeta", outputMeta.contains("probabilities"))
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_4 inference produces 6 valid probabilities summing to 1_0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val openPalm = buildCanonicalOpenPalmFeatures(imageWidth = 640, imageHeight = 480)
            val result = classifier.classifyFeatures(openPalm)
            assertEquals("Expected 6 class probabilities", 6, result.classProbabilities.size)
            assertTrue(result.isHandDetected)
            assertTrue(result.predictedClassIndex in 0..5)

            var probSum = 0f
            for (p in result.classProbabilities) {
                assertTrue("Probability out of [0,1]: $p", p in 0f..1f)
                probSum += p
            }
            assertEquals(1.0f, probSum, 1e-3f)
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_5 class ordering matches labels_json and default threshold is 0_8`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            assertEquals(
                listOf("fist", "like", "point", "stop", "three_gun", "OTHER"),
                classifier.getClassOrder()
            )
            assertEquals(0.8f, classifier.getConfidenceThreshold(), 1e-4f)
            assertEquals(GestureType.FIST, HaGridV2OnnxClassifier.mapClassNameToGestureType("fist"))
            assertEquals(GestureType.LIKE, HaGridV2OnnxClassifier.mapClassNameToGestureType("like"))
            assertEquals(GestureType.POINT, HaGridV2OnnxClassifier.mapClassNameToGestureType("point"))
            assertEquals(GestureType.STOP, HaGridV2OnnxClassifier.mapClassNameToGestureType("stop"))
            assertEquals(GestureType.THREE_GUN, HaGridV2OnnxClassifier.mapClassNameToGestureType("three_gun"))
            assertEquals(GestureType.OTHER, HaGridV2OnnxClassifier.mapClassNameToGestureType("OTHER"))
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_6 exact Python image-coordinate preprocessing parity on 640x480 and deterministic inference`() {
        val width = 640
        val height = 480
        val landmarks = MutableList(21) { idx ->
            when (idx) {
                0 -> NormalizedLandmarkPoint(0.5f, 0.5f, 0.0f)
                9 -> NormalizedLandmarkPoint(0.55f, 0.35f, -0.02f)
                else -> NormalizedLandmarkPoint(
                    x = 0.5f + idx * 0.005f,
                    y = 0.5f - idx * 0.01f,
                    z = -idx * 0.001f
                )
            }
        }

        val vec = MediaPipeHandTracker.normalizeImageCoordinatesToFeatureVector(landmarks, width, height)

        val expectedNorm9 = sqrt(32.0f * 32.0f + (-72.0f) * (-72.0f) + (-12.8f) * (-12.8f)) + 1e-6f
        assertEquals(0f, vec[0], 1e-6f)
        assertEquals(0f, vec[1], 1e-6f)
        assertEquals(0f, vec[2], 1e-6f)
        assertEquals(32.0f / expectedNorm9, vec[9 * 3], 1e-5f)
        assertEquals(-72.0f / expectedNorm9, vec[9 * 3 + 1], 1e-5f)
        assertEquals(-12.8f / expectedNorm9, vec[9 * 3 + 2], 1e-5f)

        val dx12 = (0.5f + 12 * 0.005f) * 640f - 320f
        val dy12 = (0.5f - 12 * 0.01f) * 480f - 240f
        val dz12 = (-12 * 0.001f) * 640f
        assertEquals(dx12 / expectedNorm9, vec[12 * 3], 1e-5f)
        assertEquals(dy12 / expectedNorm9, vec[12 * 3 + 1], 1e-5f)
        assertEquals(dz12 / expectedNorm9, vec[12 * 3 + 2], 1e-5f)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val openPalm = buildCanonicalOpenPalmFeatures(width, height)
            val r1 = classifier.classifyFeatures(openPalm)
            val r2 = classifier.classifyFeatures(openPalm)
            assertEquals(r1.predictedClass, r2.predictedClass)
            assertEquals(r1.confidence, r2.confidence, 1e-6f)
            assertEquals(r1.classProbabilities, r2.classProbabilities)
            assertTrue(r1.predictedClass in classifier.getClassOrder())

            classifier.setConfidenceThreshold(0.99f)
            val strictResult = classifier.classifyFeatures(openPalm)
            if (strictResult.confidence < 0.99f) {
                assertFalse(strictResult.isAccepted)
                assertEquals(GestureType.OTHER, strictResult.gesture)
            }
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_7 empty or wrong length input returns no hand safely without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val nullRes = classifier.classifyFeatures(null)
            assertEquals(GestureType.NONE, nullRes.gesture)
            assertEquals("no hand", nullRes.predictedClass)
            assertFalse(nullRes.isHandDetected)
            assertFalse(nullRes.isAccepted)

            val shortRes = classifier.classifyFeatures(FloatArray(12))
            assertEquals(GestureType.NONE, shortRes.gesture)
            assertFalse(shortRes.isHandDetected)
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_8 missing asset is reported cleanly without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(
            context = context,
            requiredAssetOverrides = listOf("nonexistent_gesture_model.onnx", HaGridV2OnnxClassifier.LABELS_ASSET)
        )
        try {
            assertFalse(classifier.isModelLoaded())
            val diag = classifier.getDiagnostics()
            assertTrue(diag.missingAssets.contains("nonexistent_gesture_model.onnx"))
            assertTrue(diag.statusMessage.contains("Missing required asset"))

            val res = classifier.classifyFeatures(FloatArray(63))
            assertFalse(res.isAccepted)
            assertNotNull(res.errorMessage)
        } finally {
            classifier.close()
        }
    }

    @Test
    fun `onnx_test_9 close releases ONNX session cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        assertTrue(classifier.isModelLoaded())
        classifier.close()
        assertFalse(classifier.isModelLoaded())

        classifier.close()
        val afterClose = classifier.classifyFeatures(FloatArray(63))
        assertFalse(afterClose.isAccepted)
        assertNotNull(afterClose.errorMessage)
    }

    @Test
    fun `onnx_test_10 repeated inference loop of 200 iterations succeeds without errors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = HaGridV2OnnxClassifier(context)
        try {
            val features = buildCanonicalOpenPalmFeatures(640, 480)
            var firstClass: String? = null
            repeat(200) { iter ->
                val res = classifier.classifyFeatures(features)
                assertTrue("Iteration $iter failed: ${res.errorMessage}", res.errorMessage == null)
                assertEquals(6, res.classProbabilities.size)
                assertTrue(abs(res.classProbabilities.sum() - 1f) < 1e-3f)
                if (firstClass == null) {
                    firstClass = res.predictedClass
                } else {
                    assertEquals(firstClass, res.predictedClass)
                }
            }
        } finally {
            classifier.close()
        }
    }

    // -------------------------------------------------------------------------
    // Phase 8: Full Gesture Workflow State Machine, Debounce, Safety & Error Tests
    // -------------------------------------------------------------------------

    private val testItem = ContentItem(
        id = "item-1",
        name = "RedmiPhoto.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 2048L,
        type = ContentType.IMAGE
    )

    private val peerA = DeviceItem(endpointId = "ep_redmi1", name = "Redmi Note 12")
    private val peerB = DeviceItem(endpointId = "ep_redmi2", name = "Redmi 13C")
    private val peerC = DeviceItem(endpointId = "ep_infinix", name = "Infinix Note 30")

    @Test
    fun `phase8_valid full workflow transitions IDLE to COMPLETED via real transfer callbacks`() {
        val manager = DefaultGestureRecognitionManager()
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)
        assertEquals(GestureSessionState.IDLE, manager.sessionState.value)

        // 1. LIKE -> ACTIVATED
        val likeEffect = manager.processCommandGesture(GestureType.LIKE, 0.95f, GestureSessionContext())
        assertTrue(likeEffect is GestureActionEffect.None)
        assertEquals(GestureSessionState.ACTIVATED, manager.sessionState.value)

        // 2. FIST -> CONTENT_HELD (with staged content)
        val fistEffect = manager.processCommandGesture(
            GestureType.FIST,
            0.94f,
            GestureSessionContext(stagedItems = listOf(testItem))
        )
        assertTrue(fistEffect is GestureActionEffect.GrabContentConfirmed)
        assertTrue(manager.isContentGrabbed.value)
        assertEquals(GestureSessionState.CONTENT_HELD, manager.sessionState.value)

        // 3. POINT -> TARGET_SELECTED
        val pointEffect = manager.processCommandGesture(
            GestureType.POINT,
            0.93f,
            GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = listOf(peerA, peerB))
        )
        assertTrue(pointEffect is GestureActionEffect.SelectPeerDevice)
        assertEquals(peerA, (pointEffect as GestureActionEffect.SelectPeerDevice).device)
        assertEquals(GestureSessionState.TARGET_SELECTED, manager.sessionState.value)

        // 4. Nearby Pairing -> CONNECTION_PENDING -> CONNECTED
        val pendingPair = ConnectionSession("ep_redmi1", peerA.name, "123456", false, SessionStatus.INITIATED)
        manager.syncWithSystemState(
            GestureSessionContext(
                stagedItems = listOf(testItem),
                discoveredDevices = listOf(peerA, peerB),
                selectedDevice = peerA,
                pendingSession = pendingPair
            )
        )
        assertEquals(GestureSessionState.CONNECTION_PENDING, manager.sessionState.value)

        manager.syncWithSystemState(
            GestureSessionContext(
                stagedItems = listOf(testItem),
                discoveredDevices = listOf(peerA, peerB),
                selectedDevice = peerA,
                connectedDevice = peerA
            )
        )
        assertEquals(GestureSessionState.CONNECTED, manager.sessionState.value)

        // 5. THREE_GUN -> TRANSFER_OFFERED -> WAITING_FOR_RECEIVER -> TRANSFERRING -> VERIFYING -> COMPLETED
        val sendEffect = manager.processCommandGesture(
            GestureType.THREE_GUN,
            0.96f,
            GestureSessionContext(
                stagedItems = listOf(testItem),
                discoveredDevices = listOf(peerA, peerB),
                selectedDevice = peerA,
                connectedDevice = peerA
            )
        )
        assertTrue(sendEffect is GestureActionEffect.DispatchTransferOffer)
        assertFalse((sendEffect as GestureActionEffect.DispatchTransferOffer).isDryRun)
        assertEquals(GestureSessionState.TRANSFER_OFFERED, manager.sessionState.value)

        val baseTransfer = ActiveTransfer(
            id = "tx-100",
            senderName = "Redmi 1",
            receiverName = peerA.name,
            items = listOf(testItem),
            totalBytes = 2048L,
            direction = TransferDirection.SEND
        )

        manager.syncWithSystemState(
            GestureSessionContext(stagedItems = listOf(testItem), selectedDevice = peerA, connectedDevice = peerA,
                activeTransfer = baseTransfer.copy(status = TransferStatus.AWAITING_ACCEPTANCE))
        )
        assertEquals(GestureSessionState.WAITING_FOR_RECEIVER, manager.sessionState.value)

        manager.syncWithSystemState(
            GestureSessionContext(stagedItems = listOf(testItem), selectedDevice = peerA, connectedDevice = peerA,
                activeTransfer = baseTransfer.copy(status = TransferStatus.TRANSFERRING, progress = 0.5f))
        )
        assertEquals(GestureSessionState.TRANSFERRING, manager.sessionState.value)

        manager.syncWithSystemState(
            GestureSessionContext(stagedItems = listOf(testItem), selectedDevice = peerA, connectedDevice = peerA,
                activeTransfer = baseTransfer.copy(status = TransferStatus.VERIFYING, progress = 1.0f))
        )
        assertEquals(GestureSessionState.VERIFYING, manager.sessionState.value)

        // Unverified SUCCESS must fail rather than falsely completing
        manager.syncWithSystemState(
            GestureSessionContext(stagedItems = listOf(testItem), selectedDevice = peerA, connectedDevice = peerA,
                activeTransfer = baseTransfer.copy(status = TransferStatus.SUCCESS, isIntegrityVerified = false))
        )
        assertEquals(GestureSessionState.FAILED, manager.sessionState.value)

        // Verified SHA-256 completion marks COMPLETED
        manager.bindActiveTransferId("tx-101")
        val verifiedTx = baseTransfer.copy(id = "tx-101", status = TransferStatus.SUCCESS, isIntegrityVerified = true)
        manager.syncWithSystemState(
            GestureSessionContext(stagedItems = listOf(testItem), selectedDevice = peerA, connectedDevice = peerA,
                activeTransfer = verifiedTx)
        )
        assertEquals(GestureSessionState.COMPLETED, manager.sessionState.value)
    }

    @Test
    fun `phase8_invalid state transitions are rejected cleanly`() {
        val manager = DefaultGestureRecognitionManager()
        val ctx = GestureSessionContext(
            stagedItems = listOf(testItem),
            discoveredDevices = listOf(peerA),
            selectedDevice = peerA,
            connectedDevice = peerA
        )

        // Cannot FIST, POINT, THREE_GUN, or STOP from IDLE
        manager.processCommandGesture(GestureType.FIST, 0.9f, ctx)
        assertEquals(GestureSessionState.IDLE, manager.sessionState.value)

        manager.processCommandGesture(GestureType.POINT, 0.9f, ctx)
        assertEquals(GestureSessionState.IDLE, manager.sessionState.value)

        val prematureSend = manager.processCommandGesture(GestureType.THREE_GUN, 0.9f, ctx)
        assertFalse(prematureSend is GestureActionEffect.DispatchTransferOffer)
        assertEquals(GestureSessionState.IDLE, manager.sessionState.value)

        val idleStop = manager.processCommandGesture(GestureType.STOP, 0.9f, ctx)
        assertFalse(idleStop is GestureActionEffect.CancelActiveWorkflow)
        assertEquals(GestureSessionState.IDLE, manager.sessionState.value)

        // Activate -> cannot skip FIST with POINT or THREE_GUN; repeated LIKE ignored
        manager.processCommandGesture(GestureType.LIKE, 0.9f, ctx)
        assertEquals(GestureSessionState.ACTIVATED, manager.sessionState.value)

        manager.processCommandGesture(GestureType.POINT, 0.9f, ctx)
        assertEquals(GestureSessionState.ACTIVATED, manager.sessionState.value)

        val earlySend = manager.processCommandGesture(GestureType.THREE_GUN, 0.9f, ctx)
        assertFalse(earlySend is GestureActionEffect.DispatchTransferOffer)
        assertEquals(GestureSessionState.ACTIVATED, manager.sessionState.value)

        // Repeated LIKE stays ACTIVATED without resetting
        manager.processCommandGesture(GestureType.FIST, 0.9f, ctx)
        assertEquals(GestureSessionState.CONTENT_HELD, manager.sessionState.value)
        manager.processCommandGesture(GestureType.LIKE, 0.9f, ctx)
        assertEquals(GestureSessionState.CONTENT_HELD, manager.sessionState.value)
    }

    @Test
    fun `phase8_fist without staged content does not advance`() {
        val manager = DefaultGestureRecognitionManager()
        manager.processCommandGesture(GestureType.LIKE, 0.95f, GestureSessionContext())
        val effect = manager.processCommandGesture(
            GestureType.FIST,
            0.95f,
            GestureSessionContext(stagedItems = emptyList())
        )
        assertTrue(effect is GestureActionEffect.None && effect.isError)
        assertEquals(GestureSessionState.ACTIVATED, manager.sessionState.value)
        assertFalse(manager.isContentGrabbed.value)
        assertTrue(manager.sessionFeedback.value?.contains("No content staged", ignoreCase = true) == true)
    }

    @Test
    fun `phase8_point with zero discovered peers fails safely`() {
        val manager = DefaultGestureRecognitionManager()
        val ctx = GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = emptyList())
        manager.processCommandGesture(GestureType.LIKE, 0.95f, ctx)
        manager.processCommandGesture(GestureType.FIST, 0.95f, ctx)
        val effect = manager.processCommandGesture(GestureType.POINT, 0.95f, ctx)
        assertTrue(effect is GestureActionEffect.None && effect.isError)
        assertEquals(GestureSessionState.CONTENT_HELD, manager.sessionState.value)
        assertTrue(manager.sessionFeedback.value?.contains("No nearby devices", ignoreCase = true) == true)
    }

    @Test
    fun `phase8_point cycles sequentially through three real peer cards`() {
        val manager = DefaultGestureRecognitionManager()
        val peers = listOf(peerA, peerB, peerC)
        manager.processCommandGesture(GestureType.LIKE, 0.95f, GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = peers))
        manager.processCommandGesture(GestureType.FIST, 0.95f, GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = peers))

        val e1 = manager.processCommandGesture(GestureType.POINT, 0.92f, GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = peers, selectedDevice = null))
        assertEquals(peerA, (e1 as GestureActionEffect.SelectPeerDevice).device)

        val e2 = manager.processCommandGesture(GestureType.POINT, 0.92f, GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = peers, selectedDevice = peerA))
        assertEquals(peerB, (e2 as GestureActionEffect.SelectPeerDevice).device)

        val e3 = manager.processCommandGesture(GestureType.POINT, 0.92f, GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = peers, selectedDevice = peerB))
        assertEquals(peerC, (e3 as GestureActionEffect.SelectPeerDevice).device)

        val e4 = manager.processCommandGesture(GestureType.POINT, 0.92f, GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = peers, selectedDevice = peerC))
        assertEquals(peerA, (e4 as GestureActionEffect.SelectPeerDevice).device)
    }

    @Test
    fun `phase8_three_gun without Nearby connection is blocked even when device discovered`() {
        val manager = DefaultGestureRecognitionManager()
        val ctxNoConn = GestureSessionContext(
            stagedItems = listOf(testItem),
            discoveredDevices = listOf(peerA),
            selectedDevice = peerA,
            connectedDevice = null
        )
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)
        manager.processCommandGesture(GestureType.LIKE, 0.95f, ctxNoConn)
        manager.processCommandGesture(GestureType.FIST, 0.95f, ctxNoConn)
        manager.processCommandGesture(GestureType.POINT, 0.95f, ctxNoConn)
        assertEquals(GestureSessionState.TARGET_SELECTED, manager.sessionState.value)

        val sendAttempt = manager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctxNoConn)
        assertFalse(sendAttempt is GestureActionEffect.DispatchTransferOffer)
        assertEquals(GestureSessionState.TARGET_SELECTED, manager.sessionState.value)
        assertTrue(manager.sessionFeedback.value?.contains("not connected", ignoreCase = true) == true)
    }

    @Test
    fun `phase8_receiver rejection transitions state machine cleanly to CANCELLED`() {
        val manager = DefaultGestureRecognitionManager()
        manager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)
        val ctx = GestureSessionContext(stagedItems = listOf(testItem), discoveredDevices = listOf(peerA), selectedDevice = peerA, connectedDevice = peerA)

        manager.processCommandGesture(GestureType.LIKE, 0.95f, ctx)
        manager.processCommandGesture(GestureType.FIST, 0.95f, ctx)
        manager.processCommandGesture(GestureType.POINT, 0.95f, ctx)
        manager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctx)
        assertEquals(GestureSessionState.TRANSFER_OFFERED, manager.sessionState.value)

        val rejectedTransfer = ActiveTransfer(
            id = "tx-reject",
            senderName = "Redmi 1",
            receiverName = peerA.name,
            items = listOf(testItem),
            totalBytes = 2048L,
            status = TransferStatus.CANCELLED,
            direction = TransferDirection.SEND,
            errorMessage = "Declined by receiver"
        )
        manager.bindActiveTransferId("tx-reject")
        manager.syncWithSystemState(ctx.copy(activeTransfer = rejectedTransfer))
        assertEquals(GestureSessionState.CANCELLED, manager.sessionState.value)
        assertEquals("Declined by receiver", manager.sessionFeedback.value)
    }

    @Test
    fun `phase8_stop works during every active session state and returns CancelActiveWorkflow`() {
        val activeStates = listOf(
            GestureSessionState.ACTIVATED,
            GestureSessionState.CONTENT_HELD,
            GestureSessionState.TARGET_SELECTED,
            GestureSessionState.CONNECTION_PENDING,
            GestureSessionState.CONNECTED,
            GestureSessionState.TRANSFER_OFFERED,
            GestureSessionState.WAITING_FOR_RECEIVER,
            GestureSessionState.TRANSFERRING,
            GestureSessionState.VERIFYING
        )

        for (state in activeStates) {
            val manager = DefaultGestureRecognitionManager()
            val ctx = GestureSessionContext(
                stagedItems = listOf(testItem),
                discoveredDevices = listOf(peerA),
                selectedDevice = peerA,
                connectedDevice = peerA
            )
            // Drive manager up to target state deterministically
            manager.processCommandGesture(GestureType.LIKE, 0.95f, ctx)
            if (state.ordinal >= GestureSessionState.CONTENT_HELD.ordinal) {
                manager.processCommandGesture(GestureType.FIST, 0.95f, ctx)
            }
            if (state == GestureSessionState.TARGET_SELECTED) {
                manager.processCommandGesture(
                    GestureType.POINT,
                    0.95f,
                    ctx.copy(connectedDevice = null)
                )
            } else if (state == GestureSessionState.CONNECTION_PENDING) {
                manager.processCommandGesture(
                    GestureType.POINT,
                    0.95f,
                    ctx.copy(
                        connectedDevice = null,
                        pendingSession = ConnectionSession(peerA.endpointId, peerA.name, "111222", false, SessionStatus.INITIATED)
                    )
                )
            } else if (state.ordinal >= GestureSessionState.CONNECTED.ordinal) {
                manager.processCommandGesture(GestureType.POINT, 0.95f, ctx)
            }
            if (state.ordinal >= GestureSessionState.TRANSFER_OFFERED.ordinal) {
                manager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctx)
            }
            val txBase = ActiveTransfer(id = "tx-s", senderName = "S", receiverName = "R", items = listOf(testItem), totalBytes = 100L)
            when (state) {
                GestureSessionState.WAITING_FOR_RECEIVER ->
                    manager.syncWithSystemState(ctx.copy(activeTransfer = txBase.copy(status = TransferStatus.AWAITING_ACCEPTANCE)))
                GestureSessionState.TRANSFERRING ->
                    manager.syncWithSystemState(ctx.copy(activeTransfer = txBase.copy(status = TransferStatus.TRANSFERRING)))
                GestureSessionState.VERIFYING ->
                    manager.syncWithSystemState(ctx.copy(activeTransfer = txBase.copy(status = TransferStatus.VERIFYING)))
                else -> Unit
            }

            assertEquals("Failed reaching active state $state", state, manager.sessionState.value)
            val stopEffect = manager.processCommandGesture(GestureType.STOP, 0.95f, ctx)
            assertTrue("STOP in state $state must return CancelActiveWorkflow", stopEffect is GestureActionEffect.CancelActiveWorkflow)
            assertEquals("STOP in state $state must reach CANCELLED", GestureSessionState.CANCELLED, manager.sessionState.value)
            assertFalse(manager.isContentGrabbed.value)
        }
    }

    @Test
    fun `phase8_temporal stabilizer enforces 5 consecutive frames hold duration cooldown and neutral reset`() {
        val stabilizer = TemporalGestureStabilizer(
            TemporalGestureConfig(
                windowSize = 5,
                requiredConsecutiveFrames = 5,
                confidenceThreshold = 0.80f,
                holdDurationMs = 200L,
                commandCooldownMs = 1000L,
                requireNeutralReset = true
            )
        )

        fun acceptedLike() = GestureResult(
            gesture = GestureType.LIKE,
            confidence = 0.91f,
            predictedClass = "like",
            isHandDetected = true,
            isAccepted = true
        )

        // Frames 1..4 (< 5 frames) must NOT trigger
        assertNull(stabilizer.process(acceptedLike(), 1000L).triggeredGesture)
        assertNull(stabilizer.process(acceptedLike(), 1060L).triggeredGesture)
        assertNull(stabilizer.process(acceptedLike(), 1120L).triggeredGesture)
        assertNull(stabilizer.process(acceptedLike(), 1180L).triggeredGesture)

        // Frame 5 at 240ms (>= 5 frames & >= 200ms hold) MUST trigger once
        val triggered = stabilizer.process(acceptedLike(), 1240L)
        assertEquals(GestureType.LIKE, triggered.triggeredGesture)

        // Repeatedly held LIKE for 10 more frames even AFTER cooldown must NOT re-trigger without neutral reset
        for (i in 1..10) {
            val held = stabilizer.process(acceptedLike(), 1240L + i * 250L)
            assertNull("Held gesture must remain locked until neutral reset", held.triggeredGesture)
            assertTrue(held.isHeldLocked)
        }

        // Low-confidence frame (< 0.80) or OTHER triggers neutral reset
        val lowConfLike = GestureResult(
            gesture = GestureType.OTHER,
            confidence = 0.75f,
            predictedClass = "like",
            isHandDetected = true,
            isAccepted = false
        )
        val afterNeutral = stabilizer.process(lowConfLike, 4000L)
        assertNull(afterNeutral.triggeredGesture)
        assertNull(afterNeutral.awaitingNeutralResetFor)

        // OTHER class also ignored safely
        val otherRes = stabilizer.process(
            GestureResult(gesture = GestureType.OTHER, confidence = 0.99f, predictedClass = "OTHER", isHandDetected = true, isAccepted = false),
            4050L
        )
        assertNull(otherRes.triggeredGesture)
        assertEquals(GestureType.OTHER, otherRes.stableGesture)

        // After neutral reset and cooldown, 5 new consistent frames >= 200ms trigger again cleanly
        assertNull(stabilizer.process(acceptedLike(), 4100L).triggeredGesture)
        assertNull(stabilizer.process(acceptedLike(), 4160L).triggeredGesture)
        assertNull(stabilizer.process(acceptedLike(), 4220L).triggeredGesture)
        assertNull(stabilizer.process(acceptedLike(), 4280L).triggeredGesture)
        val secondTrigger = stabilizer.process(acceptedLike(), 4340L)
        assertEquals(GestureType.LIKE, secondTrigger.triggeredGesture)
    }

    @Test
    fun `phase8_duplicate transfer prevention debug dry run lock and gesture transfer mode`() {
        val dryRunManager = DefaultGestureRecognitionManager()
        assertEquals(GestureOperationMode.DEBUG_DRY_RUN, dryRunManager.operationMode.value)
        val ctx = GestureSessionContext(
            stagedItems = listOf(testItem),
            discoveredDevices = listOf(peerA),
            selectedDevice = peerA,
            connectedDevice = peerA
        )

        dryRunManager.processCommandGesture(GestureType.LIKE, 0.95f, ctx)
        dryRunManager.processCommandGesture(GestureType.FIST, 0.95f, ctx)
        dryRunManager.processCommandGesture(GestureType.POINT, 0.95f, ctx)
        val dryEffect = dryRunManager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctx)
        assertTrue(dryEffect is GestureActionEffect.DispatchTransferOffer)
        assertTrue("Default mode MUST enforce Dry Run transfer lock", (dryEffect as GestureActionEffect.DispatchTransferOffer).isDryRun)

        // Duplicate THREE_GUN in same session MUST be blocked
        val duplicateAttempt = dryRunManager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctx)
        assertTrue(duplicateAttempt is GestureActionEffect.None && duplicateAttempt.isError)

        // Switch to GESTURE_TRANSFER explicitly -> executes real transfer offer & awaits receiver consent
        val realManager = DefaultGestureRecognitionManager()
        realManager.setOperationMode(GestureOperationMode.GESTURE_TRANSFER)
        realManager.processCommandGesture(GestureType.LIKE, 0.95f, ctx)
        realManager.processCommandGesture(GestureType.FIST, 0.95f, ctx)
        realManager.processCommandGesture(GestureType.POINT, 0.95f, ctx)
        val realEffect = realManager.processCommandGesture(GestureType.THREE_GUN, 0.95f, ctx)
        assertTrue(realEffect is GestureActionEffect.DispatchTransferOffer && !realEffect.isDryRun)
        // Crucial: THREE_GUN alone NEVER sets COMPLETED; awaits actual receiver consent & verification
        assertEquals(GestureSessionState.TRANSFER_OFFERED, realManager.sessionState.value)
    }

    private fun buildCanonicalOpenPalmFeatures(imageWidth: Int, imageHeight: Int): FloatArray {
        val pts = listOf(
            NormalizedLandmarkPoint(0.50f, 0.80f, 0.00f),
            NormalizedLandmarkPoint(0.43f, 0.74f, -0.02f),
            NormalizedLandmarkPoint(0.38f, 0.65f, -0.03f),
            NormalizedLandmarkPoint(0.34f, 0.56f, -0.04f),
            NormalizedLandmarkPoint(0.30f, 0.48f, -0.05f),
            NormalizedLandmarkPoint(0.44f, 0.55f, -0.02f),
            NormalizedLandmarkPoint(0.43f, 0.43f, -0.03f),
            NormalizedLandmarkPoint(0.42f, 0.34f, -0.04f),
            NormalizedLandmarkPoint(0.42f, 0.26f, -0.05f),
            NormalizedLandmarkPoint(0.50f, 0.53f, -0.02f),
            NormalizedLandmarkPoint(0.50f, 0.40f, -0.03f),
            NormalizedLandmarkPoint(0.50f, 0.30f, -0.04f),
            NormalizedLandmarkPoint(0.50f, 0.22f, -0.05f),
            NormalizedLandmarkPoint(0.56f, 0.55f, -0.02f),
            NormalizedLandmarkPoint(0.57f, 0.43f, -0.03f),
            NormalizedLandmarkPoint(0.57f, 0.34f, -0.04f),
            NormalizedLandmarkPoint(0.58f, 0.26f, -0.05f),
            NormalizedLandmarkPoint(0.62f, 0.59f, -0.02f),
            NormalizedLandmarkPoint(0.64f, 0.50f, -0.03f),
            NormalizedLandmarkPoint(0.66f, 0.42f, -0.04f),
            NormalizedLandmarkPoint(0.67f, 0.35f, -0.05f)
        )
        return MediaPipeHandTracker.normalizeImageCoordinatesToFeatureVector(pts, imageWidth, imageHeight)
    }
}
