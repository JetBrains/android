/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.communication.ConnectionLostException
import com.android.tools.idea.wearwhs.communication.WearHealthServicesDeviceManager
import com.android.tools.idea.wearwhs.logger.WearHealthServicesEventLogger
import com.intellij.openapi.Disposable
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch

internal class WearHealthServicesToolWindowStateManagerImpl(
  private val deviceManager: WearHealthServicesDeviceManager,
  private val logger: WearHealthServicesEventLogger = WearHealthServicesEventLogger())
  : WearHealthServicesToolWindowStateManager, Disposable {
  private val currentPreset = MutableStateFlow(Preset.ALL)
  private val capabilitiesList = MutableStateFlow(emptyList<WhsCapability>())
  private val capabilityToState = ConcurrentMap<WhsCapability, MutableStateFlow<CapabilityState>>()
  private val progress = MutableStateFlow<WhsStateManagerStatus>(WhsStateManagerStatus.Idle)

  // TODO(b/305924111): Update this value periodically to reflect it on the UI
  private val ongoingExercise = MutableStateFlow(false)

  override var serialNumber: String? = null
    set(value) {
      // Only accept non-null values to avoid tool window unbinding completely
      value?.let {
        logger.logBindEmulator()
        deviceManager.setSerialNumber(it)
        field = value
      }
    }

  init {
    AndroidCoroutineScope(this).launch {
      setCapabilities(deviceManager.loadCapabilities())
    }
  }

  private suspend fun setCapabilities(whsCapabilities: List<WhsCapability>) {
    capabilityToState.clear()
    capabilityToState.putAll(whsCapabilities.associateWith { MutableStateFlow(CapabilityState()) })
    setPreset(currentPreset.value)
    capabilitiesList.emit(whsCapabilities)
  }

  override fun getStatus(): StateFlow<WhsStateManagerStatus> = progress.asStateFlow()
  override fun getOngoingExercise(): StateFlow<Boolean> = ongoingExercise.asStateFlow()

  override suspend fun isWhsVersionSupported(): Boolean {
    return try {
      deviceManager.isWhsVersionSupported()
    } catch (exception: ConnectionLostException) {
      // TODO(b/320432666): For now catch this error and show whs version not supported UI, eventually show separate could not connect UI
      false
    }
  }

  override fun getCapabilitiesList(): StateFlow<List<WhsCapability>> = capabilitiesList.asStateFlow()

  // TODO(b/309609475): Check the actual WHS version using the device manager
  override suspend fun triggerEvent(eventTrigger: EventTrigger) {}

  override fun getPreset(): StateFlow<Preset> = currentPreset.asStateFlow()

  override suspend fun setPreset(preset: Preset) {
    when (preset) {
      Preset.STANDARD -> for (capability in capabilityToState.keys) {
        setCapabilityEnabled(capability, capability.isStandardCapability)
      }

      Preset.ALL -> for (capability in capabilityToState.keys) {
        setCapabilityEnabled(capability, true)
      }

      Preset.CUSTOM -> {}
    }
    currentPreset.emit(preset)
  }

  override fun getState(capability: WhsCapability): StateFlow<CapabilityState> =
    capabilityToState[capability]?.asStateFlow() ?: throw IllegalArgumentException()

  override suspend fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean) {
    val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
    val newState = stateFlow.value.copy(enabled = enabled, synced = false)
    stateFlow.emit(newState)
  }

  override suspend fun setOverrideValue(capability: WhsCapability, value: Float?) {
    val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
    val newState = stateFlow.value.copy(overrideValue = value, synced = false)
    stateFlow.emit(newState)
  }

  override suspend fun applyChanges() {
    progress.emit(WhsStateManagerStatus.Syncing)
    val capabilityUpdates = capabilityToState.entries.associate { it.key.key to it.value.value.enabled }
    val overrideUpdates = capabilityToState.entries.associate { it.key.key to it.value.value.overrideValue }
    try {
      deviceManager.setCapabilities(capabilityUpdates)
      deviceManager.overrideValues(overrideUpdates)
    } catch (exception: ConnectionLostException) {
      logger.logApplyChangesFailure()
      progress.emit(WhsStateManagerStatus.ConnectionLost)
      return
    }
    capabilityToState.entries.forEach {
      val stateFlow = it.value
      val state = stateFlow.value
      stateFlow.emit(state.copy(synced = true))
    }
    logger.logApplyChangesSuccess()
    progress.emit(WhsStateManagerStatus.Idle)
  }

  override suspend fun reset() {
    setPreset(Preset.ALL)
    for (entry in capabilityToState.keys) {
      setOverrideValue(entry, null)
    }
    deviceManager.clearContentProvider()
  }

  override fun dispose() { // Clear all callbacks to avoid memory leaks
    capabilityToState.clear()
  }
}
