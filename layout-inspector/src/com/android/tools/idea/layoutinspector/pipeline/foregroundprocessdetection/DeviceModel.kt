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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Keeps track of the currently selected device.
 *
 * The selected device is controlled by [ForegroundProcessDetection],
 * and it is used by [SelectedDeviceAction].
 */
class DeviceModel(parentDisposable: Disposable, private val processesModel: ProcessesModel): Disposable {

  @TestOnly
  constructor(
    parentDisposable: Disposable,
    processesModel: ProcessesModel,
    foregroundProcessDetectionSupportedDeviceTest: Set<DeviceDescriptor>
  ) : this(parentDisposable, processesModel) {
    foregroundProcessDetectionSupportedDevices.addAll(foregroundProcessDetectionSupportedDeviceTest)
  }

  init {
    Disposer.register(parentDisposable, this)
    ForegroundProcessDetection.addDeviceModel(this)
  }

  override fun dispose() {
    ForegroundProcessDetection.removeDeviceModel(this)
  }

  /**
   * The device on which the on-device library is polling for foreground process.
   * When null, it means that we are not polling on any device.
   *
   * [selectedDevice] should only be set by [ForegroundProcessDetection],
   * this is to make sure that there is consistency between the [selectedDevice] and the device we are polling on.
   */
  var selectedDevice: DeviceDescriptor? = null
    internal set(value) {
      // each time the selected device changes, the selected process should be reset
      processesModel.selectedProcess = null
      newSelectedDeviceListeners.forEach { it.invoke(value) }
      field = value
    }

  @TestOnly
  fun setSelectedDevice(device: DeviceDescriptor?) {
    selectedDevice = device
  }

  val newSelectedDeviceListeners = CopyOnWriteArraySet<(DeviceDescriptor?) -> Unit>()

  /**
   * The set of connected devices that support foreground process detection.
   */
  internal val foregroundProcessDetectionSupportedDevices = mutableSetOf<DeviceDescriptor>()

  val devices: Set<DeviceDescriptor>
    get() {
      return processesModel.devices
    }

  val selectedProcess: ProcessDescriptor?
    get() {
      return processesModel.selectedProcess
    }

  val processes: Set<ProcessDescriptor>
    get() {
      return processesModel.processes
    }

  fun supportsForegroundProcessDetection(device: DeviceDescriptor): Boolean {
    return foregroundProcessDetectionSupportedDevices.contains(device)
  }
}