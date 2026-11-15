package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.example.data.AirShareDatabase
import com.example.data.DiagnosticsRepository
import com.example.manager.IncomingPayloadSource
import com.example.manager.IncomingPayloadStorage
import com.example.model.ContentType
import com.example.model.DiagnosticCodes
import com.example.model.TransferItemMetadata
import com.example.model.TransferPipelineStage
import com.example.model.TransferVerificationException
import com.example.util.HashUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingPayloadStorageTest {

    private lateinit var context: Context
    private lateinit var db: AirShareDatabase
    private lateinit var diagnostics: DiagnosticsRepository
    private lateinit var storage: IncomingPayloadStorage

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AirShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        diagnostics = DiagnosticsRepository(db.diagnosticDao())
        storage = IncomingPayloadStorage(context, diagnostics)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "received").deleteRecursively()
        File(context.cacheDir, "incoming_verify").deleteRecursively()
        db.close()
    }

    @Test
    fun `copies verified payload via ContentResolver URI stream without POSIX path and runs MediaStore stage`() = runTest {
        val sampleBytes = "GestoShare-ScopedStorage-Payload-Verification-Content-2026".toByteArray()
        val expectedSha = HashUtil.calculateSha256(ByteArrayInputStream(sampleBytes))

        val nearbyContentUri = Uri.parse("content://com.google.android.gms.nearby.mock/payload/42")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(nearbyContentUri, ByteArrayInputStream(sampleBytes))

        val stages = mutableListOf<TransferPipelineStage>()
        val result = storage.copyVerifyAndSave(
            transferId = "tx-uri-ok",
            meta = TransferItemMetadata(
                itemIndex = 0,
                name = "hello world.txt",
                mimeType = "text/plain",
                sizeBytes = sampleBytes.size.toLong(),
                contentType = ContentType.TEXT,
                sha256 = expectedSha
            ),
            source = IncomingPayloadSource.ContentUriSource(nearbyContentUri),
            onStageChange = { stage, _ -> stages.add(stage) },
            customMediaStoreExporter = { privateFile, displayName, _ ->
                assertTrue(privateFile.exists())
                assertEquals(sampleBytes.size.toLong(), privateFile.length())
                assertEquals("hello world.txt", displayName)
                Uri.parse("content://media/external/downloads/9991") to null
            }
        )

        assertTrue(result.permanentFile.exists())
        assertEquals(sampleBytes.toList(), result.permanentFile.readBytes().toList())
        assertEquals(expectedSha, result.sha256Hash)
        assertEquals(sampleBytes.size.toLong(), result.bytesCopied)
        assertTrue(result.mediaStoreExported)
        assertEquals(
            listOf(
                TransferPipelineStage.COPYING_TO_PRIVATE_STORAGE,
                TransferPipelineStage.VERIFYING_INTEGRITY,
                TransferPipelineStage.EXPORTING_TO_DOWNLOADS
            ),
            stages
        )
    }

    @Test
    fun `corrupted payload fails SHA-256 verification with GS-FILE-003 and discards partial file`() {
        val sampleBytes = "Corrupted-Bytes-On-Wire".toByteArray()
        val wrongSha = "0".repeat(64)
        val sourceFile = File(context.cacheDir, "nearby_corrupt_sample.bin").apply {
            writeBytes(sampleBytes)
        }
        val contentUri = Uri.fromFile(sourceFile)
        var exportInvoked = false

        val thrown = try {
            storage.copyVerifyAndSave(
                transferId = "tx-sha-fail",
                meta = TransferItemMetadata(
                    itemIndex = 0,
                    name = "corrupt.bin",
                    mimeType = "application/octet-stream",
                    sizeBytes = sampleBytes.size.toLong(),
                    contentType = ContentType.GENERIC_FILE,
                    sha256 = wrongSha
                ),
                source = IncomingPayloadSource.ContentUriSource(contentUri),
                customMediaStoreExporter = { _, _, _ ->
                    exportInvoked = true
                    null to null
                }
            )
            null
        } catch (e: TransferVerificationException) {
            e
        }

        assertNotNull(thrown)
        assertEquals(DiagnosticCodes.GS_FILE_003, thrown!!.code)
        assertFalse("Corrupted payload must NEVER be exported to MediaStore", exportInvoked)
        assertEquals(0, File(context.filesDir, "received").listFiles()?.size ?: 0)
        assertEquals(0, File(context.cacheDir, "incoming_verify").listFiles()?.size ?: 0)
        sourceFile.delete()
    }

    @Test
    fun `byte length mismatch throws GS-FILE-004 before trusting file`() = runTest {
        val sampleBytes = "12345".toByteArray()
        val sourceFile = File(context.cacheDir, "nearby_short.bin").apply { writeBytes(sampleBytes) }
        val sha = HashUtil.calculateSha256(ByteArrayInputStream(sampleBytes))

        val thrown = try {
            storage.copyVerifyAndSave(
                transferId = "tx-len-fail",
                meta = TransferItemMetadata(
                    itemIndex = 0,
                    name = "truncated.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 5000L,
                    contentType = ContentType.DOCUMENT_PDF,
                    sha256 = sha
                ),
                source = IncomingPayloadSource.ContentUriSource(Uri.fromFile(sourceFile))
            )
            null
        } catch (e: TransferVerificationException) {
            e
        }

        assertNotNull(thrown)
        assertEquals(DiagnosticCodes.GS_FILE_004, thrown!!.code)
        assertEquals(0, File(context.filesDir, "received").listFiles()?.size ?: 0)
        sourceFile.delete()
    }

    @Test
    fun `content URI SecurityException permission failure throws GS-FILE-002`() {
        Robolectric.buildContentProvider(FailingSecurityContentProvider::class.java)
            .create("com.example.test.eacces.provider")

        val blockedUri = Uri.parse("content://com.example.test.eacces.provider/secret.jpg")
        val thrown = try {
            storage.copyVerifyAndSave(
                transferId = "tx-perm-err",
                meta = TransferItemMetadata(
                    itemIndex = 0,
                    name = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 100L,
                    contentType = ContentType.IMAGE,
                    sha256 = ""
                ),
                source = IncomingPayloadSource.ContentUriSource(blockedUri)
            )
            null
        } catch (e: TransferVerificationException) {
            e
        }

        assertNotNull(thrown)
        assertEquals(DiagnosticCodes.GS_FILE_002, thrown!!.code)
    }

    @Test
    fun `MediaStore export failure preserves verified private file and logs GS-FILE-006`() = runTest {
        val testScope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val localDiagnostics = DiagnosticsRepository(db.diagnosticDao(), ioScope = testScope)
        val localStorage = IncomingPayloadStorage(context, localDiagnostics)

        val payloadBytes = "Verified-Private-Fallback-File".toByteArray()
        val sha = HashUtil.calculateSha256(ByteArrayInputStream(payloadBytes))
        val sourceFile = File(context.cacheDir, "nearby_mediastore_fail.pdf").apply {
            writeBytes(payloadBytes)
        }

        val result = localStorage.copyVerifyAndSave(
            transferId = "tx-export-warn",
            meta = TransferItemMetadata(
                itemIndex = 0,
                name = "../../unsafe:path/report?.pdf",
                mimeType = "application/pdf",
                sizeBytes = payloadBytes.size.toLong(),
                contentType = ContentType.DOCUMENT_PDF,
                sha256 = sha
            ),
            source = IncomingPayloadSource.AppPrivateFileSource(sourceFile),
            customMediaStoreExporter = { _, _, _ ->
                null to "Simulated MediaStore insert security error"
            }
        )

        assertTrue("Verified private file must remain intact", result.permanentFile.exists())
        assertFalse("Filename path traversal must be sanitized", result.permanentFile.name.contains(".."))
        assertFalse("Filename question marks sanitized", result.permanentFile.name.contains("?"))
        assertFalse(result.mediaStoreExported)
        assertTrue(result.mediaStoreNote?.contains("Simulated MediaStore") == true)

        val events = localDiagnostics.errorEvents.first()
        assertTrue(events.any { it.code == DiagnosticCodes.GS_FILE_006 })
        sourceFile.delete()
    }

    class FailingSecurityContentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            throw SecurityException("open failed: EACCES (Permission denied)")
        }
        override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
