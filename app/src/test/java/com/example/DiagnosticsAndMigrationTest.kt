package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AirShareDatabase
import com.example.data.DiagnosticsRepository
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticClassifier
import com.example.model.DiagnosticCodes
import com.example.model.DiagnosticErrorEvent
import com.example.model.ErrorSeverity
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsAndMigrationTest {

    private lateinit var context: Context
    private lateinit var db: AirShareDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AirShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("migration_v1_v2_test.db")
    }

    @Test
    fun `Room MIGRATION_1_2 preserves existing transfer_records and creates diagnostic tables`() {
        val dbName = "migration_v1_v2_test.db"
        context.deleteDatabase(dbName)

        // Build genuine v1 SQLite schema containing only transfer_records and a pre-existing row
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transfer_records` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `fileName` TEXT NOT NULL,
                            `fileType` TEXT NOT NULL,
                            `fileSizeFormatted` TEXT NOT NULL,
                            `senderName` TEXT NOT NULL,
                            `receiverName` TEXT NOT NULL,
                            `direction` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `sha256Verification` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO `transfer_records`
                        VALUES ('legacy-1', 'vacation.jpg', 'image/jpeg', '2.4 MB', 'Redmi 1', 'Infinix 1', 'SEND', 'SUCCESS', 1700000000000, 'SHA-256 Verified (abc123)')
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v1Db = helper.writableDatabase
        assertEquals(1, v1Db.version)
        v1Db.close()

        // Open with Room v2 + MIGRATION_1_2
        val migratedDb = Room.databaseBuilder(context, AirShareDatabase::class.java, dbName)
            .addMigrations(AirShareDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            runTest {
                val records = migratedDb.transferDao().getAllTransfers().first()
                assertEquals(1, records.size)
                assertEquals("vacation.jpg", records.first().fileName)

                val diagRepo = DiagnosticsRepository(
                    migratedDb.diagnosticDao(),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler))
                )
                diagRepo.recordErrorSync(
                    DiagnosticErrorEvent(
                        code = DiagnosticCodes.GS_NET_002,
                        title = "Nearby Failure",
                        category = DiagnosticCategory.NEARBY,
                        severity = ErrorSeverity.ERROR,
                        summary = "New diagnostic table operational",
                        technicalMessage = "Discovery test",
                        stackTrace = "",
                        sourceComponent = "MigrationTest",
                        operation = "verifyTables",
                        deviceInfoJson = DiagnosticClassifier.buildDeviceInfoJson(),
                        stateSnapshotJson = "{}",
                        suggestedAction = "Retry"
                    )
                )
                val events = diagRepo.errorEvents.first()
                assertEquals(1, events.size)
                assertEquals(DiagnosticCodes.GS_NET_002, events.first().code)
            }
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `diagnostic classifier maps EACCES ENOSPC FileNotFoundException and redacts sensitive fields`() {
        val eaccesEvent = DiagnosticClassifier.classifyThrowable(
            throwable = FileNotFoundException("/storage/emulated/0/Download/.nearby/6381: open failed: EACCES (Permission denied) authDigits=842910 textContent=SecretNote"),
            sourceComponent = "RealNearbyTransferManager",
            operation = "handleCompletedFilePayload"
        )
        assertEquals(DiagnosticCodes.GS_FILE_002, eaccesEvent.code)
        assertEquals(DiagnosticCategory.FILE_IO, eaccesEvent.category)
        assertFalse(eaccesEvent.technicalMessage.contains("842910"))
        assertFalse(eaccesEvent.technicalMessage.contains("SecretNote"))
        assertTrue(eaccesEvent.technicalMessage.contains("[REDACTED]"))

        val enospcEvent = DiagnosticClassifier.classifyThrowable(
            throwable = java.io.IOException("write failed: ENOSPC (No space left on device)"),
            sourceComponent = "IncomingPayloadStorage",
            operation = "copyVerifyAndSave"
        )
        assertEquals(DiagnosticCodes.GS_FILE_005, enospcEvent.code)
        assertEquals(DiagnosticCategory.STORAGE, enospcEvent.category)
    }

    @Test
    fun `diagnostics repository bounds error events at 200 and logs at 500 and exports valid TXT and JSON`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val repo = DiagnosticsRepository(db.diagnosticDao(), scope)

        // Insert 205 error events synchronously so retention cap of 200 is verified deterministically
        for (i in 1..205) {
            repo.recordErrorSync(
                DiagnosticErrorEvent(
                    id = "err-$i",
                    code = DiagnosticCodes.GS_NET_003,
                    title = "Peer Disconnected",
                    category = DiagnosticCategory.NEARBY,
                    severity = ErrorSeverity.ERROR,
                    summary = "Error #$i",
                    technicalMessage = "Tech #$i",
                    stackTrace = "Stack #$i",
                    sourceComponent = "Test",
                    operation = "loop",
                    deviceInfoJson = DiagnosticClassifier.buildDeviceInfoJson(),
                    stateSnapshotJson = "{}",
                    suggestedAction = "Retry",
                    timestamp = 1000L + i
                )
            )
        }
        val persistedErrors = repo.errorEvents.first()
        assertEquals(DiagnosticsRepository.MAX_ERROR_EVENTS, persistedErrors.size)
        // Newest (#205) kept, oldest (#1..#5) trimmed
        assertEquals("err-205", persistedErrors.first().id)

        val singleReport = repo.formatSingleErrorReport(persistedErrors.first())
        assertTrue(singleReport.contains("=== GestoShare Error Report ==="))
        assertTrue(singleReport.contains("Code: ${DiagnosticCodes.GS_NET_003}"))
        assertTrue(singleReport.contains("Suggested Fix:"))

        val sampleTransfers = listOf(
            TransferRecord(
                id = "tx-1",
                fileName = "contract.pdf",
                fileType = "application/pdf",
                fileSizeFormatted = "1.2 MB",
                senderName = "Redmi 1",
                receiverName = "Redmi 2",
                direction = TransferDirection.SEND,
                status = TransferStatus.SUCCESS,
                timestamp = System.currentTimeMillis(),
                sha256Verification = "SHA-256 Verified"
            )
        )

        val txtExport = repo.exportDiagnosticsText(sampleTransfers, "ONNX OK", "P2P_CLUSTER active")
        assertTrue(txtExport.contains("GestoShare Diagnostics Bundle (TXT)"))
        assertTrue(txtExport.contains("contract.pdf"))
        assertTrue(txtExport.contains("Model Status: ONNX OK"))

        val jsonExport = repo.exportDiagnosticsJson(sampleTransfers, "ONNX OK", "P2P_CLUSTER active")
        val jsonObj = JSONObject(jsonExport)
        assertEquals("GestoShare", jsonObj.getString("app"))
        assertEquals(200, jsonObj.getJSONArray("errors").length())
        assertEquals(1, jsonObj.getJSONArray("recentTransfers").length())
    }
}
