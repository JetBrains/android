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
import com.android.tools.idea.concurrency.scopeDisposable
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.communication.ContentProviderDeviceManager
import com.android.tools.idea.wearwhs.communication.ConnectionLostException
import com.android.tools.idea.wearwhs.communication.WearHealthServicesDeviceManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class WearHealthServicesToolWindowStateManagerImpl(
  private val deviceManager: WearHealthServicesDeviceManager = ContentProviderDeviceManager())
  : WearHealthServicesToolWindowStateManager, Disposable {
  private val scope = AndroidCoroutineScope(this)

  private var currentPreset = MutableStateFlow(Preset.STANDARD)
  private var capabilitiesList = MutableStateFlow(emptyList<WhsCapability>())
  private var capabilityToState = mapOf<WhsCapability, CapabilityState>()
  private var progress: MutableStateFlow<WhsStateManagerStatus> = MutableStateFlow(WhsStateManagerStatus.Idle)

  init {
    Disposer.register(this, scope.scopeDisposable())
    scope.launch {
      setCapabilities(deviceManager.loadCapabilities())
    }
  }

  private fun setCapabilities(whsCapabilities: List<WhsCapability>) {
    capabilityToState = whsCapabilities.associateWith { CapabilityState() }
    setPreset(currentPreset.value)
    scope.launch {
      capabilitiesList.emit(whsCapabilities)
    }
  }

  override fun getStatus(): StateFlow<WhsStateManagerStatus> = progress.asStateFlow()

  override fun getCapabilitiesList(): StateFlow<List<WhsCapability>> = capabilitiesList.asStateFlow()

  override fun getPreset(): StateFlow<Preset> = currentPreset.asStateFlow()

  override fun setPreset(preset: Preset) {
    scope.launch {
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
  }

  override fun getCapabilityEnabled(capability: WhsCapability): StateFlow<Boolean> =
    capabilityToState[capability]?.enabled?.asStateFlow() ?: throw IllegalArgumentException()

  override fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean) {
    val state = capabilityToState[capability] ?: return
    scope.launch {
      state.enabled.emit(enabled)
      state.synced.emit(false)
    }
  }

  override fun getOverrideValue(capability: WhsCapability): StateFlow<Float?> =
    capabilityToState[capability]?.overrideValue?.asStateFlow() ?: throw IllegalArgumentException()

  override fun setOverrideValue(capability: WhsCapability, value: Float?) {
    val state = capabilityToState[capability] ?: return
    scope.launch {
      state.overrideValue.emit(value)
      state.synced.emit(false)
    }
  }

  override fun getSynced(capability: WhsCapability) =
    capabilityToState[capability]?.synced?.asStateFlow() ?: throw IllegalArgumentException()

  override fun applyChanges() {
    scope.launch {
      for (entry in capabilityToState.entries.iterator()) {
        val capability = entry.key
        val state = entry.value
        if (state.synced.value) {
          continue
        }
        progress.emit(WhsStateManagerStatus.Syncing(capability))
        try {
          if (state.enabled.value) {
            deviceManager.enableCapability(capability)
          }
          else {
            deviceManager.disableCapability(capability)
          }
          if (state.overrideValue.value != 0f && state.overrideValue.value != null) {
            deviceManager.overrideValue(capability, state.overrideValue.value!!)
          }
        }
        catch (exception: ConnectionLostException) {
          progress.emit(WhsStateManagerStatus.ConnectionLost)
          return@launch
        }
        state.synced.emit(true)
      }
      progress.emit(WhsStateManagerStatus.Idle)
    }
  }

  override fun reset() {
    scope.launch {
      setPreset(Preset.STANDARD)
      for (entry in capabilityToState.keys) {
        setOverrideValue(entry, null)
      }
    }
  }

  override fun dispose() { // Clear all callbacks to avoid memory leaks
    capabilityToState = mapOf()
  }
}

internal data class CapabilityState(
  val enabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
  val overrideValue: MutableStateFlow<Float?> = MutableStateFlow(null),
  val synced: MutableStateFlow<Boolean> = MutableStateFlow(false),
)
