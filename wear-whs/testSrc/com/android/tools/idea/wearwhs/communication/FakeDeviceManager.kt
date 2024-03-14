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

import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType

/**
 * Fake implementation of [WearHealthServicesDeviceManager] for testing.
 */
internal class FakeDeviceManager(
  internal val capabilities: List<WhsCapability> = WHS_CAPABILITIES) : WearHealthServicesDeviceManager {
  internal var failState = false
  internal val triggeredEvents = mutableListOf<EventTrigger>()
  internal var clearContentProviderInvocations = 0
  private val onDeviceStates = capabilities.associate { it.dataType to CapabilityState(true, null) }.toMutableMap()
  internal var activeExercise = false

  override fun getCapabilities() = capabilities

  override suspend fun loadActiveExercise() =
    failOrWrapResult(activeExercise)

  override suspend fun setCapabilities(capabilityUpdates: Map<WhsDataType, Boolean>) =
    failOrWrapResult(Unit).also {
      capabilityUpdates.forEach { (dataType, enabled) ->
        onDeviceStates[dataType] = CapabilityState(enabled, onDeviceStates[dataType]!!.overrideValue)
      }
    }

  override suspend fun overrideValues(overrideUpdates: Map<WhsDataType, Number?>) =
    failOrWrapResult(Unit).also {
      overrideUpdates.forEach { (dataType, value) ->
        onDeviceStates[dataType] = CapabilityState(onDeviceStates[dataType]!!.enabled, value?.toFloat())
      }
    }

  override suspend fun loadCurrentCapabilityStates() =
    failOrWrapResult(onDeviceStates)

  override suspend fun clearContentProvider(): Result<Unit> =
    failOrWrapResult(Unit).also {
      clearContentProviderInvocations++
      onDeviceStates.clear()
    }

  override suspend fun isWhsVersionSupported() =
    failOrWrapResult(true)

  override fun setSerialNumber(serialNumber: String) {}

  override suspend fun triggerEvent(eventTrigger: EventTrigger) =
    failOrWrapResult(triggeredEvents.add(eventTrigger)).map {}

  private fun <T> failOrWrapResult(value: T) =
    if (failState) {
      Result.failure(ConnectionLostException("Failed to run command"))
    }
    else {
      Result.success(value)
    }
}
