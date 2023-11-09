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
import com.android.tools.idea.wearwhs.communication.WearHealthServicesDeviceManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class WearHealthServicesToolWindowStateManagerImpl(
  private val deviceManager: WearHealthServicesDeviceManager = ContentProviderDeviceManager(),)
  : WearHealthServicesToolWindowStateManager, Disposable {
  private val scope = AndroidCoroutineScope(this)

  private var currentPreset = MutableStateFlow(Preset.STANDARD)
  private var capabilitiesList = MutableStateFlow(emptyList<WhsCapability>())
  private var capabilityToState = mapOf<WhsCapability, CapabilityState>()

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

  override fun getCapabilityEnabled(capability: WhsCapability): StateFlow<Boolean> {
    val state = capabilityToState[capability] ?: throw IllegalArgumentException()
    return state.enabled.asStateFlow()
  }

  override fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean) {
    val state = capabilityToState[capability] ?: return
    scope.launch {
      state.enabled.emit(enabled)
    }
  }

  override fun getOverrideValue(capability: WhsCapability): StateFlow<Float?> {
    val state = capabilityToState[capability] ?: throw IllegalArgumentException()
    return state.overrideValue.asStateFlow()
  }

  override fun setOverrideValue(capability: WhsCapability, value: Float?) {
    val state = capabilityToState[capability] ?: return
    scope.launch {
      state.overrideValue.emit(value)
    }
  }

  // TODO(b/305917691): Implement apply changes logic
  override fun applyChanges() {}

  // TODO(b/311143148): Implement reset logic
  override fun reset() {}

  override fun dispose() { // Clear all states to avoid memory leaks
    capabilityToState = mapOf()
  }
}

internal data class CapabilityState(
  val enabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
  val overrideValue: MutableStateFlow<Float?> = MutableStateFlow(null),
)
