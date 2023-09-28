/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.sdklib.deviceprovisioner.DeviceId
import com.intellij.openapi.project.Project

/**
 * A deployment target for an app. A user actually selects these (and not devices) with the drop
 * down or Select Multiple Devices dialog. The subclasses for virtual devices boot them differently.
 */
internal abstract class Target(val deviceKey: DeviceId, val templateKey: DeviceId?) {
  fun matches(device: Device): Boolean {
    return device.key == deviceKey
  }

  /**
   * @return the text for this target. It's used for the items in a virtual device's submenu and in
   *   the drop down button when a user selects a target.
   */
  abstract fun getText(device: Device): String

  abstract fun boot(device: Device, project: Project)

  companion object {
    @JvmStatic
    fun filterDevices(targets: Collection<Target>, devices: List<Device>): List<Device> {
      val keys = targets.map { it.deviceKey }.toSet()
      return devices.filter { it.key in keys }
    }
  }
}
