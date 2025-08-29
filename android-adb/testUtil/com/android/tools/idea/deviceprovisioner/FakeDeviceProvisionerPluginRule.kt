/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.sdklib.deviceprovisioner.testing.FakeDeviceProvisionerPlugin
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import kotlinx.coroutines.CoroutineScope
import org.junit.rules.ExternalResource

/**
 * Overrides the [DeviceProvisionerFactory] extension point so that [DeviceProvisionerService]
 * includes only the [FakeDeviceProvisionerPlugin].
 */
class FakeDeviceProvisionerPluginRule(val projectProvider: () -> Project) : ExternalResource() {
  lateinit var plugin: FakeDeviceProvisionerPlugin

  inner class FakeDeviceProvisionerPluginFactory : DeviceProvisionerFactory {
    override val isEnabled = true

    override fun create(coroutineScope: CoroutineScope, project: Project): DeviceProvisionerPlugin =
      FakeDeviceProvisionerPlugin(coroutineScope).also { plugin = it }
  }

  override fun before() {
    ExtensionTestUtil.maskExtensions(
      DeviceProvisionerFactory.EP_NAME,
      listOf(FakeDeviceProvisionerPluginFactory()),
      projectProvider(),
    )
    // Initialize the plugin field
    projectProvider().service<DeviceProvisionerService>().deviceProvisioner
  }
}
