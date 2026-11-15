package com.example

import android.content.ContentProvider
import android.content.ContentResolver
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
import com.example.manager.RealNearbyTransferManager
import com.example.manager.SavedPayloadResult
import com.example.model.ContentItem
import com.example.model.ContentType
import com.example.model.DeviceConnectionState
import com.example.model.DeviceItem
import com.example.model.DeviceType
import com.example.model.DiagnosticCategory
import com.example.model.DiagnosticCodes
import com.example.model.TransferItemMetadata
import com.example.model.TransferPipelineStage
import com.example.model.TransferStatus
import com.example.model.TransferVerificationException
import com.example.util.HashUtil
import com.example.util.PayloadManifestTracker
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingPayloadStorageTest {

    private lateinit var context: Context
    private lateinit var db: AirShareDatabase
    private lateinit var diagJob: CompletableJob
    private lateinit var diagScope: CoroutineScope
    private lateinit var diagnostics: DiagnosticsRepository
    private lateinit var storage: IncomingPayloadStorage

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AirShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        diagJob = SupervisorJob()
        diagScope = CoroutineScope(diagJob + Dispatchers.IO)
        diagnostics = DiagnosticsRepository(db.diagnosticDao(), ioScope = diagScope)
        storage = IncomingPayloadStorage(context, diagnostics)
        TrackingContentProvider.reset()
        Robolectric.buildContentProvider(TrackingContentProvider::class.java)
            .create("com.example.test.tracking.provider")
    }

    @After
    fun tearDown() {
        runBlocking {
            diagJob.children.toList().forEach { it.join() }
            diagJob.cancelAndJoin()
        }
        File(context.filesDir, "received").deleteRecursively()
        File(context.cacheDir, "incoming_verify").deleteRecursively()
        File(context.cacheDir, "test_pfd").deleteRecursively()
        db.close()
    }

    private suspend fun awaitDiagnosticsWrites() {
        diagJob.children.toList().forEach { it.join() }
    }

    // -------------------------------------------------------------------------
    // Core Scoped-Storage & FileProvider Hardening Tests
    // -------------------------------------------------------------------------

    @Test
    fun `copies verified payload via ContentResolver URI stream without POSIX path and deletes source only after commit`() = runTest {
        val sampleBytes = "GestoShare-ScopedStorage-Payload-Verification-Content-2026".toByteArray()
        val expectedSha = HashUtil.calculateSha256(ByteArrayInputStream(sampleBytes))

        val payloadUri = Uri.parse("content://com.example.test.tracking.provider/payload/42")
        TrackingContentProvider.payloadBytesByUri[payloadUri.toString()] = sampleBytes

        val stages = mutableListOf<TransferPipelineStage>()
        var deletedBeforeCommit = false
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
            source = IncomingPayloadSource.ContentUriSource(payloadUri),
            onStageChange = { stage, _ ->
                stages.add(stage)
                if (stage == TransferPipelineStage.VERIFYING_INTEGRITY) {
                    if (TrackingContentProvider.deletedUris.contains(payloadUri.toString())) {
                        deletedBeforeCommit = true
                    }
                }
            },
            customMediaStoreExporter = { privateFile, displayName, _ ->
                assertTrue(privateFile.exists())
                assertEquals(sampleBytes.size.toLong(), privateFile.length())
                assertEquals("hello world.txt", displayName)
                Uri.parse("content://media/external/downloads/9991") to null
            }
        )

        assertFalse("Nearby source URI must NOT be deleted before verification and commit complete", deletedBeforeCommit)
        assertTrue("Nearby source URI must be deleted after commit completes", TrackingContentProvider.deletedUris.contains(payloadUri.toString()))
        assertTrue(result.permanentFile.exists())
        assertEquals(sampleBytes.toList(), result.permanentFile.readBytes().toList())
        assertEquals(expectedSha, result.sha256Hash)
        assertEquals(sampleBytes.size.toLong(), result.bytesCopied)
        assertTrue(result.mediaStoreExported)
        assertEquals(ContentResolver.SCHEME_CONTENT, result.shareableUri?.scheme)
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
    fun `resolvePayloadSourceOrThrow disallows raw asJavaFile fallback on Android 10 plus and prefers URI or PFD`() {
        val tempFile = File(context.cacheDir, "test_pfd/raw_sample.bin").apply {
            parentFile?.mkdirs()
            writeBytes("NearbyRawSample".toByteArray())
        }
        val nearbyRawPath = File("/storage/emulated/0/Download/.nearby/999111")

        // 1. When neither ContentUri nor PFD is available on Android 10+ (API 34), MUST throw GS-FILE-002
        val thrown = try {
            IncomingPayloadStorage.resolvePayloadComponentsOrThrow(
                payloadId = 77L,
                uri = null,
                pfd = null,
                javaFile = nearbyRawPath,
                sdkInt = 34
            )
            null
        } catch (e: TransferVerificationException) {
            e
        }
        assertNotNull("Expected GS-FILE-002 when only asJavaFile exists on API 34", thrown)
        assertEquals(DiagnosticCodes.GS_FILE_002, thrown!!.code)

        // 2. Android 9 (API 28) legacy fallback remains valid when file is readable
        val legacySource = IncomingPayloadStorage.resolvePayloadComponentsOrThrow(
            payloadId = 78L,
            uri = null,
            pfd = null,
            javaFile = tempFile,
            sdkInt = 28
        )
        assertTrue(legacySource is IncomingPayloadSource.AppPrivateFileSource)

        // 3. ContentUri takes strict priority over PFD and javaFile
        val uri = Uri.parse("content://com.google.android.gms.nearby.mock/payload/79")
        val uriSource = IncomingPayloadStorage.resolvePayloadComponentsOrThrow(
            payloadId = 79L,
            uri = uri,
            pfd = null,
            javaFile = tempFile,
            sdkInt = 34
        )
        assertTrue(uriSource is IncomingPayloadSource.ContentUriSource)

        // 4. Unsupported file:// URI without PFD on Android 10+ (API 34) MUST be rejected with GS-FILE-002
        val fileSchemeUri = Uri.parse("file:///storage/emulated/0/Download/.nearby/999111")
        val fileUriThrown = try {
            IncomingPayloadStorage.resolvePayloadComponentsOrThrow(
                payloadId = 80L,
                uri = fileSchemeUri,
                pfd = null,
                javaFile = null,
                sdkInt = 34
            )
            null
        } catch (e: TransferVerificationException) {
            e
        }
        assertNotNull("Expected GS-FILE-002 when only file:// URI exists on API 34", fileUriThrown)
        assertEquals(DiagnosticCodes.GS_FILE_002, fileUriThrown!!.code)

        // 5. Payload.fromFile(File) exposes a PFD on Android 10+ and skips file:// URI to resolve to PfdSource
        val filePayload = Payload.fromFile(tempFile)
        val pfdSource = IncomingPayloadStorage.resolvePayloadSourceOrThrow(filePayload, sdkInt = 34)
        assertTrue(pfdSource is IncomingPayloadSource.PfdSource)
        IncomingPayloadStorage.safeDeletePayload(context, filePayload)
        tempFile.delete()
    }

    @Test
    fun `accepts valid content URI via ContentResolver and rejects unsupported file URI source on Android 10 plus`() = runTest {
        val sampleBytes = "Scoped-Content-Uri-Stream-Test".toByteArray()
        val sha = HashUtil.calculateSha256(ByteArrayInputStream(sampleBytes))
        val validContentUri = Uri.parse("content://com.example.test.tracking.provider/payload/888")
        TrackingContentProvider.payloadBytesByUri[validContentUri.toString()] = sampleBytes

        val saved = storage.copyVerifyAndSave(
            transferId = "tx-valid-content-uri",
            meta = TransferItemMetadata(0, "valid.txt", "text/plain", sampleBytes.size.toLong(), ContentType.TEXT, sha),
            source = IncomingPayloadSource.ContentUriSource(validContentUri),
            customMediaStoreExporter = { _, _, _ -> Uri.parse("content://media/downloads/888") to null }
        )
        assertTrue(saved.permanentFile.exists())
        assertEquals(sampleBytes.toList(), saved.permanentFile.readBytes().toList())

        val rawNearbyFile = File(context.cacheDir, "test_pfd/blocked_file_uri.bin").apply {
            parentFile?.mkdirs()
            writeBytes(sampleBytes)
        }
        val rejected = try {
            storage.copyVerifyAndSave(
                transferId = "tx-reject-file-uri",
                meta = TransferItemMetadata(0, "blocked.bin", "application/octet-stream", sampleBytes.size.toLong(), ContentType.GENERIC_FILE, sha),
                source = IncomingPayloadSource.ContentUriSource(Uri.fromFile(rawNearbyFile))
            )
            null
        } catch (e: TransferVerificationException) {
            e
        } finally {
            rawNearbyFile.delete()
        }
        assertNotNull("file:// ContentUriSource must be rejected on API 34", rejected)
        assertEquals(DiagnosticCodes.GS_FILE_002, rejected!!.code)
    }

    @Test
    fun `existing verified file is never overwritten and FileProvider failure never returns file URI`() = runTest {
        val firstBytes = "First-Verified-Content-Keep-Intact".toByteArray()
        val firstSha = HashUtil.calculateSha256(ByteArrayInputStream(firstBytes))
        val uri1 = Uri.parse("content://com.example.test.tracking.provider/payload/10")
        TrackingContentProvider.payloadBytesByUri[uri1.toString()] = firstBytes

        val firstSaved = storage.copyVerifyAndSave(
            transferId = "tx-1",
            meta = TransferItemMetadata(0, "report.pdf", "application/pdf", firstBytes.size.toLong(), ContentType.DOCUMENT_PDF, firstSha),
            source = IncomingPayloadSource.ContentUriSource(uri1),
            customMediaStoreExporter = { _, _, _ -> Uri.parse("content://media/downloads/10") to null }
        )

        // Second file with same name and failing FileProvider + failing MediaStore export
        val secondBytes = "Second-Verified-Content-No-FileUri".toByteArray()
        val secondSha = HashUtil.calculateSha256(ByteArrayInputStream(secondBytes))
        val uri2 = Uri.parse("content://com.example.test.tracking.provider/payload/11")
        TrackingContentProvider.payloadBytesByUri[uri2.toString()] = secondBytes

        val secondSaved = storage.copyVerifyAndSave(
            transferId = "tx-2",
            meta = TransferItemMetadata(0, "report.pdf", "application/pdf", secondBytes.size.toLong(), ContentType.DOCUMENT_PDF, secondSha),
            source = IncomingPayloadSource.ContentUriSource(uri2),
            customMediaStoreExporter = { _, _, _ -> null to "Simulated MediaStore failure" },
            customFileProviderResolver = { throw IllegalArgumentException("Simulated FileProvider root failure") }
        )

        // Original verified file must be untouched and distinct
        assertTrue(firstSaved.permanentFile.exists())
        assertTrue(secondSaved.permanentFile.exists())
        assertFalse(firstSaved.permanentFile.absolutePath == secondSaved.permanentFile.absolutePath)
        assertEquals(firstBytes.toList(), firstSaved.permanentFile.readBytes().toList())
        assertEquals(secondBytes.toList(), secondSaved.permanentFile.readBytes().toList())

        // shareableUri must NEVER fall back to Uri.fromFile() (file://)
        assertNull("Raw file:// fallback must never be returned when FileProvider and MediaStore fail", secondSaved.shareableUri)

        awaitDiagnosticsWrites()
        val events = diagnostics.errorEvents.first()
        assertTrue(events.any { it.code == DiagnosticCodes.GS_FILE_008 })
        assertTrue(events.any { it.code == DiagnosticCodes.GS_FILE_006 })
    }

    @Test
    fun `corrupted payload fails SHA-256 verification with GS-FILE-003 and discards partial file`() = runTest {
        val sampleBytes = "Corrupted-Bytes-On-Wire".toByteArray()
        val wrongSha = "0".repeat(64)
        val uri = Uri.parse("content://com.example.test.tracking.provider/payload/corrupt")
        TrackingContentProvider.payloadBytesByUri[uri.toString()] = sampleBytes
        var exportInvoked = false

        val thrown = try {
            storage.copyVerifyAndSave(
                transferId = "tx-sha-fail",
                meta = TransferItemMetadata(0, "corrupt.bin", "application/octet-stream", sampleBytes.size.toLong(), ContentType.GENERIC_FILE, wrongSha),
                source = IncomingPayloadSource.ContentUriSource(uri),
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
        assertTrue("Nearby temp URI cleaned up on verification failure", TrackingContentProvider.deletedUris.contains(uri.toString()))
    }

    @Test
    fun `byte length mismatch throws GS-FILE-004 before trusting file`() = runTest {
        val sampleBytes = "12345".toByteArray()
        val sourceFile = File(context.cacheDir, "nearby_short.bin").apply { writeBytes(sampleBytes) }
        val sha = HashUtil.calculateSha256(ByteArrayInputStream(sampleBytes))

        val thrown = try {
            storage.copyVerifyAndSave(
                transferId = "tx-len-fail",
                meta = TransferItemMetadata(0, "truncated.pdf", "application/pdf", 5000L, ContentType.DOCUMENT_PDF, sha),
                source = IncomingPayloadSource.AppPrivateFileSource(sourceFile)
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
                meta = TransferItemMetadata(0, "photo.jpg", "image/jpeg", 100L, ContentType.IMAGE, ""),
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
        val payloadBytes = "Verified-Private-Fallback-File".toByteArray()
        val sha = HashUtil.calculateSha256(ByteArrayInputStream(payloadBytes))
        val sourceFile = File(context.cacheDir, "nearby_mediastore_fail.pdf").apply {
            writeBytes(payloadBytes)
        }

        val result = storage.copyVerifyAndSave(
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
            },
            customFileProviderResolver = { privateFile ->
                Uri.parse("content://${context.packageName}.fileprovider/gestoshare_files/received/${privateFile.name}")
            }
        )

        assertTrue("Verified private file must remain intact", result.permanentFile.exists())
        assertFalse("Filename path traversal must be sanitized", result.permanentFile.name.contains(".."))
        assertFalse("Filename question marks sanitized", result.permanentFile.name.contains("?"))
        assertFalse(result.mediaStoreExported)
        assertTrue(result.mediaStoreNote?.contains("Simulated MediaStore") == true)
        assertEquals(ContentResolver.SCHEME_CONTENT, result.shareableUri?.scheme)

        awaitDiagnosticsWrites()
        val events = diagnostics.errorEvents.first()
        assertTrue(events.any { it.code == DiagnosticCodes.GS_FILE_006 })
        sourceFile.delete()
    }

    // -------------------------------------------------------------------------
    // Required Callback-Order Unit Tests (Sequences A through J)
    // -------------------------------------------------------------------------

    private data class CoordinatedRunResult(
        val tracker: PayloadManifestTracker,
        val savedResults: Map<Long, SavedPayloadResult>,
        val verifyInvocations: Map<Long, Int>,
        val errors: Map<Long, Throwable>
    )

    private enum class EventType { FILE, SUCCESS, MANIFEST }

    private fun runCoordinatedSequence(
        transferId: String = "tx-seq",
        events: List<Triple<EventType, Long, Any?>>,
        uriByPayloadId: Map<Long, Uri>,
        failCopy: Boolean = false
    ): CoordinatedRunResult {
        val tracker = PayloadManifestTracker(transferId)
        val savedResults = linkedMapOf<Long, SavedPayloadResult>()
        val verifyCounts = mutableMapOf<Long, AtomicInteger>()
        val errors = linkedMapOf<Long, Throwable>()

        fun tryTrigger(payloadId: Long) {
            val claim = tracker.tryClaimForProcessing(payloadId, Uri::class.java) ?: return
            verifyCounts.getOrPut(payloadId) { AtomicInteger(0) }.incrementAndGet()
            try {
                val source = if (failCopy) {
                    IncomingPayloadSource.ContentUriSource(Uri.parse("content://com.example.test.tracking.provider/fail/io"))
                } else {
                    IncomingPayloadSource.ContentUriSource(claim.payload)
                }
                val saved = storage.copyVerifyAndSave(
                    transferId = transferId,
                    meta = claim.metadata,
                    source = source,
                    onStageChange = { stage, _ ->
                        when (stage) {
                            TransferPipelineStage.VERIFYING_INTEGRITY -> tracker.markFileCopyCompleted(payloadId)
                            TransferPipelineStage.EXPORTING_TO_DOWNLOADS -> tracker.markIntegrityVerified(payloadId)
                            else -> Unit
                        }
                    },
                    customMediaStoreExporter = { f, name, _ ->
                        Uri.parse("content://media/external/downloads/${name.hashCode().and(0xFFFF)}") to null
                    }
                )
                tracker.markFinalStorageCompleted(payloadId)
                savedResults[payloadId] = saved
            } catch (t: Throwable) {
                tracker.markFailed(payloadId, t.message)
                errors[payloadId] = t
                if (t is TransferVerificationException) {
                    diagnostics.recordException(
                        throwable = t,
                        sourceComponent = "IncomingPayloadStorageTest",
                        operation = "coordinatedPipeline",
                        defaultCode = t.code,
                        defaultCategory = t.category,
                        relatedTransferId = transferId
                    )
                }
            }
        }

        for ((type, pid, arg) in events) {
            when (type) {
                EventType.FILE -> {
                    tracker.onFilePayloadReceived(pid, uriByPayloadId.getValue(pid))
                    tryTrigger(pid)
                }
                EventType.SUCCESS -> {
                    tracker.onTransportSuccess(pid)
                    tryTrigger(pid)
                }
                EventType.MANIFEST -> {
                    val meta = arg as TransferItemMetadata
                    tracker.registerManifest(transferId, pid, meta)
                    tryTrigger(pid)
                }
            }
        }

        return CoordinatedRunResult(
            tracker = tracker,
            savedResults = savedResults,
            verifyInvocations = verifyCounts.mapValues { it.value.get() },
            errors = errors
        )
    }

    private fun registerSampleUri(id: Long, content: ByteArray): Pair<Uri, TransferItemMetadata> {
        val uri = Uri.parse("content://com.example.test.tracking.provider/payload/$id")
        TrackingContentProvider.payloadBytesByUri[uri.toString()] = content
        val sha = runBlocking { HashUtil.calculateSha256(ByteArrayInputStream(content)) }
        val meta = TransferItemMetadata(
            itemIndex = 0,
            name = "item_$id.bin",
            mimeType = "application/octet-stream",
            sizeBytes = content.size.toLong(),
            contentType = ContentType.GENERIC_FILE,
            sha256 = sha
        )
        return uri to meta
    }

    private fun assertSingleVerifiedSuccess(
        run: CoordinatedRunResult,
        payloadId: Long,
        expectedBytes: ByteArray,
        expectedMeta: TransferItemMetadata
    ) {
        assertTrue(run.errors.isEmpty())
        assertEquals(1, run.verifyInvocations[payloadId])
        val saved = run.savedResults[payloadId]
        assertNotNull(saved)
        assertEquals(expectedBytes.size.toLong(), saved!!.bytesCopied)
        assertEquals(expectedMeta.sha256, saved.sha256Hash)
        assertTrue(saved.permanentFile.exists())
        assertTrue(saved.mediaStoreExported)
        assertEquals(1, File(context.filesDir, "received").listFiles()?.size ?: 0)

        val state = run.tracker.getLifecycleState(payloadId)!!
        assertTrue(state.filePayloadReceived)
        assertTrue(state.transportSuccessReceived)
        assertTrue(state.manifestReceived)
        assertTrue(state.processingStarted)
        assertTrue(state.fileCopyCompleted)
        assertTrue(state.integrityVerified)
        assertTrue(state.finalStorageCompleted)
        assertFalse(state.isCancelledOrFailed)
    }

    @Test
    fun `sequence A - FILE then SUCCESS then MANIFEST verifies and saves once`() {
        val bytes = "Sequence-A-FILE-SUCCESS-MANIFEST".toByteArray()
        val (uri, meta) = registerSampleUri(1001L, bytes)
        val run = runCoordinatedSequence(
            events = listOf(
                Triple(EventType.FILE, 1001L, null),
                Triple(EventType.SUCCESS, 1001L, null),
                Triple(EventType.MANIFEST, 1001L, meta)
            ),
            uriByPayloadId = mapOf(1001L to uri)
        )
        assertSingleVerifiedSuccess(run, 1001L, bytes, meta)
    }

    @Test
    fun `sequence B - SUCCESS then FILE then MANIFEST never drops early SUCCESS`() {
        val bytes = "Sequence-B-SUCCESS-FILE-MANIFEST".toByteArray()
        val (uri, meta) = registerSampleUri(1002L, bytes)
        val run = runCoordinatedSequence(
            events = listOf(
                Triple(EventType.SUCCESS, 1002L, null),
                Triple(EventType.FILE, 1002L, null),
                Triple(EventType.MANIFEST, 1002L, meta)
            ),
            uriByPayloadId = mapOf(1002L to uri)
        )
        assertSingleVerifiedSuccess(run, 1002L, bytes, meta)
    }

    @Test
    fun `sequence C - MANIFEST then FILE then SUCCESS verifies and saves once`() {
        val bytes = "Sequence-C-MANIFEST-FILE-SUCCESS".toByteArray()
        val (uri, meta) = registerSampleUri(1003L, bytes)
        val run = runCoordinatedSequence(
            events = listOf(
                Triple(EventType.MANIFEST, 1003L, meta),
                Triple(EventType.FILE, 1003L, null),
                Triple(EventType.SUCCESS, 1003L, null)
            ),
            uriByPayloadId = mapOf(1003L to uri)
        )
        assertSingleVerifiedSuccess(run, 1003L, bytes, meta)
    }

    @Test
    fun `sequence D - MANIFEST then SUCCESS then FILE resumes processing when FILE arrives last`() {
        val bytes = "Sequence-D-MANIFEST-SUCCESS-FILE".toByteArray()
        val (uri, meta) = registerSampleUri(1004L, bytes)
        val run = runCoordinatedSequence(
            events = listOf(
                Triple(EventType.MANIFEST, 1004L, meta),
                Triple(EventType.SUCCESS, 1004L, null),
                Triple(EventType.FILE, 1004L, null)
            ),
            uriByPayloadId = mapOf(1004L to uri)
        )
        assertSingleVerifiedSuccess(run, 1004L, bytes, meta)
    }

    @Test
    fun `sequence E - duplicate SUCCESS callbacks process and save exactly once`() {
        val bytes = "Sequence-E-Duplicate-SUCCESS".toByteArray()
        val (uri, meta) = registerSampleUri(1005L, bytes)
        val run = runCoordinatedSequence(
            events = listOf(
                Triple(EventType.FILE, 1005L, null),
                Triple(EventType.MANIFEST, 1005L, meta),
                Triple(EventType.SUCCESS, 1005L, null),
                Triple(EventType.SUCCESS, 1005L, null),
                Triple(EventType.SUCCESS, 1005L, null)
            ),
            uriByPayloadId = mapOf(1005L to uri)
        )
        assertSingleVerifiedSuccess(run, 1005L, bytes, meta)
    }

    @Test
    fun `sequence F - duplicate FILE callbacks process and save exactly once`() {
        val bytes = "Sequence-F-Duplicate-FILE".toByteArray()
        val (uri, meta) = registerSampleUri(1006L, bytes)
        val run = runCoordinatedSequence(
            events = listOf(
                Triple(EventType.SUCCESS, 1006L, null),
                Triple(EventType.FILE, 1006L, null),
                Triple(EventType.FILE, 1006L, null),
                Triple(EventType.MANIFEST, 1006L, meta),
                Triple(EventType.FILE, 1006L, null)
            ),
            uriByPayloadId = mapOf(1006L to uri)
        )
        assertSingleVerifiedSuccess(run, 1006L, bytes, meta)
    }

    @Test
    fun `sequence G - multiple FILE payloads arriving out of order correlate accurately`() {
        val bytes0 = "First-File-Image-Bytes-Alpha".toByteArray()
        val bytes1 = "Second-File-Document-Bytes-Beta-Longer".toByteArray()
        val uri0 = Uri.parse("content://com.example.test.tracking.provider/payload/2001")
        val uri1 = Uri.parse("content://com.example.test.tracking.provider/payload/2002")
        TrackingContentProvider.payloadBytesByUri[uri0.toString()] = bytes0
        TrackingContentProvider.payloadBytesByUri[uri1.toString()] = bytes1

        val meta0 = TransferItemMetadata(
            itemIndex = 0,
            name = "alpha.jpg",
            mimeType = "image/jpeg",
            sizeBytes = bytes0.size.toLong(),
            contentType = ContentType.IMAGE,
            sha256 = runBlocking { HashUtil.calculateSha256(ByteArrayInputStream(bytes0)) }
        )
        val meta1 = TransferItemMetadata(
            itemIndex = 1,
            name = "beta.pdf",
            mimeType = "application/pdf",
            sizeBytes = bytes1.size.toLong(),
            contentType = ContentType.DOCUMENT_PDF,
            sha256 = runBlocking { HashUtil.calculateSha256(ByteArrayInputStream(bytes1)) }
        )

        // Completely scrambled interleaved arrival order across two payload IDs
        val run = runCoordinatedSequence(
            transferId = "tx-multi-order",
            events = listOf(
                Triple(EventType.SUCCESS, 2002L, null),
                Triple(EventType.FILE, 2001L, null),
                Triple(EventType.MANIFEST, 2002L, meta1),
                Triple(EventType.MANIFEST, 2001L, meta0),
                Triple(EventType.FILE, 2002L, null),
                Triple(EventType.SUCCESS, 2001L, null)
            ),
            uriByPayloadId = mapOf(2001L to uri0, 2002L to uri1)
        )

        assertTrue(run.errors.isEmpty())
        assertEquals(1, run.verifyInvocations[2001L])
        assertEquals(1, run.verifyInvocations[2002L])

        val saved0 = run.savedResults[2001L]!!
        val saved1 = run.savedResults[2002L]!!
        assertEquals("alpha.jpg", saved0.permanentFile.name)
        assertEquals(meta0.sha256, saved0.sha256Hash)
        assertEquals(bytes0.size.toLong(), saved0.bytesCopied)
        assertEquals("beta.pdf", saved1.permanentFile.name)
        assertEquals(meta1.sha256, saved1.sha256Hash)
        assertEquals(bytes1.size.toLong(), saved1.bytesCopied)
        assertEquals(2, File(context.filesDir, "received").listFiles()?.size ?: 0)
    }

    @Test
    fun `sequence H - cancellation while waiting for metadata blocks processing and cleans up temp payloads`() {
        val bytes = "Sequence-H-Cancel-Before-Metadata".toByteArray()
        val (uri, meta) = registerSampleUri(1008L, bytes)
        val tracker = PayloadManifestTracker("tx-cancel-meta")

        assertTrue(tracker.onFilePayloadReceived(1008L, uri))
        assertTrue(tracker.onTransportSuccess(1008L))
        // Cannot process yet while waiting for metadata
        assertNull(tracker.tryClaimForProcessing(1008L, Uri::class.java))

        // Cancel session before TRANSFER_FILE_MAP arrives
        val payloadsToClean = tracker.cancelSession("User cancelled while waiting for metadata")
        assertEquals(listOf(uri), payloadsToClean)
        payloadsToClean.filterIsInstance<Uri>().forEach { context.contentResolver.delete(it, null, null) }

        // Late MANIFEST arrival after cancel MUST NOT trigger processing
        assertFalse(tracker.registerManifest("tx-cancel-meta", 1008L, meta))
        assertNull(tracker.tryClaimForProcessing(1008L, Uri::class.java))
        val state = tracker.getLifecycleState(1008L)!!
        assertTrue(state.isCancelledOrFailed)
        assertFalse(state.processingStarted)
        assertFalse(state.fileCopyCompleted)
        assertFalse(state.integrityVerified)
        assertFalse(state.finalStorageCompleted)
        assertTrue(TrackingContentProvider.deletedUris.contains(uri.toString()))
        assertEquals(0, File(context.filesDir, "received").listFiles()?.size ?: 0)
    }

    @Test
    fun `sequence I - failure during app-private copy records error avoids export and cleans temp storage`() = runTest {
        val bytes = "Sequence-I-Copy-Failure".toByteArray()
        val (uri, meta) = registerSampleUri(1009L, bytes)
        val run = runCoordinatedSequence(
            transferId = "tx-copy-fail",
            events = listOf(
                Triple(EventType.FILE, 1009L, null),
                Triple(EventType.MANIFEST, 1009L, meta),
                Triple(EventType.SUCCESS, 1009L, null)
            ),
            uriByPayloadId = mapOf(1009L to uri),
            failCopy = true
        )

        assertTrue(run.savedResults.isEmpty())
        assertTrue(run.errors[1009L] is TransferVerificationException)
        val state = run.tracker.getLifecycleState(1009L)!!
        assertTrue(state.processingStarted)
        assertFalse(state.fileCopyCompleted)
        assertFalse(state.integrityVerified)
        assertFalse(state.finalStorageCompleted)
        assertTrue(state.isCancelledOrFailed)
        assertEquals(0, File(context.filesDir, "received").listFiles()?.size ?: 0)
        assertEquals(0, File(context.cacheDir, "incoming_verify").listFiles()?.size ?: 0)

        awaitDiagnosticsWrites()
        val loggedErrors = diagnostics.errorEvents.first()
        assertTrue(loggedErrors.any { it.relatedTransferId == "tx-copy-fail" && it.code == DiagnosticCodes.GS_FILE_001 })
    }

    @Test
    fun `sequence J - SHA-256 mismatch rejects payload deletes temp files records GS-FILE-003 and skips export`() = runTest {
        val bytes = "Sequence-J-Bad-Digest-Payload".toByteArray()
        val uri = Uri.parse("content://com.example.test.tracking.provider/payload/1010")
        TrackingContentProvider.payloadBytesByUri[uri.toString()] = bytes
        val mismatchedMeta = TransferItemMetadata(
            itemIndex = 0,
            name = "tampered.pdf",
            mimeType = "application/pdf",
            sizeBytes = bytes.size.toLong(),
            contentType = ContentType.DOCUMENT_PDF,
            sha256 = "f".repeat(64)
        )

        val run = runCoordinatedSequence(
            transferId = "tx-sha-seq-fail",
            events = listOf(
                Triple(EventType.MANIFEST, 1010L, mismatchedMeta),
                Triple(EventType.SUCCESS, 1010L, null),
                Triple(EventType.FILE, 1010L, null)
            ),
            uriByPayloadId = mapOf(1010L to uri)
        )

        assertTrue(run.savedResults.isEmpty())
        val err = run.errors[1010L] as? TransferVerificationException
        assertNotNull(err)
        assertEquals(DiagnosticCodes.GS_FILE_003, err!!.code)

        val state = run.tracker.getLifecycleState(1010L)!!
        assertTrue(state.fileCopyCompleted)
        assertFalse(state.integrityVerified)
        assertFalse(state.finalStorageCompleted)
        assertTrue(state.isCancelledOrFailed)
        assertTrue(TrackingContentProvider.deletedUris.contains(uri.toString()))
        assertEquals(0, File(context.filesDir, "received").listFiles()?.size ?: 0)
        assertEquals(0, File(context.cacheDir, "incoming_verify").listFiles()?.size ?: 0)

        awaitDiagnosticsWrites()
        val loggedErrors = diagnostics.errorEvents.first()
        assertTrue(loggedErrors.any { it.relatedTransferId == "tx-sha-seq-fail" && it.code == DiagnosticCodes.GS_FILE_003 })
    }

    // -------------------------------------------------------------------------
    // Receiver Busy Concurrency Limitation Test
    // -------------------------------------------------------------------------

    @Test
    fun `second incoming TRANSFER_OFFER while receiver busy sends explicit busy TRANSFER_REJECT and logs cleanly without GS_NET_002`() = runTest {
        val sentControl = mutableListOf<Pair<String, JSONObject>>()
        val manager = RealNearbyTransferManager(
            context = context,
            scope = this,
            diagnostics = diagnostics
        )
        manager.controlPacketHookForTesting = { endpointId, json ->
            sentControl.add(endpointId to json)
        }

        val sender1 = DeviceItem("ep-1", "Redmi 1", DeviceType.PHONE, DeviceConnectionState.CONNECTED)
        val sender2 = DeviceItem("ep-2", "Infinix 1", DeviceType.PHONE, DeviceConnectionState.CONNECTED)
        manager.registerConnectedPeerForTesting(sender1)
        manager.registerConnectedPeerForTesting(sender2)

        fun buildOfferJson(txId: String, senderName: String): String = JSONObject().apply {
            put("type", "TRANSFER_OFFER")
            put("transferId", txId)
            put("senderName", senderName)
            put("totalBytes", 2048L)
            put("items", JSONArray().apply {
                put(JSONObject().apply {
                    put("itemIndex", 0)
                    put("name", "photo.jpg")
                    put("mimeType", "image/jpeg")
                    put("sizeBytes", 2048L)
                    put("contentType", ContentType.IMAGE.name)
                    put("sha256", "a".repeat(64))
                })
            })
        }.toString()

        // First offer becomes pending (and then accepted)
        manager.processControlMessageForTesting("ep-1", buildOfferJson("tx-offer-1", "Redmi 1"))
        assertEquals("tx-offer-1", manager.incomingTransferOffer.value?.transferId)
        manager.acceptTransferOffer("tx-offer-1")

        // Second concurrent offer from ep-2 while ep-1 session is active
        manager.processControlMessageForTesting("ep-2", buildOfferJson("tx-offer-2", "Infinix 1"))

        val busyReject = sentControl.lastOrNull { it.first == "ep-2" && it.second.optString("type") == "TRANSFER_REJECT" }
        assertNotNull("Expected explicit TRANSFER_REJECT sent to second sender", busyReject)
        assertEquals("tx-offer-2", busyReject!!.second.getString("transferId"))
        assertTrue(busyReject.second.optBoolean("busy", false))
        assertTrue(busyReject.second.getString("reason").contains("busy", ignoreCase = true))

        // Active receive session for ep-1 stays completely intact
        assertEquals("tx-offer-1", manager.activeTransfer.value?.id)
        assertEquals(TransferStatus.TRANSFERRING, manager.activeTransfer.value?.status)

        awaitDiagnosticsWrites()
        val logs = diagnostics.operationalLogs.first()
        assertTrue(logs.any { it.level == "WARN" && it.message.contains("tx-offer-2") && it.message.contains("busy", ignoreCase = true) })

        val errorEvents = diagnostics.errorEvents.first()
        assertFalse(
            "Busy concurrency rejection must NOT be misclassified as GS-NET-002/003 connection failure",
            errorEvents.any { it.code == DiagnosticCodes.GS_NET_002 || it.code == DiagnosticCodes.GS_NET_003 }
        )
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

    class TrackingContentProvider : ContentProvider() {
        companion object {
            val payloadBytesByUri = mutableMapOf<String, ByteArray>()
            val deletedUris = mutableSetOf<String>()

            fun reset() {
                payloadBytesByUri.clear()
                deletedUris.clear()
            }
        }

        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (uri.toString().endsWith("/fail/io")) {
                throw IOException("Simulated stream I/O failure during copy")
            }
            val bytes = payloadBytesByUri[uri.toString()]
                ?: throw IOException("No mock bytes registered for $uri")
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            val writeEnd = pipe[1]
            ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                out.write(bytes)
            }
            return readEnd
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deletedUris.add(uri.toString())
            return 1
        }

        override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
        override fun getType(uri: Uri): String? = "application/octet-stream"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
