package com.example.util

import com.example.model.TransferItemMetadata
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe payload-to-metadata manifest tracker scoped strictly to a single [transferId].
 *
 * Nearby Connections delivers `TRANSFER_FILE_MAP` (BYTES) and `Payload.Type.FILE` streams over
 * independent channels whose completion/registration order is not guaranteed across OEM radios.
 * This tracker handles either arrival order and prevents duplicate payload verification.
 */
class PayloadManifestTracker(
    val transferId: String
) {
    private val metaByPayloadId = ConcurrentHashMap<Long, TransferItemMetadata>()
    private val completedPayloadIds = ConcurrentHashMap.newKeySet<Long>()
    private val processedPayloadIds = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Associates a Nearby [payloadId] with its verified [meta] for [transferId].
     * Returns false if [metaTransferId] does not match this session's [transferId].
     */
    fun registerManifest(
        metaTransferId: String,
        payloadId: Long,
        meta: TransferItemMetadata
    ): Boolean {
        if (metaTransferId != transferId) return false
        metaByPayloadId[payloadId] = meta
        return true
    }

    fun markPayloadCompleted(payloadId: Long) {
        completedPayloadIds.add(payloadId)
    }

    fun getMetadata(payloadId: Long): TransferItemMetadata? = metaByPayloadId[payloadId]

    fun hasMetadata(payloadId: Long): Boolean = metaByPayloadId.containsKey(payloadId)

    /**
     * Atomically claims a payload for verification & saving if its manifest metadata exists
     * and it has not already been processed. Returns `null` if metadata has not arrived yet
     * or the payload was already claimed.
     */
    fun claimForVerification(payloadId: Long): TransferItemMetadata? {
        val meta = metaByPayloadId[payloadId] ?: return null
        return if (processedPayloadIds.add(payloadId)) meta else null
    }

    fun isProcessed(payloadId: Long): Boolean = processedPayloadIds.contains(payloadId)

    fun pendingMetadataPayloadIds(): Set<Long> =
        completedPayloadIds.filter { !metaByPayloadId.containsKey(it) }.toSet()
}
