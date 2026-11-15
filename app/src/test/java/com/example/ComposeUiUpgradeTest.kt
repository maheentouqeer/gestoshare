package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.AppSettingsState
import com.example.model.ActiveTransfer
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DeviceType
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticCodes
import com.example.model.DiagnosticErrorEvent
import com.example.model.ErrorSeverity
import com.example.model.TransferDirection
import com.example.model.TransferPipelineStage
import com.example.model.TransferStatus
import com.example.ui.components.GlassCameraRecipientOverlay
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.theme.GestoGlassBackground
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeUiUpgradeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `HomeScreen renders GestoShare branding in Light and Dark glass themes`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                GestoGlassBackground {
                    HomeScreen(
                        deviceName = "Redmi Test",
                        stagedItems = emptyList(),
                        discoveredDevices = emptyList(),
                        connectedDevices = emptyList(),
                        errorCount = 2,
                        isAdvertising = false,
                        recentTransfers = emptyList(),
                        onNavigate = {},
                        onToggleAdvertising = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("GestoShare", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_diagnostics_alert_card").assertExists()
    }

    @Test
    fun `SettingsScreen dark-light theme toggle triggers theme switch callback`() {
        var toggledDark: Boolean? = null
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                SettingsScreen(
                    currentDeviceName = "Infinix 1",
                    settingsState = AppSettingsState(deviceName = "Infinix 1", isDarkTheme = true),
                    errorCount = 1,
                    onUpdateDeviceName = {},
                    onToggleDarkTheme = { toggledDark = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("theme_mode_switch").performClick()
        assertEquals(false, toggledDark)
    }

    @Test
    fun `GlassCameraRecipientOverlay renders connected peers and responds to selection & multiselect`() {
        val p1 = DeviceItem("ep-1", "Redmi 1", DeviceType.PHONE, DeviceConnectionState.CONNECTED)
        val p2 = DeviceItem("ep-2", "Redmi 2", DeviceType.PHONE, DeviceConnectionState.CONNECTED)
        var selected: DeviceItem? = null
        var toggledMulti = false

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                GlassCameraRecipientOverlay(
                    connectedDevices = listOf(p1, p2),
                    discoveredDevices = emptyList(),
                    selectedDevice = p1,
                    isMultiSelectMode = false,
                    multiSelectRecipientIds = emptySet(),
                    onSelectDevice = { selected = it },
                    onToggleMultiRecipient = {},
                    onRequestConnection = {},
                    onToggleMultiSelectMode = { toggledMulti = true },
                    onManageDevicesClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("camera_nearby_recipients_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("overlay_peer_chip_ep-2").performClick()
        assertEquals("ep-2", selected?.endpointId)

        composeTestRule.onNodeWithTag("camera_overlay_multiselect_toggle").performClick()
        assertTrue(toggledMulti)
    }

    @Test
    fun `DiagnosticsScreen renders error card and dispatches copy error report callback`() {
        var copiedId: String? = null
        val sampleError = DiagnosticErrorEvent(
            id = "ev-42",
            code = DiagnosticCodes.GS_FILE_002,
            title = "Scoped Storage Access Denied (EACCES)",
            category = DiagnosticCategory.FILE_IO,
            severity = ErrorSeverity.ERROR,
            summary = "Fallback content URI required instead of direct POSIX file path.",
            technicalMessage = "SecurityException: open failed EACCES",
            stackTrace = "at IncomingPayloadStorage.kt:152",
            sourceComponent = "IncomingPayloadStorage",
            operation = "copyVerifyAndSave",
            deviceInfoJson = "{}",
            stateSnapshotJson = "{}",
            suggestedAction = "Use ContentResolver.openInputStream(uri)"
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                DiagnosticsScreen(
                    errorEvents = listOf(sampleError),
                    operationalLogs = emptyList(),
                    modelSummary = "HaGRIDv2 ONNX loaded (6 classes)",
                    nearbySummary = "P2P_CLUSTER active",
                    onCopyErrorReport = { copiedId = it.id },
                    onExportDiagnosticsTxt = {},
                    onExportDiagnosticsJson = {},
                    onDeleteErrorEvent = {},
                    onClearAllDiagnostics = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("diagnostics_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("GS-FILE-002").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_error_${DiagnosticCodes.GS_FILE_002}").performClick()
        assertEquals("ev-42", copiedId)
    }

    @Test
    fun `TransferScreen displays pipeline stage telemetry and diagnostic code badge on failure`() {
        val failedTransfer = ActiveTransfer(
            id = "tx-ui-1",
            items = listOf(
                ContentItem(
                    id = "c-1",
                    name = "notes.pdf",
                    type = ContentType.DOCUMENT_PDF,
                    mimeType = "application/pdf",
                    sizeBytes = 12345L
                )
            ),
            senderName = "Redmi 1",
            receiverName = "Redmi 2",
            recipientEndpointId = "ep-1",
            direction = TransferDirection.RECEIVE,
            status = TransferStatus.FAILED,
            pipelineStage = TransferPipelineStage.VERIFYING_INTEGRITY,
            progress = 1.0f,
            bytesTransferred = 12345L,
            totalBytes = 12345L,
            transportCompleted = true,
            fileCopyCompleted = true,
            lastErrorCode = DiagnosticCodes.GS_FILE_003,
            errorMessage = "SHA-256 verification mismatch"
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                TransferScreen(
                    activeTransfer = failedTransfer,
                    onCancelTransfer = {},
                    onRetryTransfer = {},
                    onOpenReceivedFile = { _, _ -> },
                    onNavigate = {},
                    formatSize = { "$it B" }
                )
            }
        }

        composeTestRule.onNodeWithTag("transfer_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("GS-FILE-003").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transfer_screen")
            .performScrollToNode(hasTestTag("transfer_pipeline_stages_card"))
        composeTestRule.onNodeWithTag("transfer_pipeline_stages_card").assertExists()
    }
}
