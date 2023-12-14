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
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.communication.ConnectionLostException
import com.android.tools.idea.wearwhs.communication.ContentProviderDeviceManager
import com.android.tools.idea.wearwhs.communication.WearHealthServicesDeviceManager
import com.intellij.collaboration.async.mapState
import com.intellij.openapi.Disposable
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class WearHealthServicesToolWindowStateManagerImpl(
  private val deviceManager: WearHealthServicesDeviceManager = ContentProviderDeviceManager())
  : WearHealthServicesToolWindowStateManager, Disposable {
  private val currentPreset = MutableStateFlow(Preset.STANDARD)
  private val capabilitiesList = MutableStateFlow(emptyList<WhsCapability>())
  private val capabilityToState = ConcurrentMap<WhsCapability, MutableStateFlow<CapabilityState>>()
  private val progress = MutableStateFlow<WhsStateManagerStatus>(WhsStateManagerStatus.Idle)

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

  override fun getCapabilitiesList(): StateFlow<List<WhsCapability>> = capabilitiesList.asStateFlow()

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
    val newState = stateFlow.value.copy(enabled = enabled)
    stateFlow.emit(newState)
  }

  override suspend fun setOverrideValue(capability: WhsCapability, value: Float?) {
    val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
    val newState = stateFlow.value.copy(overrideValue = value)
    stateFlow.emit(newState)
  }

  override suspend fun applyChanges() {
    for (entry in capabilityToState.entries.iterator()) {
      val capability = entry.key
      val stateFlow = entry.value
      val state = stateFlow.value
      if (state.synced) {
        continue
      }
      progress.emit(WhsStateManagerStatus.Syncing(capability))
      try {
        if (state.enabled) {
          deviceManager.enableCapability(capability)
        }
        else {
          deviceManager.disableCapability(capability)
        }
        if (state.overrideValue != 0f && state.overrideValue != null) {
          deviceManager.overrideValue(capability, state.overrideValue)
        }
      }
      catch (exception: ConnectionLostException) {
        progress.emit(WhsStateManagerStatus.ConnectionLost)
        return
      }
      stateFlow.emit(state.copy(synced = true))
      progress.emit(WhsStateManagerStatus.Idle)
    }
  }

  override suspend fun reset() {
    setPreset(Preset.STANDARD)
    for (entry in capabilityToState.keys) {
      setOverrideValue(entry, null)
    }
  }

  override fun dispose() { // Clear all callbacks to avoid memory leaks
    capabilityToState.clear()
  }
}

internal data class CapabilityState(
  val enabled: Boolean = false,
  val overrideValue: Float? = null,
  val synced: Boolean = false,
)
