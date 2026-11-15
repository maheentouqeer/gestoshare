package com.example.util

import com.example.model.TransferItemMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-payload 7-stage lifecycle snapshot for incoming FILE payloads.
 */
data class PayloadLifecycleState(
    val payloadId: Long,
    val filePayloadReceived: Boolean = false,
    val transportSuccessReceived: Boolean = false,
    val manifestReceived: Boolean = false,
    val processingStarted: Boolean = false,
    val fileCopyCompleted: Boolean = false,
    val integrityVerified: Boolean = false,
    val finalStorageCompleted: Boolean = false,
    val isCancelledOrFailed: Boolean = false,
    val failureReason: String? = null,
    val metadata: TransferItemMetadata? = null
)

/**
 * Atomic one-shot claim returned by [PayloadManifestTracker.tryClaimForProcessing]
 * when all three independent Nearby arrival prerequisites (FILE payload object,
 * transport SUCCESS status, and TRANSFER_FILE_MAP metadata) are present while the
 * receive session remains active.
 */
data class ReadyPayloadClaim<T : Any>(
    val payloadId: Long,
    val payload: T,
    val metadata: TransferItemMetadata
)

/**
 * Thread-safe, callback-order-independent coordinator for incoming Nearby FILE payloads,
 * scoped strictly to a single [transferId].
 *
 * Nearby Connections delivers `onPayloadReceived(FILE)`, `onPayloadTransferUpdate(SUCCESS)`,
 * and `TRANSFER_FILE_MAP` (`BYTES`) over independent channels whose arrival order varies across
 * Android OEMs and radios. This coordinator tracks all 7 lifecycle stages per `payloadId`:
 * 1. FILE payload object received
 * 2. Transport completion (`SUCCESS`) received
 * 3. Metadata manifest received (`TRANSFER_FILE_MAP`)
 * 4. Processing claimed/started (idempotent, at-most-once)
 * 5. App-private file copy completed
 * 6. Cryptographic SHA-256 & byte-length integrity verified
 * 7. Final storage/MediaStore export completed
 */
class PayloadManifestTracker(
    val transferId: String
) {
    private class Entry(val payloadId: Long) {
        var payloadObj: Any? = null
        var metadata: TransferItemMetadata? = null
        var filePayloadReceived: Boolean = false
        var transportSuccessReceived: Boolean = false
        var manifestReceived: Boolean = false
        var processingStarted: Boolean = false
        var fileCopyCompleted: Boolean = false
        var integrityVerified: Boolean = false
        var finalStorageCompleted: Boolean = false
        var isCancelledOrFailed: Boolean = false
        var failureReason: String? = null

        fun toSnapshot(): PayloadLifecycleState = PayloadLifecycleState(
            payloadId = payloadId,
            filePayloadReceived = filePayloadReceived,
            transportSuccessReceived = transportSuccessReceived,
            manifestReceived = manifestReceived,
            processingStarted = processingStarted,
            fileCopyCompleted = fileCopyCompleted,
            integrityVerified = integrityVerified,
            finalStorageCompleted = finalStorageCompleted,
            isCancelledOrFailed = isCancelledOrFailed,
            failureReason = failureReason,
            metadata = metadata
        )
    }

    private val lock = Any()
    private val entries = ConcurrentHashMap<Long, Entry>()

    @Volatile
    private var sessionActive: Boolean = true

    private fun getOrCreateLocked(payloadId: Long): Entry =
        entries.getOrPut(payloadId) { Entry(payloadId) }

    fun isSessionActive(): Boolean = sessionActive

    /**
     * Step 1: Registers an incoming `Payload.Type.FILE` object for [payloadId].
     * Returns `true` if newly registered while session is active; `false` if duplicate
     * or if session is already inactive/cancelled.
     */
    fun onFilePayloadReceived(payloadId: Long, payload: Any): Boolean {
        synchronized(lock) {
            if (!sessionActive) return false
            val entry = getOrCreateLocked(payloadId)
            if (entry.isCancelledOrFailed || entry.filePayloadReceived) {
                return false
            }
            entry.payloadObj = payload
            entry.filePayloadReceived = true
            return true
        }
    }

    /**
     * Step 2: Records Nearby transport `PayloadTransferUpdate.Status.SUCCESS` for [payloadId].
     * Idempotent across duplicate `SUCCESS` callbacks.
     */
    fun onTransportSuccess(payloadId: Long): Boolean {
        synchronized(lock) {
            if (!sessionActive) return false
            val entry = getOrCreateLocked(payloadId)
            if (entry.isCancelledOrFailed || entry.transportSuccessReceived) {
                return false
            }
            entry.transportSuccessReceived = true
            return true
        }
    }

    /**
     * Backwards-compatible alias for [onTransportSuccess].
     */
    fun markPayloadCompleted(payloadId: Long) {
        onTransportSuccess(payloadId)
    }

    /**
     * Step 3: Associates Nearby [payloadId] with verified [meta] for [transferId].
     * Returns `false` if [metaTransferId] does not match this session's [transferId] or session is inactive.
     */
    fun registerManifest(
        metaTransferId: String,
        payloadId: Long,
        meta: TransferItemMetadata
    ): Boolean {
        if (metaTransferId != transferId) return false
        synchronized(lock) {
            if (!sessionActive) return false
            val entry = getOrCreateLocked(payloadId)
            if (entry.isCancelledOrFailed) return false
            if (entry.manifestReceived && entry.metadata != null) {
                return false
            }
            entry.metadata = meta
            entry.manifestReceived = true
            return true
        }
    }

    /**
     * Returns `true` if either a FILE payload or manifest metadata has been observed for [payloadId].
     */
    fun isFileOrManifestKnown(payloadId: Long): Boolean {
        synchronized(lock) {
            val entry = entries[payloadId] ?: return false
            return entry.filePayloadReceived || entry.manifestReceived
        }
    }

    fun getMetadata(payloadId: Long): TransferItemMetadata? {
        synchronized(lock) {
            return entries[payloadId]?.metadata
        }
    }

    fun hasMetadata(payloadId: Long): Boolean {
        synchronized(lock) {
            return entries[payloadId]?.manifestReceived == true
        }
    }

    /**
     * Legacy two-way claim kept for compatibility with existing callers/tests where no payload
     * object wrapper was supplied separately. Marks `processingStarted` atomically once metadata exists.
     */
    fun claimForVerification(payloadId: Long): TransferItemMetadata? {
        synchronized(lock) {
            if (!sessionActive) return null
            val entry = entries[payloadId] ?: return null
            if (entry.isCancelledOrFailed || entry.processingStarted) return null
            val meta = entry.metadata ?: return null
            entry.processingStarted = true
            return meta
        }
    }

    /**
     * Step 4 (Three-Way Idempotent Trigger): Atomically claims [payloadId] for copy,
     * verification, and storage ONLY when ALL conditions hold:
     * - Session remains active and this payload has not failed/cancelled
     * - Valid FILE payload object of type [payloadClass] exists
     * - Transport completion (`SUCCESS`) has been received
     * - Matching manifest metadata has been received
     * - Processing has not already started
     */
    fun <T : Any> tryClaimForProcessing(
        payloadId: Long,
        payloadClass: Class<T>
    ): ReadyPayloadClaim<T>? {
        synchronized(lock) {
            if (!sessionActive) return null
            val entry = entries[payloadId] ?: return null
            if (entry.isCancelledOrFailed || entry.processingStarted) return null
            if (!entry.filePayloadReceived || !entry.transportSuccessReceived || !entry.manifestReceived) {
                return null
            }
            val meta = entry.metadata ?: return null
            val rawPayload = entry.payloadObj ?: return null
            if (!payloadClass.isInstance(rawPayload)) return null

            entry.processingStarted = true
            return ReadyPayloadClaim(
                payloadId = payloadId,
                payload = payloadClass.cast(rawPayload)!!,
                metadata = meta
            )
        }
    }

    /**
     * Step 5: Marks app-private copy finished for [payloadId].
     */
    fun markFileCopyCompleted(payloadId: Long) {
        synchronized(lock) {
            entries[payloadId]?.fileCopyCompleted = true
        }
    }

    /**
     * Step 6: Marks byte-length + SHA-256 verification finished for [payloadId].
     */
    fun markIntegrityVerified(payloadId: Long) {
        synchronized(lock) {
            entries[payloadId]?.integrityVerified = true
        }
    }

    /**
     * Step 7: Marks app-private commit and optional MediaStore export finished for [payloadId].
     */
    fun markFinalStorageCompleted(payloadId: Long) {
        synchronized(lock) {
            entries[payloadId]?.finalStorageCompleted = true
        }
    }

    /**
     * Marks a specific [payloadId] failed or cancelled.
     */
    fun markFailed(payloadId: Long, reason: String?) {
        synchronized(lock) {
            val entry = getOrCreateLocked(payloadId)
            entry.isCancelledOrFailed = true
            entry.failureReason = reason
        }
    }

    /**
     * Marks the entire receive session inactive/cancelled and returns all registered
     * payload objects so temporary Nearby URIs/PFDs can be safely cleaned up.
     */
    fun cancelSession(reason: String? = "Transfer cancelled"): List<Any> {
        synchronized(lock) {
            sessionActive = false
            val payloads = mutableListOf<Any>()
            entries.values.forEach { entry ->
                if (!entry.finalStorageCompleted) {
                    entry.isCancelledOrFailed = true
                    if (entry.failureReason == null) {
                        entry.failureReason = reason
                    }
                }
                entry.payloadObj?.let { payloads.add(it) }
            }
            return payloads
        }
    }

    fun markSessionCompleted() {
        synchronized(lock) {
            sessionActive = false
        }
    }

    fun isProcessed(payloadId: Long): Boolean {
        synchronized(lock) {
            return entries[payloadId]?.processingStarted == true
        }
    }

    fun pendingMetadataPayloadIds(): Set<Long> {
        synchronized(lock) {
            return entries.values
                .filter { (it.transportSuccessReceived || it.filePayloadReceived) && !it.manifestReceived }
                .map { it.payloadId }
                .toSet()
        }
    }

    fun getLifecycleState(payloadId: Long): PayloadLifecycleState? {
        synchronized(lock) {
            return entries[payloadId]?.toSnapshot()
        }
    }

    fun allLifecycleStates(): Map<Long, PayloadLifecycleState> {
        synchronized(lock) {
            return entries.mapValues { it.value.toSnapshot() }
        }
    }

    fun allRegisteredPayloads(): List<Any> {
        synchronized(lock) {
            return entries.values.mapNotNull { it.payloadObj }
        }
    }
}
