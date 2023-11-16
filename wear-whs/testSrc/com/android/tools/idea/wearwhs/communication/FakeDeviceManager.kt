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
package com.android.tools.idea.wearwhs.communication

import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability
import kotlinx.coroutines.delay

private const val DELAY_MS = 100L

/**
 * Fake implementation of [WearHealthServicesDeviceManager] for testing.
 */
internal class FakeDeviceManager(
  internal val capabilities: List<WhsCapability> = WHS_CAPABILITIES) : WearHealthServicesDeviceManager {
  internal var failState = false
  private val onDeviceStates = capabilities.associateWith { OnDeviceCapabilityState(false, null) }

  override suspend fun loadCapabilities() = if (failState) {
    throw ConnectionLostException("Failed to load capabilities")
  }
  else {
    delay(DELAY_MS)
    capabilities
  }

  override suspend fun loadOngoingExercise() = if (failState) {
    throw ConnectionLostException("Failed to load ongoing exercise")
  }
  else {
    delay(DELAY_MS)
    false
  }

  override suspend fun enableCapability(capability: WhsCapability) = if (failState) {
    throw ConnectionLostException("Failed to enable capability")
  }
  else {
    delay(DELAY_MS)
    onDeviceStates[capability]?.enabled = true
  }

  override suspend fun disableCapability(capability: WhsCapability) = if (failState) {
    throw ConnectionLostException("Failed to disable capability")
  }
  else {
    delay(DELAY_MS)
    onDeviceStates[capability]?.enabled = false
  }

  override suspend fun overrideValue(capability: WhsCapability, value: Float?) = if (failState) {
    throw ConnectionLostException("Failed to override value")
  }
  else {
    delay(DELAY_MS)
    onDeviceStates[capability]?.overrideValue = value
  }

  override suspend fun loadCurrentCapabilityStates(): Map<WhsCapability, OnDeviceCapabilityState> = if (failState) {
    throw ConnectionLostException("Failed to load capability states")
  }
  else {
    delay(DELAY_MS)
    onDeviceStates
  }
}
