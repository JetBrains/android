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
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import java.util.Objects

/**
 * A deployment target for an app, binding a Device with a BootOption. A user actually selects these
 * (and not devices) with the combobox or the Select Multiple Devices dialog.
 */
class DeploymentTarget(val device: DeploymentTargetDevice, val bootOption: BootOption) {

  val deviceId: DeviceId
    get() = device.id

  val templateId: DeviceId?
    get() = device.templateId

  fun boot() {
    when (bootOption) {
      is BootSnapshot ->
        (device.androidDevice as DeviceHandleAndroidDevice).bootFromSnapshot(bootOption.snapshot)
      ColdBoot -> (device.androidDevice as DeviceHandleAndroidDevice).coldBoot()
      DefaultBoot -> device.androidDevice.bootDefault()
    }
  }

  val id: TargetId
    get() = TargetId(deviceId, templateId, bootOption)

  override fun equals(other: Any?) =
    other is DeploymentTarget && id == other.id && bootOption == other.bootOption

  override fun hashCode(): Int {
    return Objects.hash(id, bootOption)
  }

  override fun toString() = "Target($bootOption, $id)"
}

data class TargetId(val deviceId: DeviceId, val templateId: DeviceId?, val bootOption: BootOption)

sealed class BootOption {
  /** A user-visible text representation of this boot option. */
  abstract val text: String
}

// TODO: make this a data object when Kotlin 1.9 is available
object DefaultBoot : BootOption() {
  override val text = "Quick Boot"

  override fun toString() = this::class.simpleName!!
}

object ColdBoot : BootOption() {
  override val text = "Cold Boot"

  override fun toString() = this::class.simpleName!!
}

data class BootSnapshot(val snapshot: Snapshot) : BootOption() {
  override val text = snapshot.name
}
