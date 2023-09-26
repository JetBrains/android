/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.google.common.util.concurrent.ListenableFuture
import java.time.Instant
import javax.swing.Icon

internal interface Device {
  /**
   * A physical device will always return a serial number. A virtual device will usually return a
   * virtual device path. But if Studio doesn't know about the virtual device (it's outside the
   * scope of the AVD Manager because it uses a locally built system image, for example) it can
   * return a virtual device path (probably not but I'm not going to assume), virtual device name,
   * or serial number depending on what the IDevice returned.
   */
  val key: Key
  val icon: Icon
  val launchCompatibility: LaunchCompatibility
  val isConnected: Boolean
  val connectionTime: Instant?
  val name: String
  val snapshots: Collection<Snapshot>
  val defaultTarget: Target
  val targets: Collection<Target>
  val androidDevice: AndroidDevice
  val ddmlibDeviceAsync: ListenableFuture<IDevice>
    get() {
      val device = androidDevice
      if (!device.isRunning()) {
        throw RuntimeException("$device is not running")
      }
      return device.getLaunchedDevice()
    }
}
