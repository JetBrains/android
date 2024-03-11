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
package com.android.tools.idea.deviceprovisioner

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceIcons
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.PhysicalDeviceProvisionerPlugin
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope

/**
 * Extension point for factories of [DeviceProvisionerPlugin]. The factory is necessary to allow
 * plugins to be created with a [Project] argument.
 */
interface DeviceProvisionerFactory {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<DeviceProvisionerFactory> =
      ExtensionPointName.create("com.android.tools.idea.deviceProvisioner")

    fun createProvisioners(
      coroutineScope: CoroutineScope,
      project: Project
    ): List<DeviceProvisionerPlugin> =
      EP_NAME.extensionList.filter { it.isEnabled }.map { it.create(coroutineScope, project) }
  }

  val isEnabled: Boolean

  /**
   * Create a [DeviceProvisionerPlugin].
   *
   * @param coroutineScope the [CoroutineScope] of the [com.android.tools.idea.deviceprovisioner.DeviceProvisionerService].
   * The plugin should use this scope or inherit from it.
   * @param project the IntelliJ project
   */
  fun create(coroutineScope: CoroutineScope, project: Project): DeviceProvisionerPlugin
}

val StudioDefaultDeviceIcons = DeviceIcons(
  handheld = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE,
  wear = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR,
  tv = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV,
  automotive = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_CAR
)

class PhysicalDeviceProvisionerFactory : DeviceProvisionerFactory {
  override val isEnabled: Boolean
    get() = true

  override fun create(coroutineScope: CoroutineScope, project: Project): DeviceProvisionerPlugin =
    PhysicalDeviceProvisionerPlugin(
      coroutineScope,
      deviceIcons = StudioDefaultDeviceIcons
    )
}

@JvmField val DEVICE_HANDLE_KEY = DataKey.create<DeviceHandle>("DeviceHandle")
@JvmField val DEVICE_TEMPLATE_KEY = DataKey.create<DeviceTemplate>("DeviceTemplate")
