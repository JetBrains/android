/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless.v2.ui

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@State(name = "WifiPairing", storages = [Storage("wifi.xml", roamingType = RoamingType.DEFAULT)])
@Service
// TODO(b/412571872) add tests
class WifiPairableDevicesPersistentStateComponent :
  SimplePersistentStateComponent<WifiPairableDevicesPersistentStateComponent.State>(State()) {
  class State : BaseState() {
    var hiddenDevices by stringSet()
  }

  private val _hiddenDevices = MutableStateFlow<Set<String>>(state.hiddenDevices.toSet())

  val hiddenDevices: StateFlow<Set<String>> = _hiddenDevices.asStateFlow()

  override fun loadState(newState: State) {
    super.loadState(newState)
    _hiddenDevices.value = newState.hiddenDevices.toSet()
  }

  fun addHiddenDevice(deviceIdentifier: String) {
    val currentPersistentSet = state.hiddenDevices
    if (!currentPersistentSet.contains(deviceIdentifier)) {
      // Using LinkedHashSet to maintain insertion order, consistent with `stringSet()`'s default.
      val newPersistentSet = LinkedHashSet(currentPersistentSet)
      newPersistentSet.add(deviceIdentifier)

      // The `stringSet()` delegate's `setValue` will be invoked,
      // which marks the state as modified (incrementing modification count) for persistence.
      state.hiddenDevices = newPersistentSet

      _hiddenDevices.value = newPersistentSet.toSet()
    }
  }

  companion object {
    fun getInstance(): WifiPairableDevicesPersistentStateComponent {
      return service()
    }
  }
}
