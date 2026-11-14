package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.manager.DefaultContentImportManager
import com.example.manager.DefaultGestureRecognitionManager
import com.example.manager.DefaultTransferStateManager
import com.example.manager.MediaPipeHandTracker
import com.example.manager.NormalizedLandmarkPoint
import com.example.model.ContentType
import com.example.model.GestureSessionState
import com.example.model.GestureType
import com.example.util.HashUtil
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
                0 -> NormalizedLandmarkPoint(x = 0.5f, y = 0.5f, z = 0.0f) // Wrist origin
                9 -> NormalizedLandmarkPoint(x = 0.5f, y = 0.3f, z = 0.0f) // Middle MCP at distance 0.2 above wrist
                else -> NormalizedLandmarkPoint(x = 0.5f + idx * 0.01f, y = 0.5f - idx * 0.01f, z = 0.02f)
            }
        }

        val vec = MediaPipeHandTracker.normalizeLandmarksToFeatureVector(landmarks)
        assertEquals(63, vec.size)

        // Wrist (index 0) must be centered at (0, 0, 0)
        assertEquals(0f, vec[0], 1e-4f)
        assertEquals(0f, vec[1], 1e-4f)
        assertEquals(0f, vec[2], 1e-4f)

        // Middle MCP (index 9) divided by scale 0.2 must be (0, -1, 0)
        assertEquals(0f, vec[9 * 3], 1e-4f)
        assertEquals(-1f, vec[9 * 3 + 1], 1e-4f)
        assertEquals(0f, vec[9 * 3 + 2], 1e-4f)
    }

    @Test
    fun `test gesture state machine transitions`() {
        val manager = DefaultGestureRecognitionManager()
        assertEquals(GestureSessionState.IDLE, manager.sessionState.value)

        manager.simulateGestureForTesting(GestureType.LIKE)
        assertEquals(GestureSessionState.ACTIVATED, manager.sessionState.value)

        manager.simulateGestureForTesting(GestureType.FIST)
        assertEquals(GestureSessionState.CONTENT_HELD, manager.sessionState.value)

        manager.simulateGestureForTesting(GestureType.POINT)
        assertEquals(GestureSessionState.TARGET_LOCKED, manager.sessionState.value)

        manager.simulateGestureForTesting(GestureType.THREE_GUN)
        assertEquals(GestureSessionState.TRANSFERRING, manager.sessionState.value)

        manager.simulateGestureForTesting(GestureType.STOP)
        assertEquals(GestureSessionState.CANCELLED, manager.sessionState.value)
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
        val device = com.example.model.DeviceItem(
            endpointId = "XYZ123",
            name = "Redmi Note 12",
            connectionState = com.example.model.DeviceConnectionState.DISCOVERED
        )
        assertEquals("XYZ123", device.endpointId)
        assertEquals("XYZ123", device.id)
        assertEquals("Redmi Note 12", device.name)

        val session = com.example.model.ConnectionSession(
            endpointId = "XYZ123",
            endpointName = "Redmi Note 12",
            authDigits = "782914",
            isIncoming = true,
            status = com.example.model.SessionStatus.INITIATED
        )
        assertEquals("782914", session.authDigits)
        assertTrue(session.isIncoming)
    }
}
