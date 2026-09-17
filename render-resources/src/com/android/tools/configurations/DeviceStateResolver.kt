/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations

import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.configuration.DeviceConfigHelper
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.configurations.ConfigurationListener.CFG_DEVICE
import com.android.tools.configurations.ConfigurationListener.CFG_DEVICE_STATE
import java.util.ArrayList

/** Manages the specific hardware constraints and orientation matching logic for a Configuration. */
class DeviceStateResolver(private val context: Context) {

  interface Context {
    val settings: ConfigurationSettings
    val editedConfig: FolderConfiguration

    fun updateDeviceOverlay()

    fun computeBestDevice(): Device?
  }

  private var specificDevice: Device? = null
  private var device: Device? = null
  private var state: State? = null

  var stateName: String? = null
    private set

  val cachedDevice: Device?
    get() = device

  val cachedState: State?
    get() = state

  fun initFromEditedConfig() {
    val qualifier = context.editedConfig.screenOrientationQualifier
    stateName = qualifier?.value?.shortDisplayValue ?: stateName
  }

  @Slow
  fun getDevice(): Device? {
    if (device != null) return device

    // This preserves the protected computeBestDevice() extension point for subclasses!
    device = specificDevice ?: context.computeBestDevice()
    context.updateDeviceOverlay()
    return device
  }

  fun getDeviceState(): State? {
    if (state == null) {
      val currentDevice = getDevice()
      state = currentDevice.getDeviceState(stateName)
    }
    return state
  }

  fun setDevice(newDevice: Device?, preserveState: Boolean): Int {
    if (specificDevice === newDevice) {
      // Even if the specific device hasn't changed, make sure the cached device is null
      // to force recomputation of the "best" device if necessary.
      device = null
      return 0
    }

    val prevDevice = specificDevice
    val prevState = state

    specificDevice = newDevice
    device = newDevice
    context.updateDeviceOverlay()

    var updateFlags = CFG_DEVICE

    if (newDevice != null) {
      var newState: State? = null
      // Attempt to preserve the device state?
      if (preserveState && prevDevice != null) {
        if (prevState != null) {
          val oldConfig = DeviceConfigHelper.getFolderConfig(prevState)
          if (oldConfig != null) {
            val matchName = getClosestMatch(oldConfig, newDevice.allStates)
            newState = newDevice.getState(matchName)
          } else {
            newState = newDevice.getState(prevState.name)
          }
        }
      } else if (preserveState && stateName != null) {
        newState = newDevice.getState(stateName)
      }

      if (newState == null) {
        newState = newDevice.defaultState
      }

      if (state !== newState) {
        updateFlags = updateFlags or setDeviceStateName(newState?.name)
        state = newState
        updateFlags = updateFlags or CFG_DEVICE_STATE
      }
    }
    return updateFlags
  }

  fun setDeviceState(newState: State?): Int {
    if (state !== newState) {
      var flags = CFG_DEVICE_STATE
      if (newState != null) {
        flags = flags or setDeviceStateName(newState.name)
      } else {
        stateName = null
      }
      state = newState
      return flags
    }
    return 0
  }

  fun setDeviceStateName(newStateName: String?): Int {
    var actualStateName = newStateName
    val qualifier = context.editedConfig.screenOrientationQualifier
    if (qualifier != null) {
      val orientation = qualifier.value
      if (orientation != null) {
        actualStateName = orientation.shortDisplayValue // Also used as state names
      }
    }

    if (actualStateName != stateName) {
      stateName = actualStateName
      state = null
      return CFG_DEVICE_STATE
    }
    return 0
  }

  fun setEffectiveDevice(newDevice: Device?, newState: State?): Int {
    var updateFlags = 0
    if (device !== newDevice) {
      updateFlags = CFG_DEVICE
      device = newDevice
      context.updateDeviceOverlay()
    }

    if (state !== newState) {
      state = newState
      stateName = newState?.name
      updateFlags = updateFlags or CFG_DEVICE_STATE
    }
    return updateFlags
  }

  fun getScreenSize(): ScreenSize? {
    val deviceState = getDeviceState()
    if (deviceState != null) {
      val folderConfig = DeviceConfigHelper.getFolderConfig(deviceState)
      if (folderConfig != null) {
        val qualifier = folderConfig.screenSizeQualifier
        assert(qualifier != null)
        return qualifier?.value
      }
    }

    val currentDevice = getDevice()
    if (currentDevice != null) {
      for (s in currentDevice.allStates) {
        val folderConfig = DeviceConfigHelper.getFolderConfig(s)
        if (folderConfig != null) {
          val qualifier = folderConfig.screenSizeQualifier
          assert(qualifier != null)
          return qualifier?.value
        }
      }
    }
    return null
  }

  fun getNextDeviceState(from: State?): State? {
    val currentDevice = getDevice() ?: return null
    val states = currentDevice.allStates

    if (states.isEmpty()) return null

    for (i in states.indices) {
      if (states[i] === from) {
        return states[(i + 1) % states.size]
      }
    }

    if (from != null) {
      val name = from.name
      for (i in states.indices) {
        if (states[i].name == name) {
          return states[(i + 1) % states.size]
        }
      }
    }
    return null
  }

  fun copyFrom(other: DeviceStateResolver) {
    this.specificDevice = other.specificDevice
    this.device = other.device
    this.state = other.state
    this.stateName = other.stateName
  }

  companion object {
    private fun getClosestMatch(oldConfig: FolderConfiguration, states: List<State>): String? {
      val list1 = ArrayList(states)
      val list2 = ArrayList<State>(states.size)

      val count = FolderConfiguration.getQualifierCount()
      for (i in 0 until count) {
        for (s in list1) {
          val oldQualifier = oldConfig.getQualifier(i)
          val folderConfig = DeviceConfigHelper.getFolderConfig(s)
          val newQualifier = folderConfig?.getQualifier(i)

          if (oldQualifier == null) {
            if (newQualifier == null) {
              list2.add(s)
            }
          } else if (oldQualifier == newQualifier) {
            list2.add(s)
          }
        }

        if (list2.size == 1) {
          return list2[0].name
        }

        if (list2.isNotEmpty()) {
          list1.clear()
          list1.addAll(list2)
          list2.clear()
        }
      }

      if (list1.isNotEmpty()) {
        return list1[0].name
      }

      return null
    }
  }
}
