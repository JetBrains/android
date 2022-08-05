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
package com.android.tools.idea.device.monitor

import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceListService

interface DeviceMonitorView {
  fun addListener(listener: DeviceMonitorViewListener)
  fun removeListener(listener: DeviceMonitorViewListener)
  fun setup()
  fun startRefresh(text: String)
  fun stopRefresh()
  fun showNoDeviceScreen()
  fun showActiveDeviceScreen()
  fun reportErrorRelatedToService(service: DeviceListService, message: String, t: Throwable)
  fun reportErrorRelatedToDevice(fileSystem: Device, message: String, t: Throwable)
  fun reportErrorRelatedToNode(node: ProcessTreeNode, message: String, t: Throwable)
  fun reportErrorGeneric(message: String, t: Throwable)
  fun reportMessageRelatedToDevice(fileSystem: Device, message: String)
  fun reportMessageRelatedToNode(node: ProcessTreeNode, message: String)
  fun startTreeBusyIndicator()
  fun stopTreeBusyIndicator()
  fun expandNode(treeNode: ProcessTreeNode)
  fun addProgressListener(listener: DeviceMonitorProgressListener)
  fun removeProgressListener(listener: DeviceMonitorProgressListener)
  fun startProgress()
  fun setProgressIndeterminate(indeterminate: Boolean)
  fun setProgressValue(fraction: Double)
  fun setProgressOkColor()
  fun setProgressWarningColor()
  fun setProgressErrorColor()
  fun setProgressText(text: String)
  fun stopProgress()
}
