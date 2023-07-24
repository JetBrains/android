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
package com.android.tools.idea.device.explorer.mocks

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.device.explorer.common.DeviceExplorerControllerListener
import com.android.tools.idea.device.explorer.common.DeviceExplorerTabController
import javax.swing.JComponent
import javax.swing.JPanel

class MockDeviceExplorerTabController : DeviceExplorerTabController {
  val activeDeviceTracker: FutureValuesTracker<DeviceHandle?> = FutureValuesTracker<DeviceHandle?>()
  val packageFilterTracker: FutureValuesTracker<Boolean> = FutureValuesTracker<Boolean>()

  override var controllerListener: DeviceExplorerControllerListener? = null

  override fun setup() {}

  override fun setActiveConnectedDevice(deviceHandle: DeviceHandle?) {
    activeDeviceTracker.produce(deviceHandle)
  }

  override fun getViewComponent(): JComponent {
    return JPanel()
  }

  override fun getTabName(): String {
    return "Test Tab"
  }

  override fun setPackageFilter(isActive: Boolean) {
    packageFilterTracker.produce(isActive)
  }
}