package com.example.manager

import com.example.model.ContentItem
import com.example.model.DeviceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface TransferStateManager {
    val stagedItems: StateFlow<List<ContentItem>>
    val targetDevice: StateFlow<DeviceItem?>
    val isReadyForGestureTransfer: StateFlow<Boolean>

    fun stageItem(item: ContentItem)
    fun stageMultipleItems(items: List<ContentItem>)
    fun removeItem(id: String)
    fun clearStagedItems()
    fun setTargetDevice(device: DeviceItem?)
    fun prepareForGestureTransfer()
}

class DefaultTransferStateManager : TransferStateManager {

    private val _stagedItems = MutableStateFlow<List<ContentItem>>(emptyList())
    override val stagedItems: StateFlow<List<ContentItem>> = _stagedItems.asStateFlow()

    private val _targetDevice = MutableStateFlow<DeviceItem?>(null)
    override val targetDevice: StateFlow<DeviceItem?> = _targetDevice.asStateFlow()

    private val _isReadyForGestureTransfer = MutableStateFlow(false)
    override val isReadyForGestureTransfer: StateFlow<Boolean> = _isReadyForGestureTransfer.asStateFlow()

    override fun stageItem(item: ContentItem) {
        val current = _stagedItems.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(item)
        _stagedItems.value = current
        _isReadyForGestureTransfer.value = current.isNotEmpty()
    }

    override fun stageMultipleItems(items: List<ContentItem>) {
        val current = _stagedItems.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val newItems = items.filter { it.id !in existingIds }
        current.addAll(newItems)
        _stagedItems.value = current
        _isReadyForGestureTransfer.value = current.isNotEmpty()
    }

    override fun removeItem(id: String) {
        val current = _stagedItems.value.toMutableList()
        current.removeAll { it.id == id }
        _stagedItems.value = current
        _isReadyForGestureTransfer.value = current.isNotEmpty()
    }

    override fun clearStagedItems() {
        _stagedItems.value = emptyList()
        _isReadyForGestureTransfer.value = false
    }

    override fun setTargetDevice(device: DeviceItem?) {
        _targetDevice.value = device
    }

    override fun prepareForGestureTransfer() {
        if (_stagedItems.value.isNotEmpty()) {
            _isReadyForGestureTransfer.value = true
        }
    }
}
