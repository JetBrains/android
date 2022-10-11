/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor

import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceState
import java.util.ArrayList
import java.util.function.Consumer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel

/**
 * The Device Monitor model class: encapsulates the list of devices,
 * their list of processes and also associated state changes to via the
 * [DeviceMonitorModelListener] listener class.
 */
class DeviceMonitorModel {
  private val myListeners: MutableList<DeviceMonitorModelListener> = ArrayList()
  private val myDevices: MutableList<Device> = ArrayList()
  private var myActiveDevice: Device? = null
  private var myActiveDeviceLastKnownState: DeviceState? = null

  var treeModel: DefaultTreeModel? = null
    private set

  var treeSelectionModel: DefaultTreeSelectionModel? = null
    private set

  val devices: List<Device>
    get() = myDevices

  var activeDevice: Device?
    get() = myActiveDevice
    set(activeDevice) {
      myActiveDevice = activeDevice
      myActiveDeviceLastKnownState = activeDevice?.state
      myListeners.forEach(Consumer { x: DeviceMonitorModelListener -> x.activeDeviceChanged(myActiveDevice) })
      setActiveDeviceTreeModel(activeDevice, null, null)
    }

  fun getActiveDeviceLastKnownState(device: Device): DeviceState? {
    return if (device != myActiveDevice) {
      null
    } else myActiveDeviceLastKnownState
  }

  fun setActiveDeviceLastKnownState(device: Device) {
    if (device != myActiveDevice) {
      return
    }
    myActiveDeviceLastKnownState = device.state
  }

  fun addListener(listener: DeviceMonitorModelListener) {
    myListeners.add(listener)
  }

  fun removeListener(listener: DeviceMonitorModelListener) {
    myListeners.remove(listener)
  }

  fun setActiveDeviceTreeModel(
    device: Device?,
    treeModel: DefaultTreeModel?,
    treeSelectionModel: DefaultTreeSelectionModel?
  ) {
    // Ignore if active device has changed
    if (myActiveDevice != device) {
      return
    }

    // Ignore if tree model is not changing
    if (this.treeModel == treeModel) {
      return
    }
    this.treeModel = treeModel
    this.treeSelectionModel = treeSelectionModel
    myListeners.forEach(Consumer { x: DeviceMonitorModelListener -> x.treeModelChanged(treeModel, treeSelectionModel) })
  }

  fun addDevice(device: Device) {
    if (myDevices.contains(device)) return
    myDevices.add(device)
    myListeners.forEach(Consumer { l: DeviceMonitorModelListener -> l.deviceAdded(device) })
  }

  fun removeDevice(device: Device) {
    if (!myDevices.contains(device)) return
    myListeners.forEach(Consumer { l: DeviceMonitorModelListener -> l.deviceRemoved(device) })
    myDevices.remove(device)
  }

  fun removeAllDevices() {
    myDevices.clear()
    activeDevice = null
    setActiveDeviceTreeModel(null, null, null)
    myListeners.forEach(Consumer { obj: DeviceMonitorModelListener -> obj.allDevicesRemoved() })
  }

  fun updateDevice(device: Device) {
    if (!myDevices.contains(device)) return
    myListeners.forEach(Consumer { l: DeviceMonitorModelListener -> l.deviceUpdated(device) })
  }
}
