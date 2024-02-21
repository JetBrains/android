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
import com.android.adblib.DeviceState
import com.android.adblib.shellAsText
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.take
import kotlin.jvm.Throws

const val whsPackage: String = "com.google.android.wearable.healthservices"
const val whsConfigUri: String = "content://$whsPackage.dev.synthetic/synthetic_config"
const val whsActiveExerciseUri: String = "content://$whsPackage.dev.exerciseinfo"
const val whsDevVersionCode = 1
const val whsMinimumVersionCode = 1447606
val capabilityStatePattern = Regex("Row: \\d+ data_type=(\\w+), is_enabled=(true|false), override_value=(\\d+\\.?\\d*)")
val versionCodePattern = Regex("versionCode=(\\d+)")
val activeExerciseRegex = Regex("active_exercise=(true|false)")

/**
 * Content provider implementation of [WearHealthServicesDeviceManager].
 *
 * This class uses the content provider for the synthetic HAL in WHS to sync the current state
 * of the UI to the selected Wear OS device.
 */
internal class ContentProviderDeviceManager(private val adbSessionProvider: () -> AdbSession,
                                            private var capabilities: List<WhsCapability> = WHS_CAPABILITIES) : WearHealthServicesDeviceManager {
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

  override suspend fun loadCurrentCapabilityStates(): Map<WhsDataType, CapabilityState> {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return emptyMap()
    }

    try {
      val output = runAdbShellCommandIfConnected("content query --uri $whsConfigUri")

      val contentProviderEntryMatches = capabilityStatePattern.findAll(output)

      val capabilities = mutableMapOf<WhsDataType, CapabilityState>()

      for (match in contentProviderEntryMatches) {
        val dataType = match.groupValues[1].toDataType()
        if (dataType == WhsDataType.DATA_TYPE_UNKNOWN) {
          continue
        }
        val isEnabled = match.groupValues[2].toBoolean()
        var overrideValue: Float? = match.groupValues[3].toFloat()
        if (!isEnabled && overrideValue == 0f) {
          // If data type is disabled and override has not been set content provider returns override as 0, so ignore this value
          overrideValue = null
        }

        capabilities[dataType] = CapabilityState(isEnabled, overrideValue)
      }

      return capabilities
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't load current capability states", e)
    }
  }

  override suspend fun clearContentProvider() {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return
    }

    try {
      runAdbShellCommandIfConnected("content delete --uri $whsConfigUri")
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't clear content provider", e)
    }
  }

  override suspend fun isWhsVersionSupported(): Boolean {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return false
    }

    try {
      val output = runAdbShellCommandIfConnected("dumpsys package $whsPackage | grep versionCode | head -n1")
      val versionCode: Int? = versionCodePattern.find(output)?.groupValues?.get(1)?.toInt()

      return versionCode != null && (versionCode == whsDevVersionCode || versionCode >= whsMinimumVersionCode)
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't determine if WHS version is supported", e)
    }
  }

  override fun setSerialNumber(serialNumber: String) {
    this.serialNumber = serialNumber
  }

  private fun activeExerciseCommand(): String {
    return "content query --uri $whsActiveExerciseUri"
  }

  override suspend fun loadActiveExercise(): Boolean {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return false
    }

    try {
      val output = runAdbShellCommandIfConnected(activeExerciseCommand())
      val activeExercise = activeExerciseRegex.find(output)?.groupValues?.get(1)?.toBoolean()
      return activeExercise ?: false
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't load active exercise", e)
    }
  }

  private fun contentUpdateMultipleCapabilities(capabilityUpdates: Map<WhsDataType, Any?>): String {
    val sb = StringBuilder("content update --uri $whsConfigUri")
    for ((dataType, value) in capabilityUpdates.toSortedMap(compareBy { it.name })) {
      if (dataType == WhsDataType.LOCATION && value !is Boolean) {
        continue // Location does not have an override value
      }

      val bindValue = when (value) {
        is Boolean -> value // enable or disable capability
        null -> "\"\"" // clear override by setting it to empty string
        else -> { // set override
          val override = value as Number
          if (dataType == WhsDataType.STEPS) override.toInt() else override.toFloat()
        }
      }

      sb.append(bindString(dataType.name, bindValue))
    }
    return sb.toString()
  }

  private inline fun <reified T> bindString(key: String, value: T): String {
    val type = when (value) {
      is Boolean -> 'b'
      is Int -> 'i'
      is Float -> 'f'
      else -> 's'
    }
    return " --bind $key:$type:$value"
  }

  override suspend fun setCapabilities(capabilityUpdates: Map<WhsDataType, Boolean>) {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return
    }

    try {
      val contentUpdateCommand = contentUpdateMultipleCapabilities(capabilityUpdates)
      runAdbShellCommandIfConnected(contentUpdateCommand)
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't set capabilities", e)
    }
  }

  override suspend fun triggerEvent(eventTrigger: EventTrigger) {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return
    }

    try {
      runAdbShellCommandIfConnected(triggerEventCommand(eventTrigger))
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't trigger event", e)
    }
  }

  private fun triggerEventCommand(eventTrigger: EventTrigger): String {
    val commandStringBuilder = StringBuilder("am broadcast -a \"${eventTrigger.eventKey}\"")
    for ((key, value) in eventTrigger.eventMetadata) {
      commandStringBuilder.append(" --es $key \"$value\"")
    }
    commandStringBuilder.append(" $whsPackage")
    return commandStringBuilder.toString()
  }

  override suspend fun overrideValues(overrideUpdates: Map<WhsDataType, Number?>) {
    if (serialNumber == null) {
      logger.warn(IllegalStateException("Serial number not set"))
      return
    }

    try {
      val contentUpdateCommand = contentUpdateMultipleCapabilities(overrideUpdates)
      runAdbShellCommandIfConnected(contentUpdateCommand)
    } catch (e: Exception) {
      throw ConnectionLostException("Couldn't override values", e)
    }
  }

  @Throws(ConnectionLostException::class)
  private suspend fun runAdbShellCommandIfConnected(command: String): String {
    var deviceOnline = false
    adbSession.hostServices.trackDevices().take(1).collect { devices ->
      deviceOnline = devices.any { it.serialNumber == serialNumber && it.deviceState == DeviceState.ONLINE }
    }
    if (!deviceOnline) {
      throw ConnectionLostException("Device is not online")
    }
    return adbSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber!!), command).stdout
  }
}

private fun String.toDataType(): WhsDataType {
  return WhsDataType.values().find { it.name == this } ?: WhsDataType.DATA_TYPE_UNKNOWN
}
