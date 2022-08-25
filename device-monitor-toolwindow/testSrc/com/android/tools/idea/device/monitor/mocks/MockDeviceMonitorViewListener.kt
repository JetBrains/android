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
package com.android.tools.idea.device.monitor.mocks

import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.device.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.monitor.ProcessTreeNode
import com.android.tools.idea.device.monitor.processes.Device

class MockDeviceMonitorViewListener : DeviceMonitorViewListener {
  val deviceNotSelectedTracker = FutureValuesTracker<Unit>()
  val deviceSelectedTracker = FutureValuesTracker<Device>()
  val treeNodeExpandingTracker = FutureValuesTracker<ProcessTreeNode>()

  override fun noDeviceSelected() {
    deviceNotSelectedTracker.produce(null)
  }

  override fun deviceSelected(device: Device) {
    deviceSelectedTracker.produce(device)
  }

  override fun treeNodeExpanding(treeNode: ProcessTreeNode) {
    treeNodeExpandingTracker.produce(treeNode)
  }

  override fun refreshInvoked() {}

  override fun killNodesInvoked(nodes: List<ProcessTreeNode>) {}

  override fun forceStopNodesInvoked(nodes: List<ProcessTreeNode>) {}
}