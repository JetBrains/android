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
package com.android.tools.idea.adb.wireless.provisioner

import com.android.tools.idea.adb.wireless.AdbServiceWrapperAdbLibImpl
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerFactory
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

// TODO(b/412571872) add tests
/** Builds a WifiPairableDeviceProvisionerPlugin with its dependencies provided by Studio. */
class WifiPairableDeviceProvisionerFactory : DeviceProvisionerFactory {
  override val isEnabled: Boolean
    get() = StudioFlags.WIFI_V2_ENABLED.get()

  override fun create(coroutineScope: CoroutineScope, project: Project) =
    WifiPairableDeviceProvisionerPlugin(
      coroutineScope,
      AdbServiceWrapperAdbLibImpl(project),
      project,
    )
}
