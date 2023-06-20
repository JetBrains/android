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
package com.android.tools.idea.device.explorer.mocks

import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerController
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import javax.swing.JComponent
import javax.swing.JPanel

class MockDeviceFileExplorerController : DeviceFileExplorerController {
  val activeDeviceTracker: FutureValuesTracker<DeviceFileSystem?> = FutureValuesTracker<DeviceFileSystem?>()

  override fun setup() {}

  override fun setActiveConnectedDevice(fileSystem: DeviceFileSystem?) {
    activeDeviceTracker.produce(fileSystem)
  }

  override fun getViewComponent(): JComponent {
    return JPanel()
  }
}