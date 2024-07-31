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

import com.android.adblib.AdbSession
import com.android.adblib.ClosedSessionException
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.device
import com.android.adblib.isOnline
import com.android.adblib.shellAsText
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.WhsDataValue
import com.intellij.openapi.diagnostic.Logger
import kotlin.reflect.full.isSuperclassOf

const val whsPackage: String = "com.google.android.wearable.healthservices"
const val whsConfigUri: String = "content://$whsPackage.dev.synthetic/synthetic_config"
const val whsActiveExerciseUri: String = "content://$whsPackage.dev.exerciseinfo"
private val capabilityStatePattern =
  Regex("Row: \\d+ data_type=(\\w+), is_enabled=(true|false), override_value=(\\d+\\.?\\d*E?\\d*)")
private val activeExerciseRegex = Regex("active_exercise=(true|false)")

/**
 * Content provider implementation of [WearHealthServicesDeviceManager].
 *
 * This class uses the content provider for the synthetic HAL in WHS to sync the current state of
 * the UI to the selected Wear OS device.
 */
internal class ContentProviderDeviceManager(
  private val adbSessionProvider: () -> AdbSession,
  private var capabilities: List<WhsCapability> = WHS_CAPABILITIES,
) : WearHealthServicesDeviceManager {
  private var serialNumber: String? = null
  private val logger = Logger.getInstance(ContentProviderDeviceManager::class.java)

  private var adbSession: AdbSession = adbSessionProvider()
    get() {
      try {
        field.throwIfClosed()
      } catch (closedException: ClosedSessionException) {
        field = adbSessionProvider()
      }
      return field
    }

  override fun getCapabilities() = capabilities

  override suspend fun loadCurrentCapabilityStates() =
    runAdbShellCommandIfConnected("content query --uri $whsConfigUri").map { output ->
      val contentProviderEntryMatches = capabilityStatePattern.findAll(output)

      val capabilities = mutableMapOf<WhsDataType, CapabilityState>()

      for (match in contentProviderEntryMatches) {
        val dataType = match.groupValues[1].toDataType()
        if (dataType == WhsDataType.DATA_TYPE_UNKNOWN) {
          continue
        }
        val isEnabled = match.groupValues[2].toBoolean()
        if (dataType.overrideDataType == WhsDataValue.NoValue::class) {
          capabilities[dataType] = CapabilityState.withNoValue(isEnabled, dataType)
          continue
        }
        val dataValue = dataType.valueFromString(match.groupValues[3])
        val isZero =
          (dataValue is WhsDataValue.IntValue && dataValue.value == 0) ||
            (dataValue is WhsDataValue.FloatValue && dataValue.value == 0.0f)
        val newState: CapabilityState =
          if (!isEnabled && isZero)
          // If data type is disabled and override has not been set content provider returns
          // override as 0, so ignore this value
          CapabilityState.disabled(dataType)
          else CapabilityState(isEnabled, dataValue)
        capabilities[dataType] = newState
      }

      capabilities
    }

  override suspend fun clearContentProvider() =
    runAdbShellCommandIfConnected("content delete --uri $whsConfigUri").map {}

  override fun setSerialNumber(serialNumber: String) {
    this.serialNumber = serialNumber
  }

  override suspend fun loadActiveExercise() =
    runAdbShellCommandIfConnected("content query --uri $whsActiveExerciseUri").map { output ->
      activeExerciseRegex.find(output)?.groupValues?.get(1)?.toBoolean() ?: false
    }

  private fun contentUpdateMultipleCapabilitiesBoolean(
    capabilityUpdates: Map<WhsDataType, Boolean>
  ): String {
    val sb = StringBuilder("content update --uri $whsConfigUri")
    for ((dataType, value) in capabilityUpdates.toSortedMap(compareBy { it.name })) {
      sb.append(bindString(dataType.name, value))
    }
    return sb.toString()
  }

  private fun contentUpdateMultipleCapabilitiesNumber(
    capabilityUpdates: Map<WhsDataType, Number?>
  ): String {
    val sb = StringBuilder("content update --uri $whsConfigUri")
    for ((dataType, value) in capabilityUpdates.toSortedMap(compareBy { it.name })) {
      if (!WhsDataValue.Value::class.isSuperclassOf(dataType.overrideDataType)) {
        continue // This dataType has no data
      }

      val bindValue =
        when (value) {
          null -> "\"\"" // clear override by setting it to empty string
          else -> { // set override
            when (dataType.overrideDataType) {
              WhsDataValue.IntValue::class -> value.toInt()
              WhsDataValue.FloatValue::class -> value.toFloat()
              else -> IllegalArgumentException("Unsupported data type")
            }
          }
        }

      sb.append(bindString(dataType.name, bindValue))
    }
    return sb.toString()
  }

  private inline fun <reified T> bindString(key: String, value: T): String {
    val type =
      when (value) {
        is Boolean -> 'b'
        is Int -> 'i'
        is Float,
        is Double -> 'f'
        else -> 's'
      }
    return " --bind $key:$type:$value"
  }

  override suspend fun setCapabilities(capabilityUpdates: Map<WhsDataType, Boolean>) =
    runAdbShellCommandIfConnected(contentUpdateMultipleCapabilitiesBoolean(capabilityUpdates))
      .map {}

  override suspend fun triggerEvent(eventTrigger: EventTrigger) =
    runAdbShellCommandIfConnected(triggerEventCommand(eventTrigger)).map {}

  private fun triggerEventCommand(eventTrigger: EventTrigger): String {
    val commandStringBuilder = StringBuilder("am broadcast -a \"${eventTrigger.eventKey}\"")
    for ((key, value) in eventTrigger.eventMetadata) {
      commandStringBuilder.append(" --es $key \"$value\"")
    }
    commandStringBuilder.append(" $whsPackage")
    return commandStringBuilder.toString()
  }

  override suspend fun overrideValues(overrideUpdates: List<WhsDataValue>) =
    runAdbShellCommandIfConnected(
        contentUpdateMultipleCapabilitiesNumber(
          overrideUpdates.associate { dataValue ->
            when (dataValue) {
              is WhsDataValue.IntValue -> dataValue.type to dataValue.value
              is WhsDataValue.FloatValue -> dataValue.type to dataValue.value
              is WhsDataValue.NoValue -> dataValue.type to null
            }
          }
        )
      )
      .map {}

  private suspend fun runAdbShellCommandIfConnected(command: String): Result<String> {
    if (serialNumber == null) {
      return loggedFailure(IllegalStateException("Serial number not set"))
    }

    // Wrap adbSession interactions with a try, as it can fail anywhere if it's closed
    return try {
      val deviceOnline =
        adbSession.connectedDevicesTracker.device(serialNumber!!)?.isOnline ?: false
      if (!deviceOnline) {
        return loggedFailure(ConnectionLostException("Device is not online"))
      }
      // This can still fail, if the device becomes unreachable between these lines
      Result.success(
        adbSession.deviceServices
          .shellAsText(DeviceSelector.fromSerialNumber(serialNumber!!), command)
          .stdout
      )
    } catch (e: Exception) {
      loggedFailure(e)
    }
  }

  private fun <T> loggedFailure(e: Exception): Result<T> = Result.failure(e.also { logger.warn(e) })
}

private fun String.toDataType(): WhsDataType {
  return WhsDataType.values().find { it.name == this } ?: WhsDataType.DATA_TYPE_UNKNOWN
}
