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

import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.PhysicalDeviceProvisionerPlugin
import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Extension point for factories of [DeviceProvisionerPlugin]. The factory is necessary to allow
 * plugins to be created with a [Project] argument.
 */
interface DeviceProvisionerFactory {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<DeviceProvisionerFactory> =
      ExtensionPointName.create("com.android.tools.idea.deviceProvisioner")

    fun createProvisioners(project: Project): List<DeviceProvisionerPlugin> =
      EP_NAME.extensionList.map { it.create(project) }
  }

  fun create(project: Project): DeviceProvisionerPlugin
}

class PhysicalDeviceProvisionerFactory : DeviceProvisionerFactory {
  override fun create(project: Project): DeviceProvisionerPlugin =
    PhysicalDeviceProvisionerPlugin(project.coroutineScope)
}
